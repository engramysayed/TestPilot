package delivery.ir;

/** Phase-1 IR status for one test case draft. */
public enum TcDraftStatus {
    /** All intents proven. */
    PASSED,
    /** Some steps proven; later step blocked. */
    PARTIAL,
    /** Failed before any body step (login/bind) or zero proven. */
    TODO,
    /** UPDATE mode: unchanged TC reused from store. */
    REUSED
}
