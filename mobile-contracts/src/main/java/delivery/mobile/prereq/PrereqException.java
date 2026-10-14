package delivery.mobile.prereq;

public final class PrereqException extends RuntimeException {
    private final String code;

    public PrereqException(String code, String message) {
        super(message);
        this.code = code == null ? "PREREQ" : code;
    }

    public String code() {
        return code;
    }
}
