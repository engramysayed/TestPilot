package delivery.portal.worker;

import delivery.job.ConversionJobRequest;
import delivery.job.ConversionJobResult;
import delivery.job.ConversionJobRunner;
import delivery.job.DryRunConversionService;
import delivery.job.JobCancelledException;
import delivery.job.JobProgressTracker;
import delivery.portal.DeliveryPortalProperties;
import delivery.portal.model.JobRecord;
import delivery.portal.service.JobUploadCleanup;
import delivery.portal.service.PortalStore;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

@Component
public class ConversionWorker {
    private static final Logger log = LogManager.getLogger(ConversionWorker.class);

    private final DeliveryPortalProperties props;
    private final PortalStore portalStore;

    public ConversionWorker(DeliveryPortalProperties props, PortalStore portalStore) {
        this.props = props;
        this.portalStore = portalStore;
    }

    @Async("conversionExecutor")
    public void submit(String jobId) {
        JobRecord job = portalStore.getJob(jobId).orElse(null);
        if (job == null) {
            return;
        }
        job.setStatus(JobRecord.Status.RUNNING);
        job.setMessage(props.isDryRun() ? "Dry-run packaging" : "Starting conversion");
        portalStore.syncJobPersistence(job);
        try {
            // Bridge Spring delivery.final-revise.* into system props for AgentRouterClient
            System.setProperty("delivery.final-revise.enabled", String.valueOf(props.isFinalReviseEnabled()));
            if (props.getFinalReviseBaseUrl() != null && !props.getFinalReviseBaseUrl().isBlank()) {
                System.setProperty("delivery.final-revise.base-url", props.getFinalReviseBaseUrl());
            }
            if (props.getFinalReviseModel() != null && !props.getFinalReviseModel().isBlank()) {
                System.setProperty("delivery.final-revise.model", props.getFinalReviseModel());
            }
            if (props.getFinalReviseApiKey() != null && !props.getFinalReviseApiKey().isBlank()) {
                System.setProperty("delivery.final-revise.api-key", props.getFinalReviseApiKey());
            }

            boolean effectiveFinalRevise = props.isFinalReviseEnabled() && job.isFinalRevise();
            ConversionJobRequest request = new ConversionJobRequest(
                    job.getProjectId(),
                    job.getExcelPath(),
                    job.getBaseUrl(),
                    job.getUsername(),
                    job.getPassword(),
                    Path.of(props.getWorkDir()),
                    Path.of(props.getStoreRoot()),
                    Path.of(props.getTemplateRoot()),
                    job.getMode(),
                    props.getLlmBaseUrl(),
                    props.getLlmModel(),
                    effectiveFinalRevise
            );

            ConversionJobResult result;
            BooleanSupplier cancelCheck = () -> portalStore.isCancelRequested(jobId);
            if (props.isDryRun()) {
                JobProgressTracker tracker = new JobProgressTracker();
                result = runWithProgressMirror(job, tracker,
                        () -> new DryRunConversionService().run(request, tracker, cancelCheck));
            } else {
                ConversionJobRunner runner = new ConversionJobRunner();
                result = runWithProgressMirror(job, runner.progress(), () -> runner.run(request, cancelCheck));
            }

            job.setPassedCount(result.passed());
            job.setTodoCount(result.todo());
            job.setZipPath(result.zipFile());
            job.setMessage(result.message());
            if (result.softBlocked() || "COMPLETED_WITH_BLOCK".equals(result.jobStatus())) {
                job.setStatus(JobRecord.Status.COMPLETED_WITH_BLOCK);
                job.setError("FINAL_REVISE_BLOCK");
            } else {
                job.setStatus(JobRecord.Status.COMPLETED);
            }
            try {
                int version = portalStore.filesystemStore().load(job.getProjectId()).version();
                portalStore.updateProjectVersion(job.getProjectId(), version);
            } catch (Exception e) {
                log.error("Failed to refresh project version for {}", job.getProjectId(), e);
            }
            portalStore.syncJobPersistence(job);
            log.info("Job {} completed passed={} todo={}", jobId, result.passed(), result.todo());
        } catch (JobCancelledException e) {
            cancel(job);
        } catch (IllegalStateException e) {
            fail(job, e.getMessage(), e);
        } catch (Exception e) {
            fail(job, "Conversion failed", e);
        } finally {
            portalStore.clearCancelRequest(jobId);
            JobUploadCleanup.deleteIfTempUpload(job.getExcelPath());
        }
    }

    private ConversionJobResult runWithProgressMirror(
            JobRecord job,
            JobProgressTracker tracker,
            ConversionCallable callable
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
        }, "progress-" + job.getJobId());
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
        log.info("Job {} cancelled by user", job.getJobId());
        job.setMessage("Cancelled by user");
        job.setStatus(JobRecord.Status.CANCELLED);
        portalStore.syncJobPersistence(job);
    }

    private void fail(JobRecord job, String publicMessage, Exception e) {
        log.error("Job {} failed: {}", job.getJobId(), publicMessage, e);
        job.setError(publicMessage);
        job.setMessage(publicMessage);
        job.setStatus(JobRecord.Status.FAILED);
        portalStore.syncJobPersistence(job);
    }

    @FunctionalInterface
    private interface ConversionCallable {
        ConversionJobResult call() throws Exception;
    }
}
