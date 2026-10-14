package delivery.portal.service.credits;

/**
 * Job / feature gate when account is not APPROVED.
 * Codes: {@code ACCOUNT_NOT_APPROVED} or {@code ACCOUNT_LOCKED} — never {@code INSUFFICIENT_CREDITS}.
 */
public final class AccountApprovalException extends RuntimeException {
    public static final String CODE_NOT_APPROVED = "ACCOUNT_NOT_APPROVED";
    public static final String CODE_LOCKED = "ACCOUNT_LOCKED";

    private final String code;
    private final String approvalStatus;

    public AccountApprovalException(String code, String approvalStatus, String message) {
        super(message);
        this.code = code == null ? CODE_NOT_APPROVED : code;
        this.approvalStatus = approvalStatus == null ? "" : approvalStatus;
    }

    public static AccountApprovalException forStatus(delivery.portal.roles.ApprovalStatus status) {
        if (status == delivery.portal.roles.ApprovalStatus.LOCKED) {
            return new AccountApprovalException(
                    CODE_LOCKED,
                    status.name(),
                    "Account is locked. Contact your Keel admin.");
        }
        String st = status == null ? "PENDING_APPROVAL" : status.name();
        return new AccountApprovalException(
                CODE_NOT_APPROVED,
                st,
                "Account is not approved yet (" + st + "). Wait for admin approval before starting jobs.");
    }

    public String code() { return code; }
    public String approvalStatus() { return approvalStatus; }
}
