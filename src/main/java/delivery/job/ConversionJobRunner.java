package delivery.job;

import delivery.excel.ExcelTcReader;
import delivery.excel.KeelPathCaseFilter;
import delivery.excel.ManualTestCase;
import delivery.ir.TcDraft;
import delivery.ir.TcDraftStore;
import delivery.ir.TcIdentity;
import delivery.codegen.CodegenEnvHint;
import delivery.agent.harness.recovery.ExecutionRecoveryEscalationGate;
import delivery.agent.harness.recovery.ExecutionRecoveryWiring;
import delivery.agent.validation.automate.AutomateFrameworkAcceptanceGate;
import delivery.packager.FrameworkPackager;
import delivery.portal.AgentHarnessProperties;
import delivery.store.ProjectStore;
import org.json.JSONObject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * Orchestrates two-phase conversion:
 * <ol>
 *   <li>Phase 1 {@link ProvePhase} — browser prove + IR drafts (+ step retry)</li>
 *   <li>Phase 2 {@link EmitPhase} — page cluster + CodeWriter + ZIP + locator map</li>
 *   <li>Phase 3 {@link RevisePhase} — static Excel vs emit notes (no browser heal)</li>
 * </ol>
 */
public class ConversionJobRunner {
    private final JobProgressTracker progress = new JobProgressTracker();
    private final AgentHarnessProperties harnessProps;
    private final Duration investigationBudgetCap;
    private delivery.portal.service.WorkspaceAiPolicyService workspaceAiPolicy;

    public ConversionJobRunner() {
        this(null, null);
    }

    public ConversionJobRunner(AgentHarnessProperties harnessProps) {
        this(harnessProps, null);
    }

    public ConversionJobRunner(AgentHarnessProperties harnessProps, Duration investigationBudgetCap) {
        this.harnessProps = harnessProps;
        this.investigationBudgetCap = investigationBudgetCap;
    }

    public ConversionJobRunner withWorkspaceAiPolicy(delivery.portal.service.WorkspaceAiPolicyService policy) {
        this.workspaceAiPolicy = policy;
        return this;
    }

    /** Clone the harness properties with automate-framework-repair forced off (per-workspace veto). */
    private static AgentHarnessProperties copyWithRepairDisabled(AgentHarnessProperties src) {
        AgentHarnessProperties copy = new AgentHarnessProperties();
        copy.setEnabled(src.isEnabled());
        copy.setCursorEnabled(src.isCursorEnabled());
        copy.setMaxConcurrentRuns(src.getMaxConcurrentRuns());
        copy.setMaxRuntimeMinutes(src.getMaxRuntimeMinutes());
        copy.setMaxRetries(src.getMaxRetries());
        copy.setFrameworkValidationEnabled(src.isFrameworkValidationEnabled());
        copy.setFailureInvestigationEnabled(src.isFailureInvestigationEnabled());
        copy.setTcValidationEnabled(src.isTcValidationEnabled());
        copy.setTcValidationCursorEscalationEnabled(src.isTcValidationCursorEscalationEnabled());
        copy.setExecutionRecoveryCursorEscalationEnabled(src.isExecutionRecoveryCursorEscalationEnabled());
        copy.setAutomateFrameworkRepairEnabled(false);
        copy.setCursorCommand(src.getCursorCommand());
        return copy;
    }

    public JobProgressTracker progress() {
        return progress;
    }

    public ConversionJobResult run(ConversionJobRequest request) throws Exception {
        return run(request, () -> false);
    }

    public ConversionJobResult run(ConversionJobRequest request, BooleanSupplier cancelCheck) throws Exception {
        return run(request, cancelCheck, PublicationGuard.cancellable(cancelCheck));
    }

