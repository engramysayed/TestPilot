package delivery.job;

public enum TcStatus {
    PASSED,
    /** Some steps proven; blocked mid-case — emitted as TodoTest with partial calls. */
    PARTIAL,
    TODO
}
