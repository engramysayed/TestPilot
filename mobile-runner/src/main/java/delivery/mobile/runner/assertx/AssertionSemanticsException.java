package delivery.mobile.runner.assertx;

public final class AssertionSemanticsException extends RuntimeException {
    private final String code;
    public AssertionSemanticsException(String code, String message) {
        super(message);
        this.code = code;
    }
    public String code() { return code; }
}
