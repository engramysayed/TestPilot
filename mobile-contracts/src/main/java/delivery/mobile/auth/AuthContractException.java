package delivery.mobile.auth;

public final class AuthContractException extends RuntimeException {
    private final String code;

    public AuthContractException(String code, String message) {
        super(message);
        this.code = code == null ? "AUTH" : code;
    }

    public String code() {
        return code;
    }
}
