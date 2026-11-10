package delivery.portal.worker;

import delivery.authoring.PrecisionJobConfig;
import delivery.job.ConversionJobRequest;
import delivery.job.ConversionJobResult;
import delivery.job.ConversionJobRunner;
import delivery.job.RecordedCaptureFile;
import delivery.job.DryRunConversionService;
import delivery.job.JobCancelledException;
import delivery.job.JobProgressTracker;
import delivery.portal.DeliveryPortalProperties;
import delivery.portal.jobs.DurableJobClaim;
import delivery.portal.model.JobRecord;
import delivery.portal.service.JobUploadCleanup;
import delivery.portal.service.PortalStore;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

@Component
public class ConversionWorker extends AbstractJobWorker {

    private final DeliveryPortalProperties props;
    private final Environment env;

    public ConversionWorker(DeliveryPortalProperties props, PortalStore portalStore, Environment env) {
        super(portalStore);
        this.props = props;
        this.env = env;
    }

    @Override
    protected JobRecord.JobKind kind() {
        return JobRecord.JobKind.CONVERT;
    }

    @Async("conversionExecutor")
    public void submit(String jobId) {
        try {
            dispatch(jobId);
        } catch (Exception e) {
            log.error("ConversionWorker dispatch failed for {}: {}", jobId, e.getMessage(), e);
        }
    }

