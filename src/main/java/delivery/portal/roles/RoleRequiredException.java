package delivery.portal.roles;

/** Missing permission — maps to FORBIDDEN / ROLE_REQUIRED (not INSUFFICIENT_CREDITS). */
public class RoleRequiredException extends RuntimeException {
    private final String errorCode;

    public RoleRequiredException(String message) {
        this("ROLE_REQUIRED", message);
    }

    public RoleRequiredException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode == null || errorCode.isBlank() ? "ROLE_REQUIRED" : errorCode;
    }

    public String errorCode() {
        return errorCode;
    }

    public static RoleRequiredException forbidden(String message) {
        return new RoleRequiredException("FORBIDDEN", message);
    }

    public static RoleRequiredException missingPermission(String code) {
        return new RoleRequiredException("ROLE_REQUIRED", "Missing permission: " + code);
    }

    public static RoleRequiredException account(ApprovalStatus status) {
        return new RoleRequiredException(status.denyErrorCode(), status.denyMessage());
    }
}
