package delivery.portal.security;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory login failure window. Locks a principal after N failures. */
public final class LoginThrottle {
    /** When more than this many distinct keys sit in memory, sweep keys whose window has elapsed. */
    static final int SOFT_CAP = 10_000;

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
        if (failures.size() > SOFT_CAP) {
            sweepIdle(t);
        }
    }

    /** Remove every key whose window has fully elapsed. Bounds memory under IP-rotation floods. */
    private void sweepIdle(Instant now) {
        Instant cutoff = now.minusSeconds(windowSeconds);
        for (Iterator<Map.Entry<String, Deque<Instant>>> it = failures.entrySet().iterator(); it.hasNext(); ) {
            Deque<Instant> q = it.next().getValue();
            synchronized (q) {
                Instant last = q.peekLast();
                if (last == null || last.isBefore(cutoff)) {
                    it.remove();
                }
            }
        }
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
