package delivery.mobile.appium;

public final class AppiumLifecycleException extends RuntimeException {
    private final AppiumErrorCode code;

    public AppiumLifecycleException(AppiumErrorCode code, String message) {
        super(message == null ? (code == null ? "appium lifecycle error" : code.name()) : message);
        this.code = code == null ? AppiumErrorCode.INVALID_STATE : code;
    }

    public AppiumErrorCode getCode() {
        return code;
    }
}
