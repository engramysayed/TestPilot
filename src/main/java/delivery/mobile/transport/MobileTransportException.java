package delivery.mobile.transport;

public final class MobileTransportException extends RuntimeException {
    private final MobileTransportErrorCode code;

    public MobileTransportException(MobileTransportErrorCode code, String message) {
        super(message == null ? (code == null ? "MOBILE_TRANSPORT" : code.name()) : message);
        this.code = code == null ? MobileTransportErrorCode.ARTIFACT_INVALID : code;
    }

    public MobileTransportErrorCode getCode() {
        return code;
    }
}