    public ConversionJobResult run(ConversionJobRequest request, BooleanSupplier cancelCheck,
                                   PublicationGuard publicationGuard) throws Exception {
        String mode = request.mode() == null ? "NEW" : request.mode().trim().toUpperCase();
        ProjectStore store = new ProjectStore(request.storeRoot(), request.baseUrl(), request.tenantId());
        if ("UPDATE".equals(mode) && !store.hasFramework(request.projectId())) {
            throw new IllegalStateException("UPDATE_WITHOUT_FRAMEWORK");
        }

        boolean activatedProps = SuiteRunContext.current() == null && SuiteRunContext.activateFromSystemProperties();
        try {
        List<ManualTestCase> allCases = KeelPathCaseFilter.requireForSurface(
                new ExcelTcReader(true).read(request.excel()),
                KeelPathCaseFilter.Surface.AUTOMATE,
                "No runnable test cases in workbook - MANUAL rows are skipped on Automate; use AUTOMATE/EXECUTE/VISION_ONLY or blank KeelPath"
        );
        Path suiteProjectRoot = new ProjectStore(request.storeRoot(), request.baseUrl(), request.tenantId())
                .projectRoot(request.projectId());
        allCases = SuiteCaseFilter.requireFiltered(
                allCases,
                suiteProjectRoot,
                "No test cases match the suite tag/suiteId/tcIds filter"
        );
        TcIdentity.requireValidCases(allCases);
        request = ConversionJobRequests.withBaseUrl(
                request, MonitorTargetResolver.resolveForActiveContext(request.baseUrl(), null));

        Map<String, String> storedHashes = new HashMap<>();
        Path hashFile = store.projectRoot(request.projectId()).resolve("tc-hashes.json");
        if (Files.exists(hashFile)) {
            JSONObject json = new JSONObject(Files.readString(hashFile));
            for (String key : json.keySet()) {
                storedHashes.put(key, json.getString(key));
            }
        }

        Path projectRoot = store.projectRoot(request.projectId());
        Map<String, TcDraft> storedDrafts = ReuseEligibility.loadStoredDrafts(projectRoot);
        ReuseEligibility.Context storedContext = ReuseEligibility.read(projectRoot);
        Set<String> authorIds = "UPDATE".equals(mode)
                ? ReuseEligibility.authorIds(
                        allCases, storedHashes, storedDrafts, storedContext,
                        ReuseEligibility.current(request), projectRoot)
                : null;

        String workFolder;
        Path work = JobWorkLayout.create(request);
        workFolder = work.getFileName().toString();
        CodegenEnvHint.write(work, CodegenEnvHint.read(projectRoot));
        Path projectDir = work.resolve("project");
        FrameworkPackager packager = new FrameworkPackager();
        packager.copyTemplate(request.templateRoot(), projectDir);
        if ("UPDATE".equals(mode) && store.hasFramework(request.projectId())) {
            packager.overlayCustomerConfig(
                    store.projectRoot(request.projectId()).resolve("framework"), projectDir);
        }

        int proveUnits = allCases.size();
        int jobTotal = proveUnits + EmitPhase.PROGRESS_UNITS;
        progress.update(0, jobTotal, "Phase1 starting");
        ProvePhase prove = configureProvePhase(progress, cancelCheck, request);
        prove.prove(request, allCases, authorIds, work);

        progress.update(proveUnits, jobTotal, "Phase2 starting");
        AgentHarnessProperties acceptanceProps =
                harnessProps == null ? new AgentHarnessProperties() : harnessProps;
        // Gate Cursor-driven framework repair on the per-workspace toggle so an enabled global flag
        // does not fire repair on a workspace that has not opted in (B1). Contract validation
        // (deterministic) is unaffected and always runs when acceptance is on.
        boolean repairAllowedForWorkspace = workspaceAiPolicy == null
                || workspaceAiPolicy.isFrameworkRepairEnabled(request.projectId());
        AgentHarnessProperties effectiveProps = repairAllowedForWorkspace
                ? acceptanceProps
                : copyWithRepairDisabled(acceptanceProps);
        AutomateFrameworkAcceptanceGate acceptanceGate = AutomateFrameworkAcceptanceGate.forConversionJob(
                effectiveProps, cancelCheck, investigationBudgetCap);
        EmitPhase emit = new EmitPhase(progress).withAcceptanceGate(acceptanceGate);
        ConversionJobResult conversionResult = emit.emit(request, allCases, work, projectDir, workFolder, mode, cancelCheck, publicationGuard);
        SuiteAlertNotifier.notifyOutcome(conversionResult.passed(), conversionResult.todo(), 0, conversionResult.message());
        // Charge the project owner once per successful repair-eligible run (conversion passed → repair
        // may or may not have fired; idempotent on taskId so re-runs do not double-charge).
        if (repairAllowedForWorkspace && workspaceAiPolicy != null && conversionResult.passed() > 0) {
            workspaceAiPolicy.chargeForAiTask(request.projectId(),
                    "automate-repair:" + request.jobId());
        }
        return conversionResult;
        } finally {
            if (activatedProps) {
                SuiteRunContext.clear();
            }
        }
    }

