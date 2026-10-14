package delivery.portal.roles;

import java.util.Locale;

/** Account approval lifecycle — APPROVED (not ACTIVE). */
public enum ApprovalStatus {
    PENDING_APPROVAL,
    APPROVED,
    LOCKED,
    REJECTED;

    public static ApprovalStatus parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return PENDING_APPROVAL;
        }
        String n = raw.trim().toUpperCase(Locale.ROOT);
        // Reject mistaken ACTIVE from outdated drafts
        if ("ACTIVE".equals(n)) {
            throw new IllegalArgumentException("Use APPROVED, not ACTIVE");
        }
        return ApprovalStatus.valueOf(n);
    }

    public boolean canStartFeatureJobs() {
        return this == APPROVED;
    }

    public String denyErrorCode() {
        return this == LOCKED ? "ACCOUNT_LOCKED" : "ACCOUNT_NOT_APPROVED";
    }

    public String denyMessage() {
        return switch (this) {
            case LOCKED -> "Account is locked";
            case REJECTED -> "Account was rejected";
            case PENDING_APPROVAL -> "Account pending approval";
            case APPROVED -> "Account approved";
        };
    }
}
