package delivery.mobile.runner.bind;

public final class BindException extends RuntimeException {
    public enum Code {
        INVALID_STRATEGY,
        CSS_IN_NATIVE,
        NATIVE_IN_WEBVIEW,
        EMPTY_SELECTOR
    }

    private final Code code;

    public BindException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code code() {
        return code;
    }
}
