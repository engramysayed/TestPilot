package delivery.mobile.apk;

/** Rejected APK upload/inspection with a stable error code (no silent uninstall fallback). */
public class ApkInspectionException extends Exception {
    private final ApkErrorCode code;

    public ApkInspectionException(ApkErrorCode code, String message) {
        super(message == null ? code.name() : message);
        this.code = code == null ? ApkErrorCode.APK_INVALID : code;
    }

    public ApkErrorCode getCode() {
        return code;
    }
}
