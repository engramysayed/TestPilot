package delivery.runner;

import delivery.job.WebhookSigner;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/** HMAC of the artifact digest using the runner token. Bodies stay on the runner until this check passes. */
public final class PrivateRunnerArtifacts {
    public static final String SIGNATURE_HEADER = "X-Keel-Runner-Signature";

    private PrivateRunnerArtifacts() {
    }

    public static String digest(byte[] body) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(body == null ? new byte[0] : body));
        } catch (Exception e) {
            throw new IllegalStateException("sha256 unavailable", e);
        }
    }

    public static String signature(String token, String jobId, String attemptId, byte[] body) {
        return WebhookSigner.signature(
                token == null ? "" : token,
                (jobId == null ? "" : jobId) + "." + (attemptId == null ? "" : attemptId),
                digest(body));
    }

    public static boolean matches(String token, String jobId, String attemptId, byte[] body, String header) {
        if (header == null || header.isBlank() || token == null || token.isBlank()) {
            return false;
        }
        String expected = signature(token, jobId, attemptId, body).toLowerCase();
        String incoming = header.trim().toLowerCase();
        if (incoming.startsWith("sha256=")) {
            incoming = incoming.substring(7);
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), incoming.getBytes(StandardCharsets.UTF_8));
    }
}
