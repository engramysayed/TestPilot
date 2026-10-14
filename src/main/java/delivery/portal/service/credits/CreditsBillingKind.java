package delivery.portal.service.credits;

import delivery.portal.model.JobRecord;

/** Customer-facing billable kinds: GENERATE | EXECUTE | AUTOMATE | HUNT. */
public enum CreditsBillingKind {
    GENERATE, EXECUTE, AUTOMATE, HUNT;

    public static CreditsBillingKind fromJobKind(JobRecord.JobKind kind) {
        if (kind == null) {
            return null;
        }
        return switch (kind) {
            case GENERATE_BATCH, GENERATE_COMPARE -> GENERATE;
            case EXECUTE -> EXECUTE;
            case CONVERT -> AUTOMATE;
            case HUNT -> HUNT;
        };
    }

    public static CreditsBillingKind parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return CreditsBillingKind.valueOf(raw.trim().toUpperCase());
    }
}
