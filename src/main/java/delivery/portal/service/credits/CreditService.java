package delivery.portal.service.credits;

import delivery.portal.model.JobRecord;
import delivery.portal.persistence.PortalUser;
import delivery.portal.persistence.PortalUserRepository;
import delivery.portal.persistence.credits.CreditLedgerEntry;
import delivery.portal.persistence.credits.CreditLedgerRepository;
import delivery.portal.persistence.credits.UserCredits;
import delivery.portal.persistence.credits.UserCreditsRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * User-level Keel Credits. Atomic grant/set/debit with append-only ledger.
 * Never logs secrets or card data. Does not touch project BudgetLedger.
 */
@Service
public class CreditService {
    public static final String REASON_ADMIN_GRANT = "ADMIN_GRANT";
    public static final String REASON_ADMIN_ADJUST = "ADMIN_ADJUST";
    public static final String REASON_JOB_DEBIT = "JOB_DEBIT";
    public static final String REASON_JOB_REFUND = "JOB_REFUND";
    public static final String REASON_ADDON_DEBIT = "ADDON_DEBIT";

    /** Add-on ServicePrice codes (not JobKinds). */
    public static final String ADDON_VISUAL_DIFF = "VISUAL_DIFF";
    public static final String ADDON_SUITES_MONITOR = "SUITES_MONITOR";
    public static final String ADDON_HEAL_REVIEW = "HEAL_REVIEW";

    public static final String MODE_SET_ABSOLUTE = "SET_ABSOLUTE";
    public static final String MODE_GRANT_DELTA = "GRANT_DELTA";

    private final UserCreditsRepository credits;
    private final CreditLedgerRepository ledger;
    private final PortalUserRepository users;
    private final CreditCostTable costs;

    public CreditService(
            UserCreditsRepository credits,
            CreditLedgerRepository ledger,
            PortalUserRepository users,
            CreditCostTable costs) {
        this.credits = credits;
        this.ledger = ledger;
        this.users = users;
        this.costs = costs;
    }

    public CreditCostTable costs() {
        return costs;
    }

