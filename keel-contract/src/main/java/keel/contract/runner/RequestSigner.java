package keel.contract.runner;

import java.nio.charset.StandardCharsets;

/**
 * v2 request signing. Canonical string (newline separated):
 * METHOD, path (no query), query ("" if none), timestamp (epoch seconds), nonce, sha256(body) hex.
 * Signature = hex(HMAC-SHA256(runnerToken, canonical)). Header {@link RunnerHeaders#SIGNATURE} = "v1=" + hex.
 */
public final class RequestSigner {
    public static final String SCHEME = "v1=";

    private RequestSigner() {
    }

    public static String canonical(String method, String path, String query, long epochSeconds, String nonce, byte[] body) {
        return (method == null ? "" : method.toUpperCase()) + "\n"
                + (path == null ? "" : path) + "\n"
                + (query == null ? "" : query) + "\n"
                + epochSeconds + "\n"
                + (nonce == null ? "" : nonce) + "\n"
                + ArtifactSignature.digest(body);
    }

    public static String sign(String token, String method, String path, String query, long epochSeconds,
                              String nonce, byte[] body) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("runner token required");
        }
        return SCHEME + ArtifactSignature.Hmac.hmacHex(token,
                canonical(method, path, query, epochSeconds, nonce, body).getBytes(StandardCharsets.UTF_8));
    }

    public static boolean verify(String token, String method, String path, String query, long epochSeconds,
                                 String nonce, byte[] body, String header) {
        if (header == null || token == null || token.isBlank()) {
            return false;
        }
        return ArtifactSignature.Hmac.constantTimeEquals(
                sign(token, method, path, query, epochSeconds, nonce, body), header.trim().toLowerCase());
    }
}
