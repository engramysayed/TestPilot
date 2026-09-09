package delivery.portal.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import delivery.excel.ManualTestCase;
import delivery.hunt.DryRunHuntService;
import delivery.hunt.HuntBriefBuilder;
import delivery.hunt.HuntJobResult;
import delivery.hunt.HuntPlanner;
import delivery.hunt.HuntRequest;
import delivery.hunt.HuntRuntimeFactory;
import delivery.hunt.LiveHuntService;
import delivery.job.JobCancelledException;
import delivery.job.JobProgressTracker;
import delivery.portal.DeliveryPortalProperties;
import delivery.portal.model.JobRecord;
import delivery.portal.service.GeneratedWorkbookService;
import delivery.portal.service.PortalStore;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

@Component
public class HuntWorker {
    private static final Logger log = LogManager.getLogger(HuntWorker.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final DeliveryPortalProperties props;
    private final PortalStore portalStore;
    private final GeneratedWorkbookService workbooks;

    public HuntWorker(DeliveryPortalProperties props, PortalStore portalStore,
                      GeneratedWorkbookService workbooks) {
        this.props = props;
        this.portalStore = portalStore;
        this.workbooks = workbooks;
    }

    @Async("huntExecutor")
    public void submit(String jobId) {
        JobRecord job = portalStore.getJob(jobId).orElse(null);
        if (job == null || job.getJobKind() != JobRecord.JobKind.HUNT) {
            return;
        }
        job.setStatus(JobRecord.Status.RUNNING);
        job.setMessage(props.isDryRun() ? "Dry-run Bug Hunter" : "Starting Bug Hunter");
        portalStore.syncJobPersistence(job);
        try {
            Path requestPath = job.getExcelPath();
            HuntRequest request = MAPPER.readValue(requestPath.toFile(), HuntRequest.class);
            request.setJobId(jobId);
            request.setProjectId(job.getProjectId());
            if (request.getDomMode() == null || request.getDomMode().isBlank()) {
                request.setDomMode(props.getHunt().getDomMode());
            }
            request.normalize();

            List<ManualTestCase> all = workbooks.hasWorkbook(job.getProjectId())
                    ? workbooks.readCases(job.getProjectId())
                    : List.of();
            List<ManualTestCase> selected = HuntBriefBuilder.filterSelected(all, request.getTcIds());
            if (selected.isEmpty()) {
                throw new IllegalStateException("No selected library cases matched tcIds");
            }

            Path huntRoot = portalStore.projectDiskRoot(job.getProjectId())
                    .resolve("hunt-runs").resolve(jobId);
            Files.createDirectories(huntRoot);

            BooleanSupplier cancelCheck = () -> portalStore.isCancelRequested(jobId);
            JobProgressTracker tracker = new JobProgressTracker();
            HuntJobResult result = runWithProgressMirror(job, tracker, () -> {
                if (props.isDryRun()) {
                    return new DryRunHuntService().run(request, selected, huntRoot, tracker, cancelCheck);
                }
                HuntPlanner planner = HuntRuntimeFactory.plannerFor(request, props);
                return new LiveHuntService().run(
                        request,
                        selected,
                        huntRoot,
                        HuntRuntimeFactory.loginRequest(job, props),
                        planner,
                        tracker,
                        cancelCheck
                );
            });

            if (portalStore.shouldAbortCompletion(job)) {
                cancel(job);
                return;
            }
            job.setPassedCount(result.bugCount());
            job.setTodoCount(result.scenarioCount());
            job.setZipPath(result.zipPath());
            job.setMessage(result.message());
            job.setStatus(JobRecord.Status.COMPLETED);
            portalStore.syncJobPersistence(job);
            log.info("Hunt job {} completed bugs={} scenarios={} stop={} network={}",
                    jobId, result.bugCount(), result.scenarioCount(),
                    result.stopReason(), result.networkCapture());
        } catch (JobCancelledException e) {
            cancel(job);
        } catch (Exception e) {
            if (portalStore.shouldAbortCompletion(job)) {
                cancel(job);
            } else {
                fail(job, e.getMessage() == null ? "Hunt failed" : e.getMessage(), e);
            }
        } finally {
            portalStore.clearCancelRequest(jobId);
        }
    }

    private HuntJobResult runWithProgressMirror(
            JobRecord job,
            JobProgressTracker tracker,
            HuntCallable callable
    ) throws Exception {
        AtomicBoolean running = new AtomicBoolean(true);
        Thread progressPoll = new Thread(() -> {
            long lastSync = 0L;
            while (running.get()) {
                job.setProgressCurrent(tracker.current());
                job.setProgressTotal(tracker.total());
                if (tracker.message() != null && !tracker.message().isBlank()) {
                    job.setMessage(tracker.message());
                }
                long now = System.currentTimeMillis();
                if (now - lastSync >= 400L) {
                    portalStore.syncJobPersistence(job);
                    lastSync = now;
                }
                try {
                    Thread.sleep(200L);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }, "hunt-progress-" + job.getJobId());
        progressPoll.setDaemon(true);
        progressPoll.start();
        try {
            return callable.call();
        } finally {
            running.set(false);
            progressPoll.interrupt();
            job.setProgressCurrent(tracker.current());
            job.setProgressTotal(tracker.total());
        }
    }

    private void cancel(JobRecord job) {
        job.setStatus(JobRecord.Status.CANCELLED);
        job.setMessage("Cancelled");
        portalStore.syncJobPersistence(job);
    }

    private void fail(JobRecord job, String message, Exception e) {
        log.error("Hunt job {} failed: {}", job.getJobId(), message, e);
        job.setStatus(JobRecord.Status.FAILED);
        job.setError(message);
        job.setMessage(message);
        portalStore.syncJobPersistence(job);
    }

    @FunctionalInterface
    private interface HuntCallable {
        HuntJobResult call() throws Exception;
    }
}
