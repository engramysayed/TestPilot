package delivery.portal.credits;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory Keel Credits + approval stub until Backend persists user_credits / credit_ledger.
 * Final lock: credits-ui/COORDINATE_WITH_BACKEND.md + SIGNUP_APPROVAL_DTO_FIELDS.md.
 * Approve never auto-grants credits.
 */
@Component
public class CreditsStubStore {
    private final AtomicLong ledgerSeq = new AtomicLong(1);
    private final ConcurrentHashMap<Long, Balance> balances = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, List<Map<String, Object>>> ledgers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> idempotency = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Approval> approvals = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, String> userRoles = new ConcurrentHashMap<>();

    public record Balance(long balance, long lifetimeGranted, long lifetimeSpent, String updatedAt, long version) {}
    public record Approval(String approvalStatus, String approvedAt, Long approvedByUserId) {}

    public Balance getBalance(long userId) {
        return balances.getOrDefault(userId, new Balance(0, 0, 0, null, 0));
    }

    public Approval getApproval(long userId) {
        return approvals.getOrDefault(userId, new Approval("APPROVED", null, null));
    }

    /** Seed bootstrap admin as APPROVED; others default APPROVED unless set. */
    public void ensureApproval(long userId, String status) {
        approvals.putIfAbsent(userId, new Approval(status, null, null));
    }

    public synchronized Map<String, Object> setApproval(long userId, String status, String roleId,
                                                          Long actorUserId, String note) {
        String normalized = status == null ? "" : status.trim().toUpperCase();
        if (!normalized.equals("APPROVED") && !normalized.equals("LOCKED") && !normalized.equals("REJECTED")) {
            throw new IllegalArgumentException("status must be APPROVED, LOCKED, or REJECTED");
        }
        if (normalized.equals("APPROVED")) {
            if (roleId == null || roleId.isBlank()) {
                throw new RoleRequiredException();
            }
            userRoles.put(userId, roleId.trim());
        }
        String at = Instant.now().toString();
        approvals.put(userId, new Approval(normalized, at, actorUserId));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("userId", userId);
        out.put("approvalStatus", normalized);
        out.put("approvedAt", at);
        out.put("approvedByUserId", actorUserId);
        if (normalized.equals("APPROVED")) {
            out.put("roleId", roleId.trim());
        }
        if (note != null && !note.isBlank()) {
            out.put("note", note.trim());
        }
        // Never auto-grant credits on approve — balance stays until POST /api/admin/credits/{userId}
        out.put("creditsBalance", getBalance(userId).balance());
        out.put("message", normalized.equals("APPROVED")
                ? "User approved with selected role. Set credits separately if needed — nothing is added automatically."
                : ("User marked " + normalized + "."));
        return out;
    }

    public String roleFor(long userId) {
        return userRoles.get(userId);
    }

    public static class RoleRequiredException extends IllegalArgumentException {
        public RoleRequiredException() { super("Pick a role before approving."); }
    }

