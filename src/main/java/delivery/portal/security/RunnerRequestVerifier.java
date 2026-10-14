package delivery.portal.security;

import keel.contract.runner.RequestSigner;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * B06: HMAC-SHA256 request verification for /api/v2/runners (runner token is the key).
 * Checks: signature over the canonical request ({@link RequestSigner}), timestamp within ±skew (default 5 min),
 * nonce unused within the skew window (replay cache). No loopback/IP trust anywhere.
 */
public final class RunnerRequestVerifier {
    public enum Result { OK, MISSING, BAD_TIMESTAMP, SKEW, REPLAY, BAD_SIGNATURE }

    public static final Duration DEFAULT_SKEW = Duration.ofMinutes(5);
    static final int MAX_NONCE_LENGTH = 128;
    static final int MAX_CACHE = 100_000;

    private final Clock clock;
    private final Duration skew;
    private final Map<String, Instant> seen = new ConcurrentHashMap<>();

    public RunnerRequestVerifier() {
        this(Clock.systemUTC(), DEFAULT_SKEW);
    }

    public RunnerRequestVerifier(Clock clock, Duration skew) {
        this.clock = clock;
        this.skew = skew;
    }

    public Result verify(String token, String method, String path, String query, String timestamp, String nonce,
                         byte[] body, String signature) {
        if (timestamp == null || nonce == null || signature == null || nonce.isBlank()
                || nonce.length() > MAX_NONCE_LENGTH) {
            return Result.MISSING;
        }
        long ts;
        try {
            ts = Long.parseLong(timestamp.trim());
        } catch (NumberFormatException e) {
            return Result.BAD_TIMESTAMP;
        }
        Instant now = clock.instant();
        Instant at = Instant.ofEpochSecond(ts);
        if (at.isBefore(now.minus(skew)) || at.isAfter(now.plus(skew))) {
            return Result.SKEW;
        }
        if (!RequestSigner.verify(token, method, path, query, ts, nonce, body, signature)) {
            return Result.BAD_SIGNATURE;
        }
        // Only a correctly signed request may consume its nonce (an attacker can't burn nonces).
        String key = token.hashCode() + ":" + nonce;
        evict(now);
        if (seen.putIfAbsent(key, now.plus(skew).plus(skew)) != null) {
            return Result.REPLAY;
        }
        return Result.OK;
    }

    private void evict(Instant now) {
        if (seen.size() < 1024) {
            return;
        }
        for (Iterator<Map.Entry<String, Instant>> it = seen.entrySet().iterator(); it.hasNext(); ) {
            if (it.next().getValue().isBefore(now)) {
                it.remove();
            }
        }
        if (seen.size() > MAX_CACHE) {
            throw new IllegalStateException("runner nonce cache full");
        }
    }

    int cacheSize() {
        return seen.size();
    }
}
