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
     * Returns the claim only if lease + kind-check passed; empty means the job was wrong-kind
     * or already claimed by another worker. The subclass owns its own error handling inside
     * {@link #runOne} — this method does not catch.
     */
    public final Optional<Claim> dispatch(String jobId) throws Exception {
        JobRecord job = portalStore.getJob(jobId).orElse(null);
        if (job == null || job.getJobKind() != kind()) {
            return Optional.empty();
        }
        Optional<DurableJobClaim.Lease> lease = portalStore.beginWork(jobId);
        if (lease.isEmpty()) {
            log.info("Job {} not claimed by {}", jobId, kind());
            return Optional.empty();
        }
        JobRecord refreshed = portalStore.getJob(jobId).orElse(job);
        runOne(refreshed, lease.get());
        return Optional.of(new Claim(refreshed, lease.get()));
    }

    /** Result of a successful dispatch — subclasses usually ignore this, but tests may inspect it. */
    public record Claim(JobRecord job, DurableJobClaim.Lease lease) {}
}