    @Transactional(readOnly = true)
    public Optional<UserCredits> findCredits(Long userId) {
        return credits.findById(userId);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> userCreditsDto(Long userId) {
        UserCredits row = credits.findById(userId).orElse(null);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("userId", userId);
        out.put("balance", row == null ? 0L : row.getBalance());
        out.put("lifetimeGranted", row == null ? 0L : row.getLifetimeGranted());
        out.put("lifetimeSpent", row == null ? 0L : row.getLifetimeSpent());
        out.put("updatedAt", row == null || row.getUpdatedAt() == null ? null : row.getUpdatedAt().toString());
        out.put("version", row == null ? 0L : row.getVersion());
        return out;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> accountBalanceResponse(Long userId) {
        long balance = credits.findById(userId).map(UserCredits::getBalance).orElse(0L);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("balance", balance);
        out.put("costs", costs.asCostsDto());
        out.put("enabled", costs.enabled());
        return out;
    }

    /** Fields to merge onto each /api/admin/users row. */
    @Transactional(readOnly = true)
    public void enrichAdminUserRow(Map<String, Object> row, Long userId) {
        UserCredits c = credits.findById(userId).orElse(null);
        row.put("creditsBalance", c == null ? 0L : c.getBalance());
        row.put("creditsLifetimeSpent", c == null ? 0L : c.getLifetimeSpent());
        row.put("creditsUpdatedAt", c == null || c.getUpdatedAt() == null ? null : c.getUpdatedAt().toString());
    }

    @Transactional
    public Map<String, Object> setCredits(
            Long userId, String mode, long amount, String note, String idempotencyKey, Long actorUserId) {
        users.findById(userId).orElseThrow(() -> new IllegalArgumentException("Unknown user"));
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey is required");
        }
        String key = idempotencyKey.trim();
        if (key.length() > 128) {
            throw new IllegalArgumentException("idempotencyKey max 128 chars");
        }
        String noteSan = requireNote(note);
        String modeNorm = mode == null ? "" : mode.trim().toUpperCase();
        if (!MODE_SET_ABSOLUTE.equals(modeNorm) && !MODE_GRANT_DELTA.equals(modeNorm)) {
            throw new IllegalArgumentException("mode must be SET_ABSOLUTE or GRANT_DELTA");
        }
        if (amount < 0L) {
            throw new IllegalArgumentException("amount must be >= 0");
        }
        if (MODE_GRANT_DELTA.equals(modeNorm) && amount == 0L) {
            throw new IllegalArgumentException("GRANT_DELTA amount must be > 0");
        }

        Optional<CreditLedgerEntry> existing = ledger.findByIdempotencyKey(key);
        if (existing.isPresent()) {
            CreditLedgerEntry e = existing.get();
            if (!userId.equals(e.getUserId())) {
                throw new IllegalArgumentException("idempotencyKey already used for another user");
            }
            return setResponse(userId, e.getBalanceAfter(), e.getDelta(), e.getId(),
                    "Credits unchanged (idempotent replay).");
        }

        UserCredits row = credits.findByIdForUpdate(userId).orElseGet(() -> newZero(userId));
        long balance = row.getBalance();
        long delta;
        String reason;
        if (MODE_GRANT_DELTA.equals(modeNorm)) {
            delta = amount;
            reason = REASON_ADMIN_GRANT;
        } else {
            delta = amount - balance;
            reason = delta >= 0 ? REASON_ADMIN_GRANT : REASON_ADMIN_ADJUST;
        }
        long newBal = balance + delta;
        if (newBal < 0L) {
            throw new IllegalArgumentException("resulting balance would be negative");
        }
        row.setBalance(newBal);
        if (delta > 0) {
            row.setLifetimeGranted(row.getLifetimeGranted() + delta);
        }
        // absolute clawback (negative delta) does not reduce lifetimeGranted
        row.setUpdatedAt(Instant.now());
        credits.save(row);

        CreditLedgerEntry entry = newLedger(userId, delta, newBal, reason, null, null, actorUserId, key, noteSan);
        try {
            ledger.save(entry);
        } catch (DataIntegrityViolationException race) {
            CreditLedgerEntry raced = ledger.findByIdempotencyKey(key)
                    .orElseThrow(() -> race);
            return setResponse(userId, raced.getBalanceAfter(), raced.getDelta(), raced.getId(),
                    "Credits unchanged (idempotent replay).");
        }
        String msg = "Credits updated. User now has " + newBal + " credits.";
        return setResponse(userId, newBal, delta, entry.getId(), msg);
    }

    /**
     * Debit at job START when {@code delivery.credits.enabled=true}.
     * Idempotent on {@code debit:job:{jobId}}. No-op when disabled or non-billable.
     */
    @Transactional
    public void tryDebitForJob(JobRecord job) {
        if (job == null || job.getOwnerUserId() == null || !costs.enabled()) {
            return;
        }
        CreditsBillingKind kind = CreditsBillingKind.fromJobKind(job.getJobKind());
        if (kind == null) {
            return;
        }
        long unit = costs.cost(kind);
        if (unit <= 0L) {
            return;
        }
        int quantity = BillableQuantity.count(job);
        if (quantity > 1 && unit > Long.MAX_VALUE / quantity) {
            throw new IllegalArgumentException("This job is too large to price.");
        }
        long cost = unit * quantity;
        String breakdown = BillableQuantity.breakdown(kind, quantity, unit);
        String jobId = job.getJobId();
        String key = "debit:job:" + jobId;
        Optional<CreditLedgerEntry> existing = ledger.findByIdempotencyKey(key);
        if (existing.isPresent()) {
            return; // retry / double-submit — already charged
        }

        Long userId = job.getOwnerUserId();
        UserCredits row = credits.findByIdForUpdate(userId).orElseGet(() -> newZero(userId));
        // re-check idempotency under lock
        if (ledger.findByIdempotencyKey(key).isPresent()) {
            return;
        }
        long balance = row.getBalance();
        if (balance < cost) {
            throw new InsufficientCreditsException(cost, balance, kind.name(), breakdown);
        }
        long newBal = balance - cost;
        row.setBalance(newBal);
        row.setLifetimeSpent(row.getLifetimeSpent() + cost);
        row.setUpdatedAt(Instant.now());
        credits.save(row);

        CreditLedgerEntry entry = newLedger(
                userId, -cost, newBal, REASON_JOB_DEBIT, jobId, kind.name(), null, key, breakdown);
        try {
            ledger.save(entry);
        } catch (DataIntegrityViolationException race) {
            // concurrent identical debit — treat as success (idempotent)
        }
    }

    /**
     * Debit when an add-on feature is used (visual review, scheduled suite run, heal review).
     * Cost comes from admin-editable ServicePrice by code — not from JobKind.
     * Idempotent on debit:addon:{code}:{stableId}. No-op when credits disabled or cost <= 0.
     */
    @Transactional
    public void tryDebitForAddon(Long userId, String servicePriceCode, String stableId) {
        if (userId == null || !costs.enabled()) {
            return;
        }
        if (servicePriceCode == null || servicePriceCode.isBlank()) {
            throw new IllegalArgumentException("servicePriceCode is required");
        }
        if (stableId == null || stableId.isBlank()) {
            throw new IllegalArgumentException("stableId is required");
        }
        String code = servicePriceCode.trim().toUpperCase();
        String stable = stableId.trim();
        if (stable.length() > 160) {
            throw new IllegalArgumentException("stableId max 160 chars");
        }
        long cost = costs.costByCode(code);
        if (cost <= 0L) {
            return;
        }
        String key = "debit:addon:" + code + ":" + stable;
        Optional<CreditLedgerEntry> existing = ledger.findByIdempotencyKey(key);
        if (existing.isPresent()) {
            return;
        }

        UserCredits row = credits.findByIdForUpdate(userId).orElseGet(() -> newZero(userId));
        if (ledger.findByIdempotencyKey(key).isPresent()) {
            return;
        }
        long balance = row.getBalance();
        if (balance < cost) {
            throw new InsufficientCreditsException(cost, balance, code, null);
        }
        long newBal = balance - cost;
        row.setBalance(newBal);
        row.setLifetimeSpent(row.getLifetimeSpent() + cost);
        row.setUpdatedAt(Instant.now());
        credits.save(row);

        String note = "Add-on " + code + " x 1 @ " + cost;
        CreditLedgerEntry entry = newLedger(
                userId, -cost, newBal, REASON_ADDON_DEBIT, stable, code, null, key, note);
        try {
            ledger.save(entry);
        } catch (DataIntegrityViolationException race) {
            // concurrent identical debit — treat as success (idempotent)
        }
    }

    @Transactional(readOnly = true)
    public Map<String, Object> usageForUser(Long userId, Instant from, Instant to, String jobKindFilter) {
        PortalUser u = users.findById(userId).orElseThrow(() -> new IllegalArgumentException("Unknown user"));
        String jk = null;
        if (jobKindFilter != null && !jobKindFilter.isBlank()) {
            CreditsBillingKind parsed = CreditsBillingKind.parse(jobKindFilter);
            if (parsed == null) {
                throw new IllegalArgumentException("jobKind must be GENERATE, EXECUTE, AUTOMATE, or HUNT");
            }
            jk = parsed.name();
        }
        List<CreditLedgerEntry> entries = ledger.findFiltered(userId, from, to, jk);
        long spent = 0L;
        long granted = 0L;
        List<Map<String, Object>> rows = new ArrayList<>();
        for (CreditLedgerEntry e : entries) {
            if (e.getDelta() < 0) {
                spent += -e.getDelta();
            } else if (e.getDelta() > 0) {
                granted += e.getDelta();
            }
            rows.add(ledgerDto(e));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("userId", userId);
        out.put("email", u.getEmail());
        out.put("balance", credits.findById(userId).map(UserCredits::getBalance).orElse(0L));
        out.put("entries", rows);
        out.put("spentInRange", spent);
        out.put("grantedInRange", granted);
        AccountApprovalService.putApprovalFields(out, u);
        return out;
    }

    public static Map<String, Object> ledgerDto(CreditLedgerEntry e) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", e.getId());
        row.put("userId", e.getUserId());
        row.put("delta", e.getDelta());
        row.put("balanceAfter", e.getBalanceAfter());
        row.put("reason", e.getReason());
        row.put("jobId", e.getJobId());
        row.put("jobKind", e.getJobKind());
        row.put("actorUserId", e.getActorUserId());
        row.put("idempotencyKey", e.getIdempotencyKey());
        row.put("note", e.getNote());
        row.put("createdAt", e.getCreatedAt() == null ? null : e.getCreatedAt().toString());
        return row;
    }

    private UserCredits newZero(Long userId) {
        UserCredits row = new UserCredits();
        row.setUserId(userId);
        row.setBalance(0L);
        row.setLifetimeGranted(0L);
        row.setLifetimeSpent(0L);
        row.setUpdatedAt(Instant.now());
        return credits.saveAndFlush(row);
    }

    private static CreditLedgerEntry newLedger(
            Long userId, long delta, long balanceAfter, String reason,
            String jobId, String jobKind, Long actorUserId, String idempotencyKey, String note) {
        CreditLedgerEntry e = new CreditLedgerEntry();
        e.setUserId(userId);
        e.setDelta(delta);
        e.setBalanceAfter(balanceAfter);
        e.setReason(reason);
        e.setJobId(jobId);
        e.setJobKind(jobKind);
        e.setActorUserId(actorUserId);
        e.setIdempotencyKey(idempotencyKey);
        e.setNote(note);
        e.setCreatedAt(Instant.now());
        return e;
    }

    private static Map<String, Object> setResponse(
            Long userId, long balance, long deltaApplied, Long ledgerEntryId, String message) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("userId", userId);
        out.put("balance", balance);
        out.put("deltaApplied", deltaApplied);
        out.put("ledgerEntryId", ledgerEntryId);
        out.put("message", message);
        return out;
    }

    private static String requireNote(String note) {
        if (note == null || note.isBlank()) {
            throw new IllegalArgumentException("note is required");
        }
        String trimmed = note.trim();
        if (trimmed.length() > 512) {
            trimmed = trimmed.substring(0, 512);
        }
        String lower = trimmed.toLowerCase();
        if (lower.contains("password") || lower.contains("cvv") || lower.contains("card number")
                || lower.contains("api-key") || lower.contains("apikey") || lower.contains("secret")) {
            return "[redacted]";
        }
        return trimmed;
    }
}
