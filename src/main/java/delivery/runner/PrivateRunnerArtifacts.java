package delivery.runner;

import keel.contract.runner.ArtifactSignature;
import keel.contract.runner.RunnerHeaders;

/**
 * HMAC of the artifact digest using the runner token. Bodies stay on the runner until this check passes.
 * B03: the algorithm lives in {@code keel-contract} ({@link ArtifactSignature}); this facade keeps old call sites.
 */
public final class PrivateRunnerArtifacts {
    public static final String SIGNATURE_HEADER = RunnerHeaders.ARTIFACT_SIGNATURE;

    private PrivateRunnerArtifacts() {
    }

    public static String digest(byte[] body) {
        return ArtifactSignature.digest(body);
    }

    public static String signature(String token, String jobId, String attemptId, byte[] body) {
        return ArtifactSignature.signature(token == null ? "" : token, jobId, attemptId, body);
    }

    public static boolean matches(String token, String jobId, String attemptId, byte[] body, String header) {
        return ArtifactSignature.matches(token, jobId, attemptId, body, header);
    }
}
