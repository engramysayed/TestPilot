package delivery.portal.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import delivery.excel.ManualTestCase;
import delivery.hunt.AndroidHuntPack;
import delivery.hunt.DryRunHuntService;
import delivery.hunt.HuntBriefBuilder;
import delivery.hunt.HuntJobResult;
import delivery.hunt.HuntPlanner;
import delivery.hunt.HuntRequest;
import delivery.hunt.HuntRuntimeFactory;
import delivery.hunt.LiveHuntService;
import delivery.knowledge.KnowledgePack;
import delivery.knowledge.KnowledgePackGuidance;
import delivery.knowledge.KnowledgePackReader;
import delivery.job.JobCancelledException;
import delivery.job.JobProgressTracker;
import delivery.portal.DeliveryPortalProperties;
import delivery.portal.jobs.DurableJobClaim;
import delivery.portal.model.JobRecord;
import delivery.portal.service.FailureReasonHumanizer;
import delivery.portal.service.GeneratedWorkbookService;
import delivery.portal.service.PortalStore;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

@Component
public class HuntWorker extends AbstractJobWorker {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final DeliveryPortalProperties props;
    private final GeneratedWorkbookService workbooks;
    private final Environment env;
    private final KnowledgePackReader knowledgePackReader;
    private final delivery.agent.harness.AgentHarnessProperties harnessProps;
    private final delivery.portal.service.WorkspaceAiPolicyService workspaceAiPolicy;
    private final delivery.mobile.run.MobileRunOrchestrator mobileRuns;
    private final delivery.mobile.run.MobileCursorPass mobileCursor;

    public HuntWorker(DeliveryPortalProperties props, PortalStore portalStore,
                      GeneratedWorkbookService workbooks, Environment env,
                      KnowledgePackReader knowledgePackReader,
                      delivery.agent.harness.AgentHarnessProperties harnessProps,
                      delivery.portal.service.WorkspaceAiPolicyService workspaceAiPolicy,
                      delivery.mobile.run.MobileRunOrchestrator mobileRuns,
                      delivery.mobile.run.MobileCursorPass mobileCursor) {
        super(portalStore);
        this.props = props;
        this.workbooks = workbooks;
        this.env = env;
        this.knowledgePackReader = knowledgePackReader;
        this.harnessProps = harnessProps;
        this.workspaceAiPolicy = workspaceAiPolicy;
        this.mobileRuns = mobileRuns;
        this.mobileCursor = mobileCursor;
    }

    @Override
    protected JobRecord.JobKind kind() {
        return JobRecord.JobKind.HUNT;
    }

    @Async("huntExecutor")
    public void submit(String jobId) {
        try {
            dispatch(jobId);
        } catch (Exception e) {
            log.error("HuntWorker dispatch failed for {}: {}", jobId, e.getMessage(), e);
        }
    }

