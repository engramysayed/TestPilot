package delivery.portal.worker;

import delivery.portal.jobs.DurableJobClaim;
import delivery.portal.model.JobRecord;
import delivery.portal.service.PortalStore;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Optional;

/**
 * Shared worker scaffold — audit C5 first pass (non-breaking).
 *
 * <p>Every concrete worker ({@link ConversionWorker}, {@link ExecuteWorker},
 * {@link GenerateBatchWorker}, {@link HuntWorker}, {@link CompareGenerateWorker})
 * does the same four things around its real work:
 * <ol>
 *   <li>Fetch the job by id and reject when the kind doesn't match.</li>
 *   <li>Call {@code portalStore.beginWork(jobId)} to claim a lease.</li>
 *   <li>Run the kind-specific body.</li>
 *   <li>Publish success / failure / cancelled via {@link PortalStore#syncJobPersistence}.</li>
 * </ol>
 * The lease / kind-check / sync lines are copy-pasted across the 5 workers. A future
 * refactor can port each one to extend {@code AbstractJobWorker} and override
 * {@link #runOne(JobRecord, DurableJobClaim.Lease)}. This first commit ships the
 * scaffold as a target shape only — no existing worker is migrated. Later turns
 * migrate one worker per commit, prove the test suite stays green, and remove the
 * duplicated plumbing in that worker.
 */
public abstract class AbstractJobWorker {
    protected static final Logger log = LogManager.getLogger(AbstractJobWorker.class);

    protected final PortalStore portalStore;

    protected AbstractJobWorker(PortalStore portalStore) {
        this.portalStore = portalStore;
    }

    /** The kind this worker handles; a job of a different kind is a no-op. */
    protected abstract JobRecord.JobKind kind();

    /** Do the real work. Called only after the lease is claimed and the kind matches. */
    protected abstract void runOne(JobRecord job, DurableJobClaim.Lease lease) throws Exception;

    /**
     * Shared entry point. A concrete subclass calls this from its {@code @Async} method.
     * Returns the lease only if it was claimed; empty means the job was wrong-kind or
     * already claimed by another worker.
     */
    public final Optional<DurableJobClaim.Lease> dispatch(String jobId) {
        JobRecord job = portalStore.getJob(jobId).orElse(null);
        if (job == null || job.getJobKind() != kind()) {
            return Optional.empty();
        }
        Optional<DurableJobClaim.Lease> lease = portalStore.beginWork(jobId);
        if (lease.isEmpty()) {
            log.info("Job {} not claimed by {}", jobId, kind());
            return Optional.empty();
        }
        try {
            JobRecord refreshed = portalStore.getJob(jobId).orElse(job);
            runOne(refreshed, lease.get());
        } catch (Exception e) {
            // Subclass normally handles its own error reporting inside runOne. This catch is a
            // safety net so an unexpected throw still marks the job FAILED and releases the lease.
            log.error("Unhandled failure in {} for job {}: {}", kind(), jobId, e.getMessage(), e);
            portalStore.getJob(jobId).ifPresent(j -> {
                j.setStatus(JobRecord.Status.FAILED);
                j.setMessage("Worker error: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
                portalStore.syncJobPersistence(j);
            });
        }
        return lease;
    }
}