    /**
     * Same framework builder as {@link #run}, with the browser prove phase left out.
     * Locators come from the recording. Other saved drafts are kept so an update does not wipe them.
     */
    public ConversionJobResult runRecorded(ConversionJobRequest request, List<RecordedCapture> captures,
                                           BooleanSupplier cancelCheck, PublicationGuard publicationGuard)
            throws Exception {
        String mode = request.mode() == null ? "NEW" : request.mode().trim().toUpperCase();
        ProjectStore store = new ProjectStore(request.storeRoot(), request.baseUrl(), request.tenantId());
        List<ManualTestCase> selected = new ExcelTcReader(true).read(request.excel());
        Map<String, TcDraft> stored = ReuseEligibility.loadStoredDrafts(store.projectRoot(request.projectId()));
        List<TcDraft> drafts = new ArrayList<>();
        Set<String> replaced = new HashSet<>();
        for (ManualTestCase tc : selected) {
            TcDraft built = RecordedDrafts.draft(tc, captures);
            if (built.provenSteps().isEmpty() && built.loginSteps().isEmpty()) {
                throw new IllegalStateException("NO_RECORDED_LOCATORS");
            }
            drafts.add(built);
            replaced.add(tc.tcId());
        }
        for (TcDraft old : stored.values()) {
            if (old != null && old.tcId() != null && !replaced.contains(old.tcId())) {
                drafts.add(old);
            }
        }
        List<ManualTestCase> allCases = new ArrayList<>(selected);
        for (TcDraft draft : drafts) {
            boolean known = false;
            for (ManualTestCase tc : selected) {
                if (tc.tcId() != null && tc.tcId().equals(draft.tcId())) {
                    known = true;
                    break;
                }
            }
            if (!known) {
                allCases.add(new ManualTestCase(
                        draft.tcId(), draft.title(), "", draft.stepsText(), draft.expectedResult(),
                        "P2", "recorded"));
            }
        }
        Path work = JobWorkLayout.create(request);
        String workFolder = work.getFileName().toString();
        Path projectDir = work.resolve("project");
        FrameworkPackager packager = new FrameworkPackager();
        packager.copyTemplate(request.templateRoot(), projectDir);
        if ("UPDATE".equals(mode) && store.hasFramework(request.projectId())) {
            packager.overlayCustomerConfig(
                    store.projectRoot(request.projectId()).resolve("framework"), projectDir);
        }
        TcDraftStore draftStore = new TcDraftStore(work);
        for (TcDraft draft : drafts) {
            draftStore.write(draft);
        }
        progress.update(0, EmitPhase.PROGRESS_UNITS, "Building the framework from the recording");
        return new EmitPhase(progress).emit(
                request, allCases, work, projectDir, workFolder, mode, cancelCheck, publicationGuard);
    }

    ProvePhase configureProvePhase(
            JobProgressTracker progress,
            BooleanSupplier cancelCheck,
            ConversionJobRequest request) {
        ProvePhase prove = new ProvePhase(progress)
                .withCancelCheck(cancelCheck)
                .withHealWorkbookApplier(ExecuteJobRunner.healWorkbookApplier(request));
        ExecutionRecoveryEscalationGate gate = ExecutionRecoveryWiring.optionalGate(
                harnessProps, cancelCheck, investigationBudgetCap);
        if (gate != null) {
            prove = prove.withExecutionRecoveryGate(gate);
        }
        return prove;
    }

    /** Strip login instructions so body authoring does not re-emit login clicks. */
    static ManualTestCase bodyOnlyCase(ManualTestCase tc) {
        String steps = tc.steps() == null ? "" : tc.steps();
        String cleaned = steps
                .replaceAll("(?im)^\\s*\\d+[.)]?\\s*(enter|type|fill).*?(username|user\\s*name|password|e-?mail).*$", "")
                .replaceAll("(?im)^\\s*\\d+[.)]?\\s*click\\s+(the\\s+)?(login|sign\\s*in).*button.*$", "")
                .replaceAll("(?im)^\\s*\\d+[.)]?\\s*(log\\s*in|sign\\s*in)\\b.*$", "")
                .replaceAll("(?im)^\\s*\\d+[.)]?\\s*(open|go to|navigate to|visit).*(login\\s*page|sign\\s*in\\s*page).*$", "")
                .replaceAll("(?im)^\\s*\\d+[.)]?\\s*(open|go to|navigate to|visit).*(base\\s*url|application\\s*url|website|home\\s*page).*$", "")
                .trim();
        return new ManualTestCase(
                tc.tcId(),
                tc.title(),
                tc.preconditions(),
                """
                        Post-login steps only (login runs in @BeforeMethod):
                        %s
                        Do NOT click login, type username, or type password.
                        """.formatted(cleaned.isBlank() ? steps : cleaned),
                tc.expectedResult(),
                tc.priority(),
                tc.tags(),
                tc.visualAssertion(),
                tc.testData(),
                tc.keelPath(),
                tc.callBefore()
        );
    }
}
