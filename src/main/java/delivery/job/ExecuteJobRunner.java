package delivery.job;

import delivery.excel.ExcelTcReader;
import delivery.excel.KeelPathCaseFilter;
import delivery.excel.ManualTestCase;
import delivery.ir.TcDraft;
import delivery.ir.TcDraftStore;
import delivery.ir.TcIdentity;
import delivery.agent.harness.recovery.ExecutionRecoveryEscalationGate;
import delivery.agent.harness.recovery.ExecutionRecoveryWiring;
import delivery.agent.harness.AgentHarnessProperties;
import delivery.job.port.WorkspaceAiPolicy;
import delivery.store.ProjectStore;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Prove-only execute job: Excel → ProvePhase → durable IR + evidence under execute-runs.
 */
public class ExecuteJobRunner {
    private final JobProgressTracker progress = new JobProgressTracker();
    private final AgentHarnessProperties harnessProps;
    private WorkspaceAiPolicy workspaceAiPolicy;

    public ExecuteJobRunner() {
        this(null);
    }

    public ExecuteJobRunner(AgentHarnessProperties harnessProps) {
        this.harnessProps = harnessProps;
    }

    public ExecuteJobRunner withWorkspaceAiPolicy(WorkspaceAiPolicy policy) {
        this.workspaceAiPolicy = policy;
        return this;
    }

    public JobProgressTracker progress() {
        return progress;
    }

    public ExecuteJobResult run(ConversionJobRequest request, String jobId) throws Exception {
        return run(request, jobId, () -> false);
    }

    public ExecuteJobResult run(ConversionJobRequest request, String jobId, BooleanSupplier cancelCheck) throws Exception {
        boolean activatedProps = SuiteRunContext.current() == null && SuiteRunContext.activateFromSystemProperties();
        try {
        List<ManualTestCase> cases = KeelPathCaseFilter.requireForSurface(
                new ExcelTcReader(true).read(request.excel()),
                KeelPathCaseFilter.Surface.EXECUTE,
                "No runnable test cases in workbook — only MANUAL rows are skipped on Execute"
        );
        ProjectStore store = new ProjectStore(request.storeRoot(), request.baseUrl(), request.tenantId());
        Path projectRoot = store.projectRoot(request.projectId());
        cases = SuiteCaseFilter.requireFiltered(
                cases,
                projectRoot,
                "No test cases match the suite tag/suiteId/tcIds filter"
        );
        TcIdentity.requireValidCases(cases);

        String effectiveBase = MonitorTargetResolver.resolveForActiveContext(request.baseUrl(), null);
        request = ConversionJobRequests.withBaseUrl(request, effectiveBase);

        Path dest = projectRoot.resolve("execute-runs").resolve(jobId);
        Files.createDirectories(dest);

        String workFolder;
        Path work = JobWorkLayout.create(request);
        workFolder = work.getFileName().toString();

        int proveUnits = cases.size();
        int jobTotal = proveUnits + 2; // + design compare + evidence save
        progress.update(0, jobTotal, "Execute starting");

        ProvePhase prove = configureProvePhase(progress, dest, cancelCheck, request);
        prove.prove(request, cases, null, work);

        progress.update(proveUnits, jobTotal, "Design compare");
        try {
            new DesignComparePhase().run(request, work, cases);

            progress.update(proveUnits + 1, jobTotal, "Saving evidence");
            copyTree(work.resolve("ir"), dest.resolve("ir"));
            copyTree(work.resolve("evidence"), dest.resolve("evidence"));
            copyTree(work.resolve("retry"), dest.resolve("retry"));
            progress.update(jobTotal, jobTotal, "Execute completed");
        } catch (Exception e) {
            // Persist whatever IR/evidence ProvePhase already wrote so the UI is not empty on fail.
            try {
                copyTree(work.resolve("ir"), dest.resolve("ir"));
                copyTree(work.resolve("evidence"), dest.resolve("evidence"));
                copyTree(work.resolve("retry"), dest.resolve("retry"));
            } catch (Exception ignored) {
                // best-effort
            }
            throw e;
        }

        TcDraftStore draftStore = new TcDraftStore(work);
        List<TcDraft> drafts = draftStore.readAll();
        String message = PrecisionFallbackMessage.appendIfNeeded(drafts, "execute completed");
        RunHealth health = progress.health();
        message = message + " — " + health.summary();
        ExecuteJobResult result = new ExecuteJobResult(progress.passed(), progress.todo(), message,
                health.flaky(), health.healed(), health.infraError(), health.configError(),
                health.firstPassRate(), health.finalPassRate());
        SuiteAlertNotifier.notifyOutcome(result.passed(), result.todo(), result.flaky(), result.message());
        // Charge the project owner for the AI failure-investigation task when the workspace opted
        // in AND the harness was configured to run it (idempotent on jobId via tryDebitForAddon;
        // no-op when the Cursor call never actually fired).
        if (workspaceAiPolicy != null
                && harnessProps != null
                && harnessProps.isEnabled()
                && harnessProps.isFailureInvestigationEnabled()
                && workspaceAiPolicy.isFailureInvestigationEnabled(request.projectId())) {
            workspaceAiPolicy.chargeForAiTask(request.projectId(),
                    "execute-investigation:" + jobId);
        }
        return result;
        } finally {
            if (activatedProps) {
                SuiteRunContext.clear();
            }
        }
    }

    ProvePhase configureProvePhase(
            JobProgressTracker progress,
            Path mirrorRoot,
            BooleanSupplier cancelCheck,
            ConversionJobRequest request) {
        ProvePhase prove = new ProvePhase(progress)
                .withMirrorRoot(mirrorRoot)
                .withCancelCheck(cancelCheck)
                .withHealWorkbookApplier(healWorkbookApplier(request));
        // Gate the Cursor-driven failure investigation on the per-workspace policy so an enabled
        // global flag does not fire for a workspace that has not opted in (B1). Charge is applied
        // post-run in the ExecuteWorker (CreditService.tryDebitForAddon is idempotent on jobId).
        boolean failureInvestigationAllowed = workspaceAiPolicy == null
                || workspaceAiPolicy.isFailureInvestigationEnabled(request.projectId());
        // B5 closed: if the workspace flips the toggle off mid-run, also abort the investigation.
        BooleanSupplier investigationCancel = (workspaceAiPolicy != null && failureInvestigationAllowed)
                ? () -> cancelCheck.getAsBoolean()
                        || !workspaceAiPolicy.isFailureInvestigationEnabled(request.projectId())
                : cancelCheck;
        ExecutionRecoveryEscalationGate gate = failureInvestigationAllowed
                ? ExecutionRecoveryWiring.optionalGate(harnessProps, investigationCancel)
                : null;
        if (gate != null) {
            prove = prove.withExecutionRecoveryGate(gate);
        }
        return prove;
    }

    static ProvePhase.HealWorkbookApplier healWorkbookApplier(ConversionJobRequest request) {
        if (request == null || request.storeRoot() == null) {
            return null;
        }
        // B05: through SiteMemoryPort (in-process: GeneratedWorkbookService, unchanged behaviour).
        return delivery.job.port.EnginePorts.siteMemory().healWorkbookApplier(request.storeRoot());
    }

    private static void copyTree(Path src, Path dest) throws Exception {
        if (!Files.exists(src)) {
            return;
        }
        Files.walk(src).forEach(path -> {
            try {
                Path rel = dest.resolve(src.relativize(path).toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(rel);
                } else {
                    Files.createDirectories(rel.getParent());
                    Files.copy(path, rel, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }
}
