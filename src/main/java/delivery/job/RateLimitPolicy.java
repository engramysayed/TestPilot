package delivery.job;

import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Documented public API rate limit: 60 requests / tenant / minute. */
public final class RateLimitPolicy {
    public static final int REQUESTS_PER_MINUTE = 60;

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public boolean allow(String tenantId, Instant now) {
        if (tenantId == null || tenantId.isBlank() || now == null) {
            return false;
        }
        long minute = now.getEpochSecond() / 60;
        Window window = windows.compute(tenantId, (k, existing) -> {
            if (existing == null || existing.minute != minute) {
                return new Window(minute, 1);
            }
            return new Window(minute, existing.count + 1);
        });
        prune(minute);
        return window.count <= REQUESTS_PER_MINUTE;
    }

    private void prune(long currentMinute) {
        Iterator<Map.Entry<String, Window>> it = windows.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue().minute < currentMinute - 1) {
                it.remove();
            }
        }
    }

    private record Window(long minute, int count) {
    }
}
