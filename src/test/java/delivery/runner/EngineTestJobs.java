package delivery.runner;

import delivery.portal.model.JobRecord;
import delivery.portal.service.PortalStore;

import java.util.UUID;

/** Test helper: the public v1 API only creates EXECUTE jobs, so engine tests derive a private-runner CONVERT job. */
public final class EngineTestJobs {
    private EngineTestJobs() {
    }

    public static String convertLike(PortalStore store, String executeJobId) throws Exception {
        JobRecord src = store.getJob(executeJobId).orElseThrow();
        JobRecord job = new JobRecord(UUID.randomUUID().toString(), src.getProjectId(), src.getOwnerUserId(), "CONVERT",
                src.getExcelPath(), src.getBaseUrl(), "", "", false, JobRecord.JobKind.CONVERT);
        job.setTenantId(src.getTenantId());
        job.setAuthoringEngine(src.getAuthoringEngine());
        job.setRequirePrivateRunner(true);
        store.saveJob(job);
        return job.getJobId();
    }
}
