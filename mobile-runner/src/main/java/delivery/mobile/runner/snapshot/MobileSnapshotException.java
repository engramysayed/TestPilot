package delivery.mobile.runner.snapshot;

/** Fail-closed snapshot parse / limit / security errors (never a silent empty tree). */
public final class MobileSnapshotException extends RuntimeException {
    public enum Code {
        EMPTY,
        OVERSIZE_BYTES,
        OVERSIZE_NODES,
        OVERSIZE_DEPTH,
        MALFORMED,
        XXE_REJECTED,
        BAD_BOUNDS,
        LIMIT
    }

    private final Code code;

    public MobileSnapshotException(Code code, String message) {
        super(message);
        this.code = code == null ? Code.MALFORMED : code;
    }

    public MobileSnapshotException(Code code, String message, Throwable cause) {
        super(message, cause);
        this.code = code == null ? Code.MALFORMED : code;
    }

    public Code code() {
        return code;
    }
}