    @Override
    protected void runOne(JobRecord job, DurableJobClaim.Lease lease) {
        String jobId = job.getJobId();
        job.setMessage(props.isDryRun() ? "Dry-run Bug Hunter" : "Starting Bug Hunter");
        if (!props.isDryRun()) {
            DurableJobClaim.markStage(
                    job, lease.attemptId(), DurableJobClaim.Stage.BROWSER);
        }
        portalStore.syncJobPersistence(job);
        try {
            delivery.net.InstallNetworkBridge.apply(env);
            Path requestPath = job.getExcelPath();
            HuntRequest request = MAPPER.readValue(requestPath.toFile(), HuntRequest.class);
            request.setJobId(jobId);
            request.setProjectId(job.getProjectId());
            if (request.getDomMode() == null || request.getDomMode().isBlank()) {
                request.setDomMode(props.getHunt().getDomMode());
            }
            request.normalize();

            HuntJobResult result;
            if ("ANDROID".equalsIgnoreCase(request.getPlatform())) {
                Path huntRoot = portalStore.projectDiskRoot(job.getProjectId())
                        .resolve("hunt-runs").resolve(jobId);
                Files.createDirectories(huntRoot);
                BooleanSupplier cancelCheck = () -> portalStore.isCancelRequested(jobId);
                job.setMessage("Exploring the app on the phone");
                portalStore.syncJobPersistence(job);
                result = AndroidHuntPack.pack(mobileRuns, request.getMobileRunId(), huntRoot, cancelCheck);
            } else {
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

            String knowledgeGuidanceMd = "";
            if (props.getHunt().isKnowledgePacksEnabled()
                    && request.getKnowledgePackIds() != null
                    && !request.getKnowledgePackIds().isEmpty()
                    && knowledgePackReader != null) {
                List<KnowledgePack> packs = knowledgePackReader.getByIds(
                        job.getProjectId(), request.getKnowledgePackIds());
                knowledgeGuidanceMd = KnowledgePackGuidance.compose(packs);
                log.info("Hunt job {} knowledgePacks requested={} loaded={} guidanceChars={}",
                        jobId,
                        request.getKnowledgePackIds().size(),
                        packs.size(),
                        knowledgeGuidanceMd.length());
            }
            final String knowledgeGuidance = knowledgeGuidanceMd == null ? "" : knowledgeGuidanceMd;

            BooleanSupplier cancelCheck = () -> portalStore.isCancelRequested(jobId);
            JobProgressTracker tracker = new JobProgressTracker();
            result = runWithProgressMirror(job, tracker, () -> {
                if (props.isDryRun()) {
                    return new DryRunHuntService().run(request, selected, huntRoot, tracker, cancelCheck, knowledgeGuidance);
                }
                HuntPlanner planner = HuntRuntimeFactory.plannerFor(request, delivery.portal.engine.InProcessJobContext.settings(props));
                return new LiveHuntService().run(
                        request,
                        selected,
                        huntRoot,
                        HuntRuntimeFactory.loginRequest(delivery.portal.engine.InProcessJobContext.of(job, props, cancelCheck)),
                        planner,
                        tracker,
                        cancelCheck,
                        Path.of(props.getStoreRoot()),
                        knowledgeGuidance
                );
            });
            }

            if (!DurableJobClaim.sameAttempt(job, lease)) {
                log.info("Hunt job {} fenced; skipping publish", jobId);
                return;
            }
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

            // Optional AI triage pass — opt-in per workspace, charged per task.
            boolean triageAllowed = workspaceAiPolicy == null
                    || workspaceAiPolicy.isHuntValidationEnabled(job.getProjectId());
            if (triageAllowed
                    && harnessProps != null
                    && harnessProps.isEnabled()
                    && harnessProps.isCursorEnabled()
                    && (result.bugCount() > 0 || result.scenarioCount() > 0)
                    && "ANDROID".equalsIgnoreCase(request.getPlatform())) {
                try {
                    String note = mobileCursor.triage(
                            portalStore.projectDiskRoot(job.getProjectId()),
                            jobId,
                            job.getProjectId(),
                            result.message());
                    if (note != null && !note.isBlank()) {
                        job.setMessage(result.message() + " " + note);
                        portalStore.syncJobPersistence(job);
                    }
                    log.info("Phone hunt triage {}", note);
                } catch (Exception triageErr) {
                    log.warn("Phone hunt triage threw, continuing: {}", triageErr.getMessage());
                }
            } else if (triageAllowed
                    && harnessProps != null
                    && harnessProps.isEnabled()
                    && harnessProps.isCursorEnabled()
                    && (result.bugCount() > 0 || result.scenarioCount() > 0)) {
                try {
                    var bridge = new delivery.agent.validation.hunt.HuntHarnessBridge(
                            harnessProps,
                            delivery.agent.harness.AgentHarnessCoordinator.forProduction(harnessProps));
                    var triage = bridge.tryTriage(
                            portalStore.projectDiskRoot(job.getProjectId()),
                            jobId,
                            job.getProjectId(),
                            result.bugCount(),
                            result.scenarioCount(),
                            result.message());
                    log.info("Hunt triage status={} detail={}", triage.status(), triage.detail());
                    if (workspaceAiPolicy != null && "completed".equalsIgnoreCase(triage.status())) {
                        workspaceAiPolicy.chargeForAiTask(job.getProjectId(), "hunt-triage:" + jobId);
                    }
                } catch (Exception triageErr) {
                    log.warn("Hunt triage threw, continuing: {}", triageErr.getMessage());
                }
            }
        } catch (JobCancelledException e) {
            cancel(job);
        } catch (Exception e) {
            if (portalStore.shouldAbortCompletion(job)) {
                cancel(job);
            } else {
                String raw = e.getMessage() == null ? "Hunt failed" : e.getMessage();
                fail(job, FailureReasonHumanizer.forUser(raw), e);
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
                    Thread.sleep(1000L);
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
        String publicMessage = message == null || message.isBlank() ? "Hunt failed" : message;
        log.error("Hunt job {} failed: {}", job.getJobId(),
                e.getMessage() == null ? publicMessage : e.getMessage(), e);
        job.setStatus(JobRecord.Status.FAILED);
        job.setError(publicMessage);
        job.setMessage(publicMessage);
        portalStore.syncJobPersistence(job);
    }

    @FunctionalInterface
    private interface HuntCallable {
        HuntJobResult call() throws Exception;
    }
}
