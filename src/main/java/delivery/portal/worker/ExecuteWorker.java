package delivery.portal.worker;

import delivery.job.ConversionJobRequest;
import delivery.job.DryRunExecuteService;
import delivery.job.ExecuteJobResult;
import delivery.job.ExecuteJobRunner;
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
public class ExecuteWorker {
    private static final Logger log = LogManager.getLogger(ExecuteWorker.class);

    private final DeliveryPortalProperties props;
    private final PortalStore portalStore;

    public ExecuteWorker(DeliveryPortalProperties props, PortalStore portalStore) {
        this.props = props;
        this.portalStore = portalStore;
    }

    @Async("executeExecutor")
    public void submit(String jobId) {
        JobRecord job = portalStore.getJob(jobId).orElse(null);
        if (job == null || job.getJobKind() != JobRecord.JobKind.EXECUTE) {
            return;
        }
        job.setStatus(JobRecord.Status.RUNNING);
        job.setMessage(props.isDryRun() ? "Dry-run execute" : "Starting execute");
        portalStore.syncJobPersistence(job);
        try {
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
                    false
            );

            ExecuteJobResult result;
            BooleanSupplier cancelCheck = () -> portalStore.isCancelRequested(jobId);
            if (props.isDryRun()) {
                JobProgressTracker tracker = new JobProgressTracker();
                result = runWithProgressMirror(job, tracker,
                        () -> new DryRunExecuteService().run(request, jobId, tracker, cancelCheck));
            } else {
                ExecuteJobRunner runner = new ExecuteJobRunner();
                result = runWithProgressMirror(job, runner.progress(),
                        () -> runner.run(request, jobId, cancelCheck));
            }

            job.setPassedCount(result.passed());
            job.setTodoCount(result.todo());
            job.setMessage(result.message());
            if (portalStore.shouldAbortCompletion(job)) {
                cancel(job);
            } else if (result.passed() == 0 && result.todo() > 0) {
                job.setStatus(JobRecord.Status.FAILED);
                job.setError("ALL_CASES_TODO");
                job.setMessage(result.message() == null || result.message().isBlank()
                        ? "No cases passed — all TODO/PARTIAL (hard stop)"
                        : result.message());
                portalStore.syncJobPersistence(job);
            } else {
                job.setStatus(JobRecord.Status.COMPLETED);
                portalStore.syncJobPersistence(job);
            }
            log.info("Execute job {} completed passed={} todo={}", jobId, result.passed(), result.todo());
        } catch (JobCancelledException e) {
            cancel(job);
        } catch (IllegalStateException e) {
            if (portalStore.shouldAbortCompletion(job)) {
                cancel(job);
            } else {
                fail(job, e.getMessage(), e);
            }
        } catch (Exception e) {
            if (portalStore.shouldAbortCompletion(job)) {
                cancel(job);
            } else {
                fail(job, "Execute failed", e);
            }
        } finally {
            portalStore.clearCancelRequest(jobId);
            JobUploadCleanup.deleteIfTempUpload(job.getExcelPath());
        }
    }

    private ExecuteJobResult runWithProgressMirror(
            JobRecord job,
            JobProgressTracker tracker,
            ExecuteCallable callable
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
        }, "execute-progress-" + job.getJobId());
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
        log.info("Execute job {} cancelled by user", job.getJobId());
        job.setMessage("Cancelled by user");
        job.setStatus(JobRecord.Status.CANCELLED);
        portalStore.syncJobPersistence(job);
    }

    private void fail(JobRecord job, String publicMessage, Exception e) {
        log.error("Execute job {} failed: {}", job.getJobId(), publicMessage, e);
        job.setError(publicMessage);
        job.setMessage(publicMessage);
        job.setStatus(JobRecord.Status.FAILED);
        portalStore.syncJobPersistence(job);
    }

    @FunctionalInterface
    private interface ExecuteCallable {
        ExecuteJobResult call() throws Exception;
    }
}