    public synchronized Map<String, Object> applySet(long userId, String mode, long amount, String note,
                                                     String idempotencyKey, Long actorUserId) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey is required");
        }
        if (note == null || note.isBlank()) {
            throw new IllegalArgumentException("note is required");
        }
        if (note.length() > 512) {
            throw new IllegalArgumentException("note max 512 characters");
        }
        Long existingLedger = idempotency.get(idempotencyKey);
        if (existingLedger != null) {
            Balance b = getBalance(userId);
            Map<String, Object> replay = new LinkedHashMap<>();
            replay.put("userId", userId);
            replay.put("balance", b.balance());
            replay.put("deltaApplied", 0L);
            replay.put("ledgerEntryId", existingLedger);
            replay.put("message", "Credits already updated (same request).");
            return replay;
        }
        String m = mode == null ? "" : mode.trim().toUpperCase();
        Balance cur = getBalance(userId);
        long delta;
        long newBal;
        String reason;
        if ("SET_ABSOLUTE".equals(m)) {
            if (amount < 0) {
                throw new IllegalArgumentException("amount must be >= 0 for SET_ABSOLUTE");
            }
            newBal = amount;
            delta = newBal - cur.balance();
            reason = "ADMIN_ADJUST";
        } else if ("GRANT_DELTA".equals(m)) {
            if (amount <= 0) {
                throw new IllegalArgumentException("amount must be > 0 for GRANT_DELTA");
            }
            delta = amount;
            newBal = cur.balance() + delta;
            reason = "ADMIN_GRANT";
        } else {
            throw new IllegalArgumentException("mode must be SET_ABSOLUTE or GRANT_DELTA");
        }
        if (newBal < 0) {
            throw new IllegalArgumentException("balance cannot go negative");
        }
        long granted = cur.lifetimeGranted() + Math.max(0, delta);
        long spent = cur.lifetimeSpent();
        String updatedAt = Instant.now().toString();
        long version = cur.version() + 1;
        balances.put(userId, new Balance(newBal, granted, spent, updatedAt, version));
        long ledgerId = ledgerSeq.getAndIncrement();
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("id", ledgerId);
        entry.put("userId", userId);
        entry.put("delta", delta);
        entry.put("balanceAfter", newBal);
        entry.put("reason", reason);
        entry.put("jobId", null);
        entry.put("jobKind", null);
        entry.put("actorUserId", actorUserId);
        entry.put("idempotencyKey", idempotencyKey);
        entry.put("note", note.trim());
        entry.put("createdAt", updatedAt);
        ledgers.computeIfAbsent(userId, k -> new ArrayList<>()).add(entry);
        idempotency.put(idempotencyKey, ledgerId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("userId", userId);
        out.put("balance", newBal);
        out.put("deltaApplied", delta);
        out.put("ledgerEntryId", ledgerId);
        out.put("message", "Credits updated. User now has " + newBal + " credits.");
        return out;
    }

    public Map<String, Object> usage(long userId, String email, String from, String to, String jobKind) {
        Balance b = getBalance(userId);
        List<Map<String, Object>> all = new ArrayList<>(ledgers.getOrDefault(userId, List.of()));
        all.sort(Comparator.comparing(e -> String.valueOf(e.get("createdAt")), Comparator.reverseOrder()));
        long spent = 0;
        long granted = 0;
        List<Map<String, Object>> filtered = new ArrayList<>();
        for (Map<String, Object> e : all) {
            String created = String.valueOf(e.getOrDefault("createdAt", ""));
            if (from != null && !from.isBlank() && created.compareTo(from) < 0) {
                continue;
            }
            if (to != null && !to.isBlank() && created.compareTo(to) > 0) {
                continue;
            }
            if (jobKind != null && !jobKind.isBlank()) {
                Object jk = e.get("jobKind");
                if (jk == null || !jobKind.equalsIgnoreCase(String.valueOf(jk))) {
                    continue;
                }
            }
            filtered.add(e);
            long d = ((Number) e.get("delta")).longValue();
            if (d < 0) {
                spent += -d;
            } else {
                granted += d;
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("userId", userId);
        out.put("email", email == null ? "" : email);
        out.put("balance", b.balance());
        out.put("entries", filtered);
        out.put("spentInRange", spent);
        out.put("grantedInRange", granted);
        return out;
    }

    public Map<String, Object> userCreditsExtras(long userId) {
        Balance b = getBalance(userId);
        Approval a = getApproval(userId);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("creditsBalance", b.balance());
        m.put("creditsLifetimeSpent", b.lifetimeSpent());
        m.put("creditsUpdatedAt", b.updatedAt());
        m.put("approvalStatus", a.approvalStatus());
        m.put("approvedAt", a.approvedAt());
        m.put("approvedByUserId", a.approvedByUserId());
        return m;
    }
}
