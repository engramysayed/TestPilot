package delivery.mobile.runner.assertx;

public record AssertResult(boolean passed, String code, String detail) {
    public static AssertResult ok() { return new AssertResult(true, "OK", ""); }
    public static AssertResult fail(String code, String detail) { return new AssertResult(false, code, detail); }
}
