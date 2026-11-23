package delivery.mobile.run;

import delivery.mobile.runner.precision.PrecisionOutcome;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * In-memory ring buffer of the last {@link #CAPACITY} mobile Precision picks, per portal
 * process. Powers the admin dashboard at {@code /admin/mobile-precision} — no persistence,
 * no DB table, no JPA entity (pick traffic is small and the dashboard is operational, not
 * historical).
 *
 * <p>Oldest entries are evicted first. Reads are snapshotted into a fresh list so callers
 * see a consistent view without holding the lock.
 */
@Component
public class MobilePrecisionPickLog {

    static final int CAPACITY = 500;

    private final Deque<Entry> buffer = new ArrayDeque<>(CAPACITY);
    private final Object lock = new Object();

    /** Write-side. */
    public void record(String runId, String projectId, String tenantId,
                       String intent, int candidateCount, PrecisionOutcome outcome) {
        if (outcome == null) return;
        Entry e = new Entry(Instant.now().toEpochMilli(),
                safe(runId), safe(projectId), safe(tenantId),
                safe(intent), candidateCount,
                safe(outcome.code()),
                safe(outcome.chosenId()),
                safe(outcome.usedProvider()),
                safe(outcome.fallbackReason()),
                outcome.countsAsCursor());
        synchronized (lock) {
            if (buffer.size() >= CAPACITY) buffer.removeFirst();
            buffer.addLast(e);
        }
    }

    /** Most recent first. Snapshot — safe to iterate without a lock. */
    public List<Entry> recent(int limit) {
        List<Entry> out;
        synchronized (lock) {
            out = new ArrayList<>(buffer);
        }
        Collections.reverse(out);
        if (limit > 0 && out.size() > limit) {
            return out.subList(0, limit);
        }
        return out;
    }

    /** Test helper so a test doesn't need a fresh Spring context to disable the log. */
    static MobilePrecisionPickLog inert() {
        return new MobilePrecisionPickLog() {
            @Override public void record(String runId, String projectId, String tenantId,
                                         String intent, int candidateCount, PrecisionOutcome outcome) {
                // no-op
            }
        };
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    /** One picked / fallback / rejected row. */
    public record Entry(long epochMs,
                        String runId, String projectId, String tenantId,
                        String intent, int candidateCount,
                        String code, String chosenId, String usedProvider,
                        String fallbackReason, boolean countsAsCursor) {
    }
}
