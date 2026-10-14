package delivery.portal.api.v2;

import delivery.portal.service.PortalStore;
import org.springframework.stereotype.Component;

/**
 * Fencing tokens for Runner API v2: strictly increasing per job, bumped on every v2 claim. A write carrying any
 * token other than the current one is rejected (409 STALE_FENCING_TOKEN) even if the attempt id still matches.
 * B08: persisted in {@code jobs.fencing_token}, so a portal restart neither resets nor re-seeds them.
 */
@Component
public class RunnerFencing {
    private final PortalStore store;

    public RunnerFencing(PortalStore store) {
        this.store = store;
    }

    public long issue(String jobId) {
        return store.issueFencingToken(jobId);
    }

    /** @return true when {@code presented} is the current persisted token. */
    public boolean accept(String jobId, Long presented) {
        return presented != null && presented >= 1 && store.currentFencingToken(jobId) == presented;
    }

    /** Tokens stay in the DB after the result (a late duplicate write is still fenced). */
    public void release(String jobId) {
    }
}
