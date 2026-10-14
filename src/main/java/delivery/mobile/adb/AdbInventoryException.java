package delivery.mobile.adb;

public class AdbInventoryException extends Exception {
    private final AdbErrorCode code;

    public AdbInventoryException(AdbErrorCode code, String message) {
        super(message);
        this.code = code == null ? AdbErrorCode.COMMAND_FAILED : code;
    }

    public AdbErrorCode getCode() {
        return code;
    }
}
