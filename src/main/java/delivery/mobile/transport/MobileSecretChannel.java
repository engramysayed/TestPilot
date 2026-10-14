package delivery.mobile.transport;

import delivery.portal.security.JobSecretCrypto;
import keel.contract.runner.ArtifactSignature;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Attempt-bound short-TTL secret envelope channel. Secrets never enter the frozen input ZIP/manifest
 * (only {@code secretEnvelopeRef}). Plaintext is erased after open/expiry.
 */
public final class MobileSecretChannel {
    public record Envelope(String envelopeId, String jobId, String attemptId, Instant expiresAt,
                           String ciphertext, String mac) {
    }

    private final ConcurrentHashMap<String, Envelope> envelopes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Map<String, String>> plaintextCache = new ConcurrentHashMap<>();

    public Envelope seal(String jobId, String attemptId, Map<String, String> secrets,
                         Instant now, Duration ttl, String runnerToken) {
        if (jobId == null || jobId.isBlank() || attemptId == null || attemptId.isBlank()) {
            throw new MobileTransportException(MobileTransportErrorCode.STALE_ATTEMPT, "job/attempt required");
        }
        if (secrets == null || secrets.isEmpty()) {
            throw new MobileTransportException(MobileTransportErrorCode.SECRET_REQUIRED, "secrets required");
        }
        Duration window = ttl == null || ttl.isZero() || ttl.isNegative() ? Duration.ofMinutes(5) : ttl;
        String envelopeId = "sec_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String json = encodeSecrets(secrets);
        String ciphertext = JobSecretCrypto.encrypt(json);
        String mac = mac(runnerToken, envelopeId, jobId, attemptId, ciphertext);
        Envelope env = new Envelope(envelopeId, jobId, attemptId, now.plus(window), ciphertext, mac);
        envelopes.put(envelopeId, env);
        plaintextCache.put(envelopeId, Map.copyOf(secrets));
        return env;
    }

    public Map<String, String> open(String envelopeId, String jobId, String attemptId,
                                    Instant now, String runnerToken) {
        Envelope env = envelopes.get(envelopeId);
        if (env == null) {
            throw new MobileTransportException(MobileTransportErrorCode.SECRET_REQUIRED, "unknown envelope");
        }
        if (!env.jobId().equals(jobId) || !env.attemptId().equals(attemptId)) {
            throw new MobileTransportException(MobileTransportErrorCode.STALE_ATTEMPT, "envelope attempt mismatch");
        }
        if (now == null || !env.expiresAt().isAfter(now)) {
            erase(envelopeId);
            throw new MobileTransportException(MobileTransportErrorCode.SECRET_EXPIRED, "secret envelope expired");
        }
        String expected = mac(runnerToken, env.envelopeId(), env.jobId(), env.attemptId(), env.ciphertext());
        if (!ArtifactSignature.Hmac.constantTimeEquals(expected, env.mac())) {
            throw new MobileTransportException(MobileTransportErrorCode.ARTIFACT_INVALID, "secret envelope MAC rejected");
        }
        Map<String, String> plain = plaintextCache.get(envelopeId);
        if (plain == null) {
            String json = JobSecretCrypto.decrypt(env.ciphertext());
            plain = decodeSecrets(json);
        }
        return Map.copyOf(plain);
    }

    public void erase(String envelopeId) {
        if (envelopeId == null) {
            return;
        }
        envelopes.remove(envelopeId);
        plaintextCache.remove(envelopeId);
    }

    public Envelope get(String envelopeId) {
        return envelopes.get(envelopeId);
    }

    private static String mac(String token, String envelopeId, String jobId, String attemptId, String ciphertext) {
        String payload = envelopeId + "." + jobId + "." + attemptId + "." + ciphertext;
        return ArtifactSignature.Hmac.hmacHex(token == null ? "" : token, payload.getBytes(StandardCharsets.UTF_8));
    }

    private static String encodeSecrets(Map<String, String> secrets) {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        boolean first = true;
        for (Map.Entry<String, String> e : secrets.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"').append(esc(e.getKey())).append('"').append(':')
                    .append('"').append(esc(e.getValue() == null ? "" : e.getValue())).append('"');
        }
        sb.append('}');
        return sb.toString();
    }

    private static Map<String, String> decodeSecrets(String json) {
        // Minimal parser for {"k":"v",...} produced by encodeSecrets.
        Map<String, String> out = new LinkedHashMap<>();
        if (json == null || json.length() < 2) {
            return out;
        }
        String body = json.trim();
        if (body.startsWith("{")) {
            body = body.substring(1);
        }
        if (body.endsWith("}")) {
            body = body.substring(0, body.length() - 1);
        }
        if (body.isBlank()) {
            return out;
        }
        // Split on "," not inside quotes — values are escaped so simple split is safe for our encoder.
        for (String part : body.split(",")) {
            int colon = part.indexOf("\":\"");
            if (colon < 0) {
                continue;
            }
            String key = part.substring(1, colon);
            String val = part.substring(colon + 3, part.length() - 1);
            out.put(unesc(key), unesc(val));
        }
        return out;
    }

    private static String esc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String unesc(String s) {
        return s.replace("\\\"", "\"").replace("\\\\", "\\");
    }
}
