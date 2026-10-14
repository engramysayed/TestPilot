package delivery.mobile.runner.grammar;

public final class GrammarException extends RuntimeException {
    private final String code;
    public GrammarException(String code, String message) {
        super(message);
        this.code = code;
    }
    public String code() { return code; }
}
