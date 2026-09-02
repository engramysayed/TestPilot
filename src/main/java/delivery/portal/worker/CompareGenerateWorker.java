package delivery.portal.worker;

import delivery.job.JobCancelledException;
import delivery.portal.DeliveryPortalProperties;
import delivery.portal.model.JobRecord;
import delivery.portal.model.ProjectRecord;
import delivery.portal.service.CompareJobFiles;
import delivery.portal.service.JobUploadCleanup;
import delivery.portal.service.PortalStore;
import delivery.portal.service.TcGenerateService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Map;

@Component
public class CompareGenerateWorker {
    private static final Logger log = LogManager.getLogger(CompareGenerateWorker.class);

    private final DeliveryPortalProperties props;
    private final PortalStore portalStore;
    private final TcGenerateService generate;

    public CompareGenerateWorker(
            DeliveryPortalProperties props,
            PortalStore portalStore,
            TcGenerateService generate
    ) {
        this.props = props;
        this.portalStore = portalStore;
        this.generate = generate;
    }

    @Async("generateBatchExecutor")
    public void submit(String jobId) {
        JobRecord job = portalStore.getJob(jobId).orElse(null);
        if (job == null || job.getJobKind() != JobRecord.JobKind.GENERATE_COMPARE) {
            return;
        }
        job.setStatus(JobRecord.Status.RUNNING);
        job.setProgressTotal(2);
        job.setProgressCurrent(0);
        job.setMessage("Starting model comparison");
        portalStore.syncJobPersistence(job);
        Path payloadPath = job.getExcelPath();
        try {
            if (!props.isGenerateEnabled()) {
                throw new IllegalStateException("GENERATE_DISABLED");
            }
            CompareJobFiles.Payload payload = CompareJobFiles.readPayload(payloadPath);
            ProjectRecord project = portalStore.getOwnedProject(job.getProjectId(), job.getOwnerUserId())
                    .orElseThrow(() -> new IllegalArgumentException("Unknown project"));
            java.util.function.BooleanSupplier cancelCheck = () -> portalStore.isCancelRequested(jobId);

            job.setMessage("Generating with " + payload.modelA() + " (model A)…");
            portalStore.syncJobPersistence(job);
            TcGenerateService.StoryGenerateResult resultA = generate.generateStory(
                    project, payload.stories(), false, null, payload.modelA(), cancelCheck);
            job.setProgressCurrent(1);
            job.setPassedCount(resultA.cases().size());
            portalStore.syncJobPersistence(job);

            if (portalStore.isCancelRequested(jobId)) {
                throw new JobCancelledException();
            }

            job.setMessage("Generating with " + payload.modelB() + " (model B)…");
            portalStore.syncJobPersistence(job);
            TcGenerateService.StoryGenerateResult resultB = generate.generateStory(
                    project, payload.stories(), false, null, payload.modelB(), cancelCheck);
            job.setProgressCurrent(2);
            job.setTodoCount(resultB.cases().size());

            Map<String, Object> compareResult = generate.buildCompareResult(
                    job.getProjectId(), resultA, resultB);
            Path resultFile = CompareJobFiles.resultPath(props.getStoreRoot(), job.getProjectId(), jobId);
            CompareJobFiles.writeResult(resultFile, compareResult);
            job.setZipPath(resultFile);
            job.setMessage("Comparison ready — open Generate to pick a model");
            job.setStatus(JobRecord.Status.COMPLETED);
            portalStore.syncJobPersistence(job);
            log.info("Compare job {} completed modelA={} modelB={}", jobId, payload.modelA(), payload.modelB());
        } catch (JobCancelledException e) {
            cancel(job);
        } catch (IllegalStateException e) {
            fail(job, e.getMessage(), e);
        } catch (Exception e) {
            fail(job, "Model comparison failed", e);
        } finally {
            portalStore.clearCancelRequest(jobId);
            JobUploadCleanup.deleteIfTempUpload(payloadPath);
        }
    }

    private void cancel(JobRecord job) {
        log.info("Compare job {} cancelled by user", job.getJobId());
        job.setMessage("Cancelled by user");
        job.setStatus(JobRecord.Status.CANCELLED);
        portalStore.syncJobPersistence(job);
    }

    private void fail(JobRecord job, String publicMessage, Exception e) {
        log.error("Compare job {} failed: {}", job.getJobId(), publicMessage, e);
        job.setError(publicMessage);
        job.setMessage(publicMessage);
        job.setStatus(JobRecord.Status.FAILED);
        portalStore.syncJobPersistence(job);
    }
}
