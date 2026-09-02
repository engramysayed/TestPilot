package delivery.portal.worker;

import delivery.job.GenerateBatchJobRequest;
import delivery.job.GenerateBatchJobResult;
import delivery.job.GenerateBatchJobRunner;
import delivery.job.JobCancelledException;
import delivery.job.JobProgressTracker;
import delivery.portal.DeliveryPortalProperties;
import delivery.portal.model.JobRecord;
import delivery.portal.model.ProjectRecord;
import delivery.portal.service.GeneratedWorkbookService;
import delivery.portal.service.JobUploadCleanup;
import delivery.portal.service.PortalStore;
import delivery.portal.service.TcGenerateService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

@Component
public class GenerateBatchWorker {
    private static final Logger log = LogManager.getLogger(GenerateBatchWorker.class);

    private final DeliveryPortalProperties props;
    private final PortalStore portalStore;
    private final TcGenerateService generate;
    private final GeneratedWorkbookService workbooks;

    public GenerateBatchWorker(
            DeliveryPortalProperties props,
            PortalStore portalStore,
            TcGenerateService generate,
            GeneratedWorkbookService workbooks
    ) {
        this.props = props;
        this.portalStore = portalStore;
        this.generate = generate;
        this.workbooks = workbooks;
    }

    @Async("generateBatchExecutor")
    public void submit(String jobId) {
        JobRecord job = portalStore.getJob(jobId).orElse(null);
        if (job == null || job.getJobKind() != JobRecord.JobKind.GENERATE_BATCH) {
            return;
        }
        job.setStatus(JobRecord.Status.RUNNING);
        job.setMessage("Starting generate batch");
        portalStore.syncJobPersistence(job);
        try {
            if (!props.isGenerateEnabled()) {
                throw new IllegalStateException("GENERATE_DISABLED");
            }
            ProjectRecord project = portalStore.getOwnedProject(job.getProjectId(), job.getOwnerUserId())
                    .orElseThrow(() -> new IllegalArgumentException("Unknown project"));
            Path outputDir = Path.of(props.getStoreRoot(), job.getProjectId(), "generate-runs", jobId);
            GenerateBatchJobRequest request = new GenerateBatchJobRequest(
                    project,
                    job.getExcelPath(),
                    outputDir,
                    job.isFinalRevise(),
                    false,
                    job.getGenerateModel()
            );
            GenerateBatchJobRunner runner = new GenerateBatchJobRunner();
            BooleanSupplier cancelCheck = () -> portalStore.isCancelRequested(jobId);
            GenerateBatchJobResult result = runWithProgressMirror(
                    job,
                    runner.progress(),
                    () -> runner.run(request, generate, cancelCheck)
            );
            job.setPassedCount(result.generatedTcCount());
            job.setTodoCount(result.failedStoryCount());
            job.setZipPath(result.outputCsv());
            job.setMessage(result.message());
            job.setStatus(JobRecord.Status.COMPLETED);
            workbooks.saveFromCsvFile(
                    job.getProjectId(), result.outputCsv(), "GENERATE_BATCH", jobId, job.getGenerateModel());
            portalStore.syncJobPersistence(job);
            log.info("Generate batch {} completed tcs={} failedStories={}",
                    jobId, result.generatedTcCount(), result.failedStoryCount());
        } catch (JobCancelledException e) {
            cancel(job);
        } catch (IllegalStateException e) {
            fail(job, e.getMessage(), e);
        } catch (Exception e) {
            fail(job, "Generate batch failed", e);
        } finally {
            portalStore.clearCancelRequest(jobId);
            JobUploadCleanup.deleteIfTempUpload(job.getExcelPath());
        }
    }

    private GenerateBatchJobResult runWithProgressMirror(
            JobRecord job,
            JobProgressTracker tracker,
            Callable callable
    ) throws Exception {
        AtomicBoolean running = new AtomicBoolean(true);
        Thread progressPoll = new Thread(() -> {
            long lastSync = 0L;
            while (running.get()) {
                mirrorProgress(job, tracker);
                long now = System.currentTimeMillis();
                if (now - lastSync >= 400L) {
                    portalStore.syncJobPersistence(job);
                    lastSync = now;
                }
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }, "gen-batch-progress-" + job.getJobId());
        progressPoll.setDaemon(true);
        progressPoll.start();
        try {
            return callable.call();
        } finally {
            running.set(false);
            progressPoll.interrupt();
            mirrorProgress(job, tracker);
            portalStore.syncJobPersistence(job);
        }
    }

    private static void mirrorProgress(JobRecord job, JobProgressTracker tracker) {
        job.setProgressCurrent(tracker.current());
        job.setProgressTotal(tracker.total());
        job.setPassedCount(tracker.passed());
        job.setTodoCount(tracker.todo());
        if (tracker.message() != null && !tracker.message().isBlank()) {
            job.setMessage(tracker.message());
        }
    }

    private void cancel(JobRecord job) {
        log.info("Generate batch {} cancelled by user", job.getJobId());
        job.setMessage("Cancelled by user");
        job.setStatus(JobRecord.Status.CANCELLED);
        portalStore.syncJobPersistence(job);
    }

    private void fail(JobRecord job, String publicMessage, Exception e) {
        log.error("Generate batch {} failed: {}", job.getJobId(), publicMessage, e);
        job.setError(publicMessage);
        job.setMessage(publicMessage);
        job.setStatus(JobRecord.Status.FAILED);
        portalStore.syncJobPersistence(job);
    }

    @FunctionalInterface
    private interface Callable {
        GenerateBatchJobResult call() throws Exception;
    }
}
