package keel.contract.runner;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Artifact signing shared by runner and portal: HMAC-SHA256(token, jobId.attemptId + "." + sha256(body)). */
public final class ArtifactSignature {
    private ArtifactSignature() {
    }

    public static String digest(byte[] body) {
        return Hmac.sha256Hex(body == null ? new byte[0] : body);
    }

    public static String signature(String token, String jobId, String attemptId, byte[] body) {
        String key = token == null ? "" : token;
        if (key.isBlank()) {
            throw new IllegalArgumentException("webhook secret is required");
        }
        String payload = (jobId == null ? "" : jobId) + "." + (attemptId == null ? "" : attemptId)
                + "." + digest(body);
        return Hmac.hmacHex(key, payload.getBytes(StandardCharsets.UTF_8));
    }

    public static boolean matches(String token, String jobId, String attemptId, byte[] body, String header) {
        if (header == null || header.isBlank() || token == null || token.isBlank()) {
            return false;
        }
        String incoming = header.trim().toLowerCase();
        if (incoming.startsWith("sha256=")) {
            incoming = incoming.substring(7);
        }
        return Hmac.constantTimeEquals(signature(token, jobId, attemptId, body).toLowerCase(), incoming);
    }

    /** Small JDK-only HMAC helpers (no Spring, no commons). */
    public static final class Hmac {
        private Hmac() {
        }

        public static String sha256Hex(byte[] data) {
            try {
                return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
            } catch (Exception e) {
                throw new IllegalStateException("sha256 unavailable", e);
            }
        }

        public static String hmacHex(String key, byte[] data) {
            try {
                javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
                mac.init(new javax.crypto.spec.SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
                return HexFormat.of().formatHex(mac.doFinal(data));
            } catch (Exception e) {
                throw new IllegalStateException("hmac unavailable", e);
            }
        }

        public static boolean constantTimeEquals(String a, String b) {
            return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
        }
    }
}
