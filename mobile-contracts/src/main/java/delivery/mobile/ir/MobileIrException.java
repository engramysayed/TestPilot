package delivery.mobile.ir;

/** Strict codec / contract rejection (unknown schema, op, or invalid step). */
public final class MobileIrException extends RuntimeException {
    private final String code;

    public MobileIrException(String code, String message) {
        super(message);
        this.code = code == null ? "MOBILE_IR_INVALID" : code;
    }

    public String code() {
        return code;
    }
}
