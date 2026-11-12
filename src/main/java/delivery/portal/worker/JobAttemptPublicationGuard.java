package delivery.portal.worker;

import delivery.job.PublicationGuard;
import delivery.portal.jobs.DurableJobClaim;
import delivery.portal.service.PortalStore;

import java.util.Optional;
import java.util.concurrent.Callable;

/** Default convert publish fence: {@link PortalStore#publishAttempt} only. */
public final class JobAttemptPublicationGuard implements PublicationGuard {
    private final PortalStore portalStore;
    private final String jobId;
    private final DurableJobClaim.Lease lease;

    public JobAttemptPublicationGuard(PortalStore portalStore, String jobId, DurableJobClaim.Lease lease) {
        this.portalStore = portalStore;
        this.jobId = jobId;
        this.lease = lease;
    }

    @Override
    public <T> T publish(Callable<T> action) throws Exception {
        return portalStore.publishAttempt(jobId, lease, action);
    }
}
