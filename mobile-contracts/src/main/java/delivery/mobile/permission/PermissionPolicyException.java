package delivery.mobile.permission;

public final class PermissionPolicyException extends RuntimeException {
    private final String code;
    public PermissionPolicyException(String code, String message) {
        super(message);
        this.code = code == null ? "PERMISSION" : code;
    }
    public String code() { return code; }
}