    @Override
    protected void runOne(JobRecord job, DurableJobClaim.Lease lease) {
        String jobId = job.getJobId();
        Path recordedFile = RecordedCaptureFile.path(Path.of(props.getWorkDir()), jobId);
        boolean fromRecording = Files.isRegularFile(recordedFile);
        job.setMessage(fromRecording
                ? "Building the framework from the recording"
                : (props.isDryRun() ? "Dry-run packaging" : "Starting conversion"));
        if (!props.isDryRun()) {
            DurableJobClaim.markStage(
                    job, lease.attemptId(),
                    fromRecording
                            ? DurableJobClaim.Stage.EMIT
                            : DurableJobClaim.Stage.BROWSER);
        }
        portalStore.syncJobPersistence(job);
        try {
            delivery.net.InstallNetworkBridge.apply(env);
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
            PrecisionJobConfig precisionConfig = portalStore.precisionConfigForJob(job);
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
                    effectiveFinalRevise,
                    props.isCodegenOllamaNaming(),
                    job.getAuthoringEngine(),
                    precisionConfig,
                    delivery.identity.TenantResolver.require(
                            Path.of(props.getStoreRoot()), job.getTenantId(), job.getOwnerUserId()),
                    job.getJobId(),
                    delivery.job.TenantScope.HOSTED
            );

            ConversionJobResult result;
            BooleanSupplier cancelCheck = () -> !portalStore.ownsAttempt(
                    portalStore.getJob(jobId).orElse(null), lease);
            delivery.job.PublicationGuard guard = new delivery.job.PublicationGuard() {
                public <T> T publish(java.util.concurrent.Callable<T> action) throws Exception {
                    return portalStore.publishAttempt(jobId, lease, action);
                }
            };
            if (fromRecording) {
                var captures = RecordedCaptureFile.read(recordedFile);
                ConversionJobRunner runner = new ConversionJobRunner();
                result = runWithProgressMirror(job, runner.progress(), lease,
                        () -> runner.runRecorded(request, captures, cancelCheck, guard));
            } else if (props.isDryRun()) {
                JobProgressTracker tracker = new JobProgressTracker();
                result = runWithProgressMirror(job, tracker, lease,
                        () -> new DryRunConversionService().run(request, tracker, cancelCheck, guard));
            } else {
                ConversionJobRunner runner = new ConversionJobRunner();
                result = runWithProgressMirror(job, runner.progress(), lease,
                        () -> runner.run(request, cancelCheck, guard));
            }

            job.setPassedCount(result.passed());
            job.setTodoCount(result.todo());
            job.setZipPath(result.zipFile());
            job.setMessage(result.message());
            if (!sameAttempt(job, lease) || JobRecord.isTerminal(job.getStatus())) {
                log.info("Job {} fenced; skipping publish", jobId);
            } else if (portalStore.shouldAbortCompletion(job)) {
                cancel(job);
            } else if (result.softBlocked() || "COMPLETED_WITH_BLOCK".equals(result.jobStatus())) {
                job.setStatus(JobRecord.Status.COMPLETED_WITH_BLOCK);
                job.setError("FINAL_REVISE_BLOCK");
                portalStore.syncJobPersistence(job);
            } else if (!props.isDryRun() && result.passed() == 0 && result.todo() > 0) {
                job.setStatus(JobRecord.Status.FAILED);
                job.setError("ALL_CASES_TODO");
                job.setMessage(result.message() == null || result.message().isBlank()
                        ? "No cases proven — all TODO/PARTIAL (hard stop)"
                        : result.message());
                portalStore.syncJobPersistence(job);
            } else {
                job.setStatus(JobRecord.Status.COMPLETED);
                portalStore.syncJobPersistence(job);
            }
            if (!portalStore.shouldAbortCompletion(job)
                    && job.getStatus() != JobRecord.Status.CANCELLED
                    && sameAttempt(job, lease)) {
                try {
                    int version = portalStore.filesystemStore().load(job.getProjectId()).version();
                    portalStore.updateProjectVersion(job.getProjectId(), version);
                } catch (Exception e) {
                    log.error("Failed to refresh project version for {}", job.getProjectId(), e);
                }
            }
            log.info("Job {} completed passed={} todo={}", jobId, result.passed(), result.todo());
        } catch (JobCancelledException e) {
            if (sameAttempt(job, lease) && !JobRecord.isTerminal(job.getStatus())) {
                if (portalStore.shouldAbortCompletion(job)) cancel(job);
                else portalStore.reconcileExpiredLeases();
            }
        } catch (IllegalStateException e) {
            if (!sameAttempt(job, lease) || JobRecord.isTerminal(job.getStatus())) {
                log.info("Job {} fenced after error; skipping fail", jobId);
            } else if (portalStore.shouldAbortCompletion(job)) {
                cancel(job);
            } else {
                fail(job, e.getMessage(), e);
            }
        } catch (Exception e) {
            if (!sameAttempt(job, lease) || JobRecord.isTerminal(job.getStatus())) {
                log.info("Job {} fenced after error; skipping fail", jobId);
            } else if (portalStore.shouldAbortCompletion(job)) {
                cancel(job);
            } else {
                fail(job, "Conversion failed", e);
            }
        } finally {
            portalStore.clearCancelRequest(jobId);
            try {
                Files.deleteIfExists(RecordedCaptureFile.path(Path.of(props.getWorkDir()), jobId));
            } catch (Exception ignored) {
                // the sidecar is only a hint for this job
            }
            JobUploadCleanup.deleteIfTempUpload(job.getExcelPath());
        }
    }

    private ConversionJobResult runWithProgressMirror(
            JobRecord job,
            JobProgressTracker tracker,
            DurableJobClaim.Lease lease,
            ConversionCallable callable
    ) throws Exception {
        AtomicBoolean running = new AtomicBoolean(true);
        Thread progressPoll = new Thread(() -> {
            long lastSync = 0L;
            while (running.get()) {
                mirrorProgress(job, tracker);
                long now = System.currentTimeMillis();
                if (now - lastSync >= 400L) {
                    portalStore.heartbeat(job.getJobId(), lease);
                    lastSync = now;
                }
                try {
                    Thread.sleep(1000);
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
            portalStore.heartbeat(job.getJobId(), lease);
        }
    }

    private static boolean sameAttempt(JobRecord job, DurableJobClaim.Lease lease) {
        return job != null && lease != null && lease.attemptId().equals(job.getAttemptId());
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
