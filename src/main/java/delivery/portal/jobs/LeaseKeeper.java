package delivery.portal.jobs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiPredicate;

/**
 * Keeps every claimed job's lease alive while its worker runs (Execute, Hunt, Generate, Compare, Conversion),
 * so {@code reconcileExpiredLeases()} can run on a timer without reclaiming healthy jobs.
 * <p>
 * Renewal stops on its own when the heartbeat is refused (job finished, cancelled, fenced) or when the
 * job exceeds {@code maxRuntime} — a hung worker then lets its lease lapse and the reconciler recovers it.
 */
public final class LeaseKeeper implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(LeaseKeeper.class);
    /** Lease TTL is 45s; renew at a third of it. */
    public static final Duration INTERVAL = Duration.ofSeconds(15);

    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "job-lease-keeper");
        t.setDaemon(true);
        return t;
    });
    private final Map<String, ScheduledFuture<?>> active = new ConcurrentHashMap<>();
    private final BiPredicate<String, DurableJobClaim.Lease> heartbeat;
    private final Duration maxRuntime;

    public LeaseKeeper(BiPredicate<String, DurableJobClaim.Lease> heartbeat, Duration maxRuntime) {
        this.heartbeat = heartbeat;
        this.maxRuntime = maxRuntime == null || maxRuntime.isZero() || maxRuntime.isNegative()
                ? Duration.ofHours(3) : maxRuntime;
    }

    public void track(String jobId, DurableJobClaim.Lease lease) {
        if (jobId == null || lease == null) {
            return;
        }
        Instant deadline = Instant.now().plus(maxRuntime);
        ScheduledFuture<?> previous = active.remove(jobId);
        if (previous != null) {
            previous.cancel(false);
        }
        ScheduledFuture<?>[] self = new ScheduledFuture<?>[1];
        self[0] = timer.scheduleAtFixedRate(() -> {
            try {
                if (Instant.now().isAfter(deadline)) {
                    log.warn("Job {} exceeded max runtime {}; letting lease lapse for reconcile", jobId, maxRuntime);
                    stop(jobId, self[0]);
                    return;
                }
                if (!heartbeat.test(jobId, lease)) {
                    stop(jobId, self[0]);
                }
            } catch (RuntimeException e) {
                log.debug("Lease heartbeat for {} failed: {}", jobId, e.getMessage());
            }
        }, INTERVAL.toMillis(), INTERVAL.toMillis(), TimeUnit.MILLISECONDS);
        active.put(jobId, self[0]);
    }

    private void stop(String jobId, ScheduledFuture<?> f) {
        if (f != null) {
            f.cancel(false);
        }
        active.remove(jobId, f);
    }

    public int trackedCount() {
        return active.size();
    }

    @Override
    public void close() {
        timer.shutdownNow();
    }
}
