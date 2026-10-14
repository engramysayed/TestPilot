package delivery.portal.service.credits;

import delivery.portal.persistence.PortalUser;
import delivery.portal.roles.ApprovalStatus;
import delivery.portal.persistence.PortalUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Signup approval gate. Approving does <b>not</b> grant credits or change role —
 * admin must call credit set API separately (no trial grant).
 */
@Service
public class AccountApprovalService {
    private final PortalUserRepository users;

    public AccountApprovalService(PortalUserRepository users) {
        this.users = users;
    }

    /** Throws if user cannot start billable jobs. */
    @Transactional(readOnly = true)
    public void requireApprovedForJobs(Long userId) {
        PortalUser u = users.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown user"));
        if (u.getApprovalStatus() != ApprovalStatus.APPROVED) {
            throw AccountApprovalException.forStatus(u.getApprovalStatus());
        }
    }

    @Transactional
    public Map<String, Object> setApproval(Long userId, String statusRaw, String note, Long actorUserId) {
        PortalUser u = users.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown user"));
        if (statusRaw == null || statusRaw.isBlank()) {
            throw new IllegalArgumentException("status is required");
        }
        ApprovalStatus next;
        try {
            next = ApprovalStatus.valueOf(statusRaw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("status must be APPROVED, LOCKED, or REJECTED");
        }
        // Admin API does not set PENDING_APPROVAL via this endpoint (signup default only)
        if (next == ApprovalStatus.PENDING_APPROVAL) {
            throw new IllegalArgumentException("status must be APPROVED, LOCKED, or REJECTED");
        }
        u.setApprovalStatus(next);
        if (next == ApprovalStatus.APPROVED) {
            u.setApprovedAt(Instant.now());
            u.setApprovedByUserId(actorUserId);
            // Intentionally NO credit grant / trial pack here.
        } else {
            // lock/reject — keep approvedAt as historical if previously approved
            if (u.getApprovedAt() == null) {
                u.setApprovedAt(null);
                u.setApprovedByUserId(null);
            }
        }
        users.save(u);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("userId", u.getId());
        out.put("email", u.getEmail());
        out.put("approvalStatus", u.getApprovalStatus().name());
        out.put("approvedAt", u.getApprovedAt() == null ? null : u.getApprovedAt().toString());
        out.put("approvedByUserId", u.getApprovedByUserId());
        out.put("note", note == null || note.isBlank() ? null : note.trim());
        out.put("message", "Approval updated to " + next.name()
                + ". Credits unchanged — use POST /api/admin/credits/{userId} to grant.");
        return out;
    }

    public static void putApprovalFields(Map<String, Object> row, PortalUser u) {
        if (u == null) {
            row.put("approvalStatus", ApprovalStatus.PENDING_APPROVAL.name());
            row.put("approvedAt", null);
            row.put("approvedByUserId", null);
            return;
        }
        ApprovalStatus st = u.getApprovalStatus() == null
                ? ApprovalStatus.PENDING_APPROVAL : u.getApprovalStatus();
        row.put("approvalStatus", st.name());
        row.put("approvedAt", u.getApprovedAt() == null ? null : u.getApprovedAt().toString());
        row.put("approvedByUserId", u.getApprovedByUserId());
    }
}
