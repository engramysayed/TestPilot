package delivery.portal.security;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory login failure window. Locks a principal after N failures. */
public final class LoginThrottle {
    private final int maxFailures;
    private final long windowSeconds;
    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();

    public LoginThrottle(int maxFailures, long windowSeconds) {
        this.maxFailures = Math.max(1, maxFailures);
        this.windowSeconds = Math.max(1, windowSeconds);
    }

    public boolean allow(String key, Instant now) {
        Instant t = now == null ? Instant.now() : now;
        Deque<Instant> q = prune(key, t);
        return q.size() < maxFailures;
    }

    public void recordFailure(String key, Instant now) {
        Instant t = now == null ? Instant.now() : now;
        failures.computeIfAbsent(key, ignored -> new ArrayDeque<>()).addLast(t);
        prune(key, t);
    }

    public void recordSuccess(String key) {
        if (key != null) {
            failures.remove(key);
        }
    }

    public static String key(String username, String remoteAddr) {
        String user = username == null || username.isBlank() ? "unknown" : username.trim().toLowerCase();
        String addr = remoteAddr == null ? "" : remoteAddr;
        return user + "|" + addr;
    }

    public long retryAfterSeconds(String key, Instant now) {
        Instant t = now == null ? Instant.now() : now;
        Deque<Instant> q = prune(key, t);
        if (q.size() < maxFailures) {
            return 0;
        }
        Instant oldest = q.peekFirst();
        if (oldest == null) {
            return 0;
        }
        long remaining = windowSeconds - Math.max(0, t.getEpochSecond() - oldest.getEpochSecond());
        return Math.max(1, remaining);
    }

    private Deque<Instant> prune(String key, Instant now) {
        Deque<Instant> q = failures.computeIfAbsent(key == null ? "" : key, ignored -> new ArrayDeque<>());
        Instant cutoff = now.minusSeconds(windowSeconds);
        while (!q.isEmpty() && q.peekFirst().isBefore(cutoff)) {
            q.removeFirst();
        }
        return q;
    }
}
