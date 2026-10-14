package delivery.mobile.lease;

public class DeviceLeaseException extends RuntimeException {
    private final DeviceLeaseErrorCode code;

    public DeviceLeaseException(DeviceLeaseErrorCode code, String message) {
        super(message == null ? code.name() : message);
        this.code = code;
    }

    public DeviceLeaseErrorCode getCode() {
        return code;
    }
}
