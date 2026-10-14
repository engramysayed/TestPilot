package keel.contract.runner;

/** Runner API header names shared by portal (v1 + v2) and engine. Single source of truth (B03). */
public final class RunnerHeaders {
    public static final String ATTEMPT = "X-Keel-Attempt-Id";
    public static final String CANCEL_GENERATION = "X-Keel-Cancel-Generation";
    /** Monotonic fencing token issued with the lease; writes with a stale token are rejected (v2). */
    public static final String FENCING_TOKEN = "X-Keel-Fencing-Token";
    /** v1 artifact body signature (HMAC of digest with runner token). */
    public static final String ARTIFACT_SIGNATURE = "X-Keel-Runner-Signature";
    /** v2 request signing (SPLIT_DESIGN rev 10 §runner auth). */
    public static final String SIGNATURE = "X-Keel-Signature";
    public static final String TIMESTAMP = "X-Keel-Timestamp";
    public static final String NONCE = "X-Keel-Nonce";
    public static final String V1_PREFIX = "/api/v1/runners";
    public static final String V2_PREFIX = "/api/v2/runners";

    private RunnerHeaders() {
    }
}
