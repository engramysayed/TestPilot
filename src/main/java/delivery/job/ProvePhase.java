package delivery.job;

import delivery.routing.ObservationBudget;
import delivery.routing.ModelRouter;

import delivery.authoring.CompoundAssert;
import delivery.authoring.AuthoringEngine;
import delivery.authoring.AuthoringService;
import delivery.authoring.DummyValueInventor;
import delivery.authoring.LocalLlmClient;
import delivery.authoring.LiveHeadingQuote;
import delivery.authoring.LocatorValidator;
import delivery.authoring.LoginStepDetector;
import delivery.authoring.PrecisionBatchBindOutcome;
import delivery.authoring.PrecisionBindOutcome;
import delivery.authoring.PrecisionBindService;
import delivery.authoring.PrecisionCallBudget;
import delivery.authoring.PrecisionJobConfig;
import delivery.authoring.RequiredControlFiller;
import delivery.authoring.DomCandidate;
import delivery.authoring.BindQualityMetrics;
import delivery.authoring.PageLocatorCatalog;
import delivery.authoring.NestedPreferredHook;
import delivery.authoring.StepIntentBinder;
import delivery.codegen.PageClusterer;
import delivery.codegen.PageNameNormalizer;
import delivery.codegen.ProvenStep;
import delivery.excel.CallBefore;
import delivery.excel.ManualTestCase;
import delivery.net.TargetBlockedException;
import delivery.net.TargetNetworkPolicy;
import delivery.net.WorkerNetworkGuard;
import delivery.store.DomainLocatorMemory;
import delivery.store.PreferredHooksStore;
import delivery.store.ProjectStore;
import delivery.heal.CursorHealClient;
import delivery.heal.FailedLocator;
import delivery.heal.HealCascade;
import delivery.heal.HealResult;
import delivery.heal.CandidateLivenessProbe;
import delivery.vision.SeleniumGroundingBrowser;
import delivery.vision.VisionAssertionEvidence;
import delivery.vision.VisionAssertionGate;
import delivery.vision.VisionAssertionResult;
import delivery.vision.VisionAssertionStatus;
import delivery.vision.VisionAttemptLog;
import delivery.vision.VisionGroundingConfig;
import delivery.vision.VisionGroundingProvider;
import delivery.vision.VisionProveHook;
import delivery.vision.VisionTriggers;
import delivery.ir.TcDraft;
import delivery.ir.TcDraftStatus;
import delivery.ir.TcDraftStore;
import drivers.WebDriverFactory;
import parsingLayer.HtmlSlimmer;
import org.json.JSONArray;
import org.json.JSONObject;
import utils.LogsManager;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * Phase 1: parse/execute/validate each TC against the live browser, persist IR drafts.
 * Retries a failed bind/execute up to {@link #MAX_RETRIES} times with fresh DOM.
 */
public class ProvePhase {
    /** Extra attempts after the first failure (plan: retry 2 times max). */
    public static final int MAX_RETRIES = 2;
    /** Extra attempts allowed for INFRA-category failures; does not consume {@link #MAX_RETRIES}. */
    public static final int MAX_INFRA_RETRIES = 2;

    /** Asserts fail honestly. Heal is for missing locators on actions, not expected-text misses. */
    public static boolean mayHeal(StepIntentBinder.IntentLine intent, String reason) {
        return intent == null || intent.kind() != StepIntentBinder.IntentKind.ASSERT_VISIBLE;
    }
    /** Infra backoff schedule + sleeper; package-visible for tests (no real 2 s / 5 s waits). */
    long[] infraBackoffMs = RetryBudget.DEFAULT_INFRA_BACKOFF_MS;
    RetryBudget.Sleeper infraSleeper = RetryBudget.Sleeper.REAL;

    /** Per-TC accumulator of retry-budget usage across intents (reset before each proveOne). */
    private int tcTestRetries;
    private int tcTestFailures;
    private int tcInfraRetries;

    private final JobProgressTracker progress;
    private DomainLocatorMemory locatorMemory = new DomainLocatorMemory();
    private final PageLocatorCatalog pageCatalog = new PageLocatorCatalog();
    private final BindQualityMetrics bindQuality = new BindQualityMetrics();
    private Path locatorMemoryFile;
    /** When set (Execute jobs), mirror each draft to durable execute-runs storage after write. */
    private Path mirrorRoot;
    private BooleanSupplier cancelCheck = () -> false;
    private HealWorkbookApplier healWorkbookApplier;
    private PrecisionBindService precisionBindService;
    private boolean precisionReuseLocatorMemory;
    private boolean precisionLocatorMemoryDisabledLogged;
    private PrecisionJobTracker precisionTracker = new PrecisionJobTracker(AuthoringEngine.KEEL);

    private static final class PrecisionJobTracker {
        final AuthoringEngine engine;
        private boolean tcFallback;
        private String tcFallbackReason = "";
        private int tcCallsAtStart;

        PrecisionJobTracker(AuthoringEngine engine) {
            this.engine = engine == null ? AuthoringEngine.KEEL : engine;
        }

        void beginTc(int callsUsedNow) {
            tcCallsAtStart = callsUsedNow;
            tcFallback = false;
            tcFallbackReason = "";
        }

        int tcCallsUsed(int callsUsedNow) {
            return Math.max(0, callsUsedNow - tcCallsAtStart);
        }

        void noteTcFallback(String reason) {
            tcFallback = true;
            if (reason != null && !reason.isBlank()) {
                tcFallbackReason = reason;
            }
        }

        boolean tcFallback() {
            return tcFallback;
        }

        String tcFallbackReason() {
            return tcFallbackReason;
        }
    }

    private record PrecisionBindResult(
            List<ProvenStep> steps,
            String tier,
            boolean fellBack,
            String fallbackReason
    ) {
    }

    @FunctionalInterface
    public interface HealWorkbookApplier {
        void apply(
                String projectId,
                String tcId,
                List<ProvenStep> recoverySteps,
                List<String> automationNotes,
                String baseUrl
        ) throws Exception;
    }

    public ProvePhase(JobProgressTracker progress) {
        this.progress = progress == null ? new JobProgressTracker() : progress;
    }

    public ProvePhase withMirrorRoot(Path mirrorRoot) {
        this.mirrorRoot = mirrorRoot;
        return this;
    }

    public ProvePhase withCancelCheck(BooleanSupplier cancelCheck) {
        this.cancelCheck = cancelCheck == null ? () -> false : cancelCheck;
        return this;
    }

    public ProvePhase withHealWorkbookApplier(HealWorkbookApplier healWorkbookApplier) {
        this.healWorkbookApplier = healWorkbookApplier;
        return this;
    }

    public JobProgressTracker progress() {
        return progress;
    }

    public List<TcDraft> prove(
            ConversionJobRequest request,
            List<ManualTestCase> allCases,
            Set<String> tcIdsToAuthor,
            Path workDir
    ) throws Exception {
        TcDraftStore drafts = new TcDraftStore(workDir);
        RetryRecordStore retryRecords = new RetryRecordStore(workDir);
        List<RetryVerdict> verdicts = new ArrayList<>();
        int reusedFlaky = 0;
        Path evidence = workDir.resolve("evidence");
        LocalLlmClient llm = new LocalLlmClient(request.localLlmBaseUrl(), request.localLlmModel());
        AuthoringService authoring = new AuthoringService(llm, new LocatorValidator());
        delivery.net.WorkerPac.installForJob(request == null ? "" : request.baseUrl());
        WebDriverFactory driverFactory = new WebDriverFactory();
        HealCascade healCascade = VisionGroundingConfig.enabled()
                ? new HealCascade(
                        authoring,
                        new CursorHealClient(),
                        VisionGroundingConfig.createProvider(),
                        () -> new SeleniumGroundingBrowser(driverFactory.get()))
                : new HealCascade(authoring, new CursorHealClient());
        authoring.setBindQuality(bindQuality);
        healCascade.setBindQuality(bindQuality);
        TcExecutionService execution = new TcExecutionService(driverFactory, request);
        AuthoringEngine jobEngine = request == null || request.authoringEngine() == null
                ? AuthoringEngine.KEEL : request.authoringEngine();
        precisionTracker = new PrecisionJobTracker(jobEngine);
        precisionBindService = null;
        precisionReuseLocatorMemory = false;
        precisionLocatorMemoryDisabledLogged = false;
        PrecisionJobConfig precisionConfig = request == null || request.precisionConfig() == null
                ? PrecisionJobConfig.DEFAULTS : request.precisionConfig();
        precisionReuseLocatorMemory = precisionConfig.reuseLocatorMemory();
        if (jobEngine == AuthoringEngine.PRECISION) {
            PrecisionCallBudget budget = PrecisionCallBudget.fromProperties(
                    precisionConfig.enabled(), precisionConfig.maxCallsPerJob());
            precisionBindService = new PrecisionBindService(
                    authoring, new CursorHealClient(), budget, precisionConfig.enabled());
            healCascade.attachPrecisionBudget(budget);
        }
        JobLoginService jobLogin = new JobLoginService();
        boolean jobHasCredentials = request.username() != null && !request.username().isBlank()
                && request.password() != null;
        // null = author every TC (NEW); UPDATE author set = re-prove; others may reuse eligible proof
        List<TcDraft> out = new ArrayList<>();
        Map<String, TcDraft> previousDrafts = Map.of();
        ReuseEligibility.Context storedContext = null;
        Path previousRoot = null;
        if (request != null && request.storeRoot() != null && request.projectId() != null
                && request.mode() != null && "UPDATE".equalsIgnoreCase(request.mode().trim())) {
            ProjectStore previousStore = new ProjectStore(
                    request.storeRoot(), request.baseUrl(), request.tenantId());
            previousRoot = previousStore.projectRoot(request.projectId());
            previousDrafts = ReuseEligibility.loadStoredDrafts(previousRoot);
            storedContext = ReuseEligibility.read(previousRoot);
        }
        locatorMemory = new DomainLocatorMemory();
        if (request != null && request.storeRoot() != null
                && request.baseUrl() != null && !request.baseUrl().isBlank()) {
            if (request.tenantId() != null) {
                locatorMemoryFile = locatorMemory.openShared(
                        request.storeRoot(), request.tenantId(), request.baseUrl());
            } else {
                locatorMemoryFile = locatorMemory.openShared(request.storeRoot(), request.baseUrl());
            }
        } else {
            locatorMemoryFile = memoryFile(request);
            locatorMemory.load(locatorMemoryFile);
        }
        List<String> preferredHooks = request == null
                ? List.of()
                : PreferredHooksStore.load(request.storeRoot(), request.tenantId(), request.baseUrl());

        try (delivery.vision.VisionMissJournal.Scope journal = delivery.vision.VisionMissJournal.activate(evidence, request.password()); PreferredHooksStore.Scope ignoredHooks = PreferredHooksStore.activate(preferredHooks)) {
            int index = 0;
            Set<String> failedCallBeforeIds = new LinkedHashSet<>();
            List<Boolean> freshSession = CallBeforeExpander.freshSessionAt(allCases);
            Map<String, Integer> occurrences = new HashMap<>();
            for (ManualTestCase tc : allCases) {
                JobCancelSupport.checkCancelled(cancelCheck);
                index++;
                int jobTotal = progress.effectiveTotal(allCases.size());
                progress.update(index, jobTotal, "Phase1 prove " + tc.tcId());
                if (tcIdsToAuthor != null && !tcIdsToAuthor.contains(tc.tcId())) {
                    precisionTracker.beginTc(
                            precisionBindService == null ? 0 : precisionBindService.callsUsed());
                    TcDraft prior = previousDrafts.get(tc.tcId());
                    if (ReuseEligibility.environmentMatches(storedContext, ReuseEligibility.current(request))
                            && ReuseEligibility.canReuse(prior)) {
                        TcDraft reused = ReuseEligibility.copyForReuse(prior, tc, previousRoot);
                        ReuseEligibility.preserveEvidence(prior, previousRoot, workDir);
                        drafts.write(reused);
                        mirrorDraft(workDir, reused.tcId());
                        out.add(reused);
                        progress.recordOutcome(TcDraftStatus.REUSED);
                        verdicts.add(RetryVerdict.REUSED);
                        progress.recordVerdict(RetryVerdict.REUSED);
                        try {
                            if (retryRecords.writeReused(reused.tcId(),
                                    RetryRecordStore.priorVerdict(previousRoot, tc.tcId()))) {
                                reusedFlaky++;
                                progress.recordReusedFlaky();
                            }
                        } catch (Exception e) {
                            LogsManager.info("Retry reuse record failed for " + tc.tcId() + ": " + e.getMessage());
                        }
                        progress.update(index, jobTotal,
                                "Phase1 reused " + tc.tcId()
                                        + " — passed " + progress.passed()
                                        + ", blocked " + progress.todo());
                        continue;
                    }
                }
                if (callBeforeBlocked(tc, failedCallBeforeIds)) {
                    String reason = "Call-before did not pass ("
                            + String.join(", ", CallBefore.parse(tc.callBefore()))
                            + ") — skipped so this case does not run on a broken session";
                    TcDraft skipped = todoDraft(tc, List.of(), false, List.of(),
                            reason, "", 0, "");
                    drafts.write(skipped);
                    mirrorDraft(workDir, skipped.tcId());
                    out.add(skipped);
                    failedCallBeforeIds.add(tc.tcId());
                    progress.recordOutcome(skipped.status());
                    verdicts.add(RetryVerdict.FAILED);
                    progress.recordVerdict(RetryVerdict.FAILED);
                    progress.update(index, jobTotal,
                            "Phase1 skipped " + tc.tcId() + " — call-before failed");
                    continue;
                }

                // Fresh browser at each leaf-chain start; Call-before → leaf keep the same session.
                int occurrence = occurrences.merge(tc.tcId(), 1, Integer::sum);
                String evidenceFolder = OccurrenceIdentity.folder(tc.tcId(), occurrence);
                tcTestRetries = 0;
                tcTestFailures = 0;
                tcInfraRetries = 0;
                TcDraft draft = proveOne(tc, request, authoring, healCascade, execution, jobLogin,
                        driverFactory, evidence, jobHasCredentials, index, jobTotal, freshSession,
                        evidenceFolder);
                draft = stampPrecision(draft);
                draft = scrubDraftSecrets(draft, request);
                drafts.write(draft);
                mirrorDraft(workDir, draft.tcId());
                out.add(draft);
                if (draft.status() == TcDraftStatus.PASSED || draft.status() == TcDraftStatus.REUSED) {
                    failedCallBeforeIds.remove(tc.tcId());
                } else {
                    failedCallBeforeIds.add(tc.tcId());
                }
                saveLocatorMemory();
                RetryVerdict verdict = RetryVerdict.of(draft.status(), tcTestFailures, draft.healTier(),
                        draft.failureReason());
                verdicts.add(verdict);
                try {
                    retryRecords.write(draft.tcId(), verdict, tcTestRetries, tcTestFailures, tcInfraRetries,
                            draft.failureReason());
                } catch (Exception e) {
                    LogsManager.info("Retry record write failed for " + draft.tcId() + ": " + e.getMessage());
                }
                progress.recordOutcome(draft.status());
                progress.recordVerdict(verdict);
                progress.update(index, jobTotal,
                        "Phase1 " + draft.status() + " " + tc.tcId()
                                + " — passed " + progress.passed()
                                + ", blocked " + progress.todo());
            }
        } finally {
            try {
                driverFactory.quit();
            } catch (Exception ignored) {
            }
            try {
                retryRecords.writeRun(RunHealth.of(verdicts, reusedFlaky));
            } catch (Exception e) {
                LogsManager.info("Retry run metrics write failed: " + e.getMessage());
            }
        }
        return out;
    }

    private void mirrorDraft(Path workDir, String tcId) {
        if (mirrorRoot == null) {
            return;
        }
        try {
            ExecuteRunArtifacts.syncTc(workDir, mirrorRoot, tcId);
        } catch (Exception e) {
            LogsManager.info("Execute mirror failed for " + tcId + ": " + e.getMessage());
        }
    }

    private TcDraft proveOne(
            ManualTestCase tc,
            ConversionJobRequest request,
            AuthoringService authoring,
            HealCascade healCascade,
            TcExecutionService execution,
            JobLoginService jobLogin,
            WebDriverFactory driverFactory,
            Path evidence,
            boolean jobHasCredentials,
            int tcIndex,
            int tcTotal,
            List<Boolean> freshSession,
            String evidenceFolder
    ) {
        boolean wipeSession = wipeAt(tcIndex, freshSession);
        try {
            if (wipeSession) {
                if (tcIndex > 1) {
                    driverFactory.restart();
                }
                openFreshPage(driverFactory, request.baseUrl());
            } else {
                ensureBrowserAlive(driverFactory);
            }
        } catch (Exception e) {
            if (DeadBrowserSession.isDead(e)) {
                try {
                    driverFactory.restart();
                    openFreshPage(driverFactory, request.baseUrl());
                } catch (Exception retry) {
                    return todoDraft(tc, List.of(), false, List.of(),
                            "Browser session died: " + retry.getMessage(), "", 0, "");
                }
            } else {
                return todoDraft(tc, List.of(), false, List.of(),
                        "Browser navigate failed: " + e.getMessage(), "", 0, "");
            }
        }

        execution.beginTc(evidenceFolder);
        pageCatalog.clear();
        String evidenceDirForFlush = evidence == null ? "" : evidence.toString();
        try {
        healCascade.setExcelOpenPath(StepIntentBinder.firstOpenPath(
                tc.preconditions(), tc.steps()));

        boolean continueSession = !wipeSession;
        // Keep Call-before / prior-TC login: do not bounce later cases back to /login.
        if (!continueSession || !isAuthOpenPath(StepIntentBinder.firstOpenPath(
                tc.preconditions(), tc.steps()))) {
            LoginFormNavigator.navigateExcelOpenPathIfPresent(driverFactory, request.baseUrl(), tc);
        } else {
            LogsManager.info("SESSION_CONTINUE: skip auth open-path for " + tc.tcId());
        }
        boolean formVisible = LoginFormNavigator.pageHasLoginForm(driverFactory);
        boolean needsLogin;
        if (continueSession) {
            // Only re-login if the live page is already a login form (session lost).
            needsLogin = formVisible && LoginStepDetector.needsAuthenticatedSession(
                    tc, jobHasCredentials, true);
            if (needsLogin) {
                LogsManager.info("SESSION_CONTINUE: login form visible — re-auth for " + tc.tcId());
            }
        } else {
            needsLogin = LoginStepDetector.needsAuthenticatedSession(
                    tc, jobHasCredentials, formVisible);
        }

        String loginFormUrl = "";
        List<ProvenStep> loginSteps = List.of();
        if (needsLogin) {
            try {
                if (!LoginFormNavigator.ensureLoginFormVisible(driverFactory, request.baseUrl(), tc)) {
                    String ev = execution.captureFailureEvidence(tc.tcId(), evidence);
                    return todoDraft(tc, List.of(), true, List.of(),
                            "Login required but no login form found (Excel path or on-page login/sign-in control)",
                            ev, 0, currentUrl(driverFactory));
                }
                String loginHtml = HtmlSlimmer.slim(PageSnapshot.html(driverFactory.get()), proveHtmlBudget());
                loginSteps = authoring.authorLoginPrelude(tc, loginHtml);
                if (loginSteps.stream().anyMatch(s -> !s.validated()) || loginSteps.isEmpty()) {
                    String reason = loginSteps.isEmpty()
                            ? "Login prelude could not be bound to DOM candidates"
                            : "Login locator validation failed: " + loginSteps.stream()
                            .filter(s -> !s.validated())
                            .findFirst()
                            .map(ProvenStep::rationale)
                            .orElse("unknown");
                    String ev = execution.captureFailureEvidence(tc.tcId(), evidence);
                    loginFormUrl = currentUrl(driverFactory);
                    return todoDraft(tc, List.of(), true, loginSteps, reason, ev, 0, loginFormUrl)
                            .withLoginFormUrl(loginFormUrl);
                }
                // Keep placeholders in IR/codegen; resolve secrets only for live Selenium execute
                loginFormUrl = currentUrl(driverFactory);
                List<ProvenStep> loginForExec = LoginSecretResolver.resolveForLive(loginSteps, request);
                TcOutcome loginOutcome = execution.execute(tc.tcId(), loginForExec, evidence);
                if (loginOutcome.status() != TcStatus.PASSED) {
                    try {
                        jobLogin.loginIfNeeded(driverFactory, request);
                    } catch (IllegalStateException e) {
                        String ev = loginOutcome.evidenceDir() == null
                                ? execution.captureFailureEvidence(tc.tcId(), evidence)
                                : loginOutcome.evidenceDir().toString();
                        return todoDraft(tc, List.of(), true,
                                scrubSecrets(stampLoginNames(loginSteps, loginFormUrl), request),
                                "LOGIN_FAILED: " + loginOutcome.failureReason() + " | " + e.getMessage(),
                                ev, 0, currentUrl(driverFactory))
                                .withLoginFormUrl(loginFormUrl);
                    }
                    loginSteps = scrubSecrets(stampLoginNames(loginSteps, loginFormUrl), request);
                } else if (loginOutcome.provenSteps() != null && !loginOutcome.provenSteps().isEmpty()) {
                    loginSteps = scrubSecrets(
                            stampLoginNames(loginOutcome.provenSteps(), loginFormUrl), request);
                } else {
                    loginSteps = scrubSecrets(stampLoginNames(loginSteps, loginFormUrl), request);
                }
            } catch (IllegalStateException e) {
                String ev = execution.captureFailureEvidence(tc.tcId(), evidence);
                return todoDraft(tc, List.of(), true, loginSteps, e.getMessage(), ev, 0, currentUrl(driverFactory))
                        .withLoginFormUrl(loginFormUrl);
            } catch (Exception e) {
                String ev = execution.captureFailureEvidence(tc.tcId(), evidence);
                return todoDraft(tc, List.of(), true, loginSteps,
                        "Login prelude error: " + e.getMessage(), ev, 0, currentUrl(driverFactory))
                        .withLoginFormUrl(loginFormUrl);
            }
        }

        ManualTestCase bodyTc = needsLogin ? ConversionJobRunner.bodyOnlyCase(tc) : tc;
        List<StepIntentBinder.IntentLine> intents = StepIntentBinder.bodyIntents(bodyTc, needsLogin);

        if (intents.isEmpty()) {
            return proveBatchFallback(tc, bodyTc, authoring, healCascade, execution, evidence,
                    needsLogin, loginSteps, loginFormUrl, driverFactory, tcIndex, tcTotal);
        }

        precisionTracker.beginTc(
                precisionBindService == null ? 0 : precisionBindService.callsUsed());
        List<ProvenStep> provenAll = new ArrayList<>();
        List<FailedLocator> failedThisTc = new ArrayList<>();
        int intentIndex = 0;
        String maxHealTier = "none";
        String healSkipAccum = "";
        while (intentIndex < intents.size()) {
            JobCancelSupport.checkCancelled(cancelCheck);
            StepIntentBinder.IntentLine intent = intents.get(intentIndex);
            progress.update(tcIndex, tcTotal,
                    "Phase1 " + tc.tcId() + " step " + (intentIndex + 1) + "/" + intents.size()
                            + ": " + trim(intent.text(), 60));

            StepAttempt attempt = null;
            int batchConsumed = 0;
            PrecisionBatchStepAttempt batchAttempt = null;
            String bindingPageUrl = currentUrl(driverFactory);
            if (precisionBindService != null) {
                int batchEnd = precisionBatchEnd(intents, intentIndex);
                if (batchEnd - intentIndex >= 2) {
                    List<StepIntentBinder.IntentLine> batchSlice =
                            intents.subList(intentIndex, batchEnd);
                    batchAttempt = attemptPrecisionBatch(
                            tc, request, batchSlice, intentIndex, authoring, execution,
                            evidence, driverFactory, provenAll, failedThisTc);
                    if (batchAttempt != null && batchAttempt.usedBatch()) {
                        attempt = batchAttempt.attempt();
                        batchConsumed = batchAttempt.intentsConsumed();
                    }
                }
            }
            if (attempt == null) {
                attempt = attemptIntentWithRetry(
                        tc, request, intent, intentIndex, authoring, healCascade, execution, evidence,
                        driverFactory, provenAll, failedThisTc);
                batchConsumed = 1;
            }
            maxHealTier = mergeHealTier(maxHealTier, attempt.healTier());
            if (attempt.healSkipReason() != null && !attempt.healSkipReason().isBlank()) {
                healSkipAccum = attempt.healSkipReason();
            }
            if (!attempt.ok()) {
                List<ProvenStep> merged = new ArrayList<>(provenAll);
                merged.addAll(attempt.partialSteps());
                String url = currentUrl(driverFactory);
                merged = stampPageNames(merged, url);
                loginSteps = stampLoginNames(loginSteps);
                TcDraftStatus status = merged.isEmpty() ? TcDraftStatus.TODO : TcDraftStatus.PARTIAL;
                return new TcDraft(
                        tc.tcId(), tc.title(), tc.steps(), tc.expectedResult(),
                        status, merged, loginSteps, needsLogin,
                        intentIndex, intent.text(), attempt.reason(),
                        attempt.evidenceDir(), attempt.retriesUsed(), url)
                        .withHeal(maxHealTier, attempt.healSkipReason())
                        .withLoginFormUrl(loginFormUrl);
            }
            List<List<ProvenStep>> stepsByIntent = batchAttempt != null && batchAttempt.usedBatch()
                    ? batchAttempt.stepsPerIntent()
                    : List.of(attempt.steps());
            for (int bi = 0; bi < batchConsumed && intentIndex + bi < intents.size(); bi++) {
                StepIntentBinder.IntentLine boundIntent = intents.get(intentIndex + bi);
                List<ProvenStep> slice = bi < stepsByIntent.size()
                        ? stepsByIntent.get(bi) : List.of();
                rememberSuccessfulLocator(request, driverFactory, tc, boundIntent, slice, bindingPageUrl);
            }
            provenAll.addAll(attempt.steps());
            intentIndex += batchConsumed;
        }

        String url = currentUrl(driverFactory);
        provenAll = stampPageNames(provenAll, url);
        provenAll = ExpectedCoverageEnricher.enrich(tc, provenAll);
        loginSteps = stampLoginNames(loginSteps);
        String reject = SemanticPassGate.rejectReason(tc, provenAll, url);
        if (reject != null) {
            return new TcDraft(
                    tc.tcId(), tc.title(), tc.steps(), tc.expectedResult(),
                    TcDraftStatus.PARTIAL, provenAll, loginSteps, needsLogin,
                    provenAll.size(), "semantic-gate", reject, "", 0, url)
                    .withHeal(maxHealTier, healSkipAccum)
                    .withLoginFormUrl(loginFormUrl);
        }
        return applyVisualAssertion(tc, new TcDraft(
                tc.tcId(), tc.title(), tc.steps(), tc.expectedResult(),
                TcDraftStatus.PASSED, provenAll, loginSteps, needsLogin,
                -1, "", "", "", 0, url)
                .withHeal(maxHealTier, healSkipAccum)
                .withLoginFormUrl(loginFormUrl), execution, evidence);
        } finally {
            flushBindQuality(evidenceDirForFlush);
        }
    }

    private TcDraft proveBatchFallback(
            ManualTestCase tc,
            ManualTestCase bodyTc,
            AuthoringService authoring,
            HealCascade healCascade,
            TcExecutionService execution,
            Path evidence,
            boolean needsLogin,
            List<ProvenStep> loginSteps,
            String loginFormUrl,
            WebDriverFactory driverFactory,
            int tcIndex,
            int tcTotal
    ) {
        progress.update(tcIndex, tcTotal, "Phase1 " + tc.tcId() + " batch author");
        String healTier = "none";
        String healSkip = "";
        try {
            String html = HtmlSlimmer.slim(PageSnapshot.html(driverFactory.get()), proveHtmlBudget());
            List<ProvenStep> steps = authoring.author(bodyTc, html, null);
            if (steps.isEmpty() || steps.stream().anyMatch(s -> !s.validated())) {
                String reason = steps.isEmpty()
                        ? "No DOM-candidate steps authored for Excel case"
                        : "Locator validation failed: " + steps.stream()
                        .filter(s -> !s.validated())
                        .findFirst()
                        .map(ProvenStep::rationale)
                        .orElse("unknown");
                return todoDraft(tc, steps, needsLogin, loginSteps, reason, "", 0, currentUrl(driverFactory))
                        .withLoginFormUrl(loginFormUrl);
            }
            TcOutcome outcome = execution.execute(tc.tcId(), steps, evidence);
            if (outcome.status() != TcStatus.PASSED) {
                // D14: one HealCascade pass then single re-execute
                List<StepIntentBinder.IntentLine> intents = StepIntentBinder.parseIntents(bodyTc);
                StepIntentBinder.IntentLine healIntent = intents.isEmpty()
                        ? new StepIntentBinder.IntentLine(StepIntentBinder.IntentKind.CLICK, "batch heal")
                        : intents.get(Math.min(outcome.provenSteps().size(), intents.size() - 1));
                String freshHtml = HtmlSlimmer.slim(PageSnapshot.html(driverFactory.get()), proveHtmlBudget());
                byte[] failPng = execution.capturePngBytes();
                Path shot = writeHealScreenshot(evidence, tc.tcId(), 0, failPng);
                CandidateLivenessProbe probe = new SeleniumCandidateLivenessProbe(driverFactory);
                prepareHealPresence(healCascade, driverFactory);
                HealResult healed = healCascade.heal(
                        tc.tcId(), healIntent, freshHtml, failPng,
                        outcome.failureReason(), shot, true,
                        priorStepSummaries(outcome.provenSteps()), false,
                        outcome.provenSteps(),
                        recordFailures(outcome.provenSteps().isEmpty() ? steps : outcome.provenSteps(),
                                outcome.failureReason()),
                        probe);
                if (healed.ok()) {
                    healTier = healed.tierUsed();
                    TcOutcome retry = execution.execute(tc.tcId(), healed.steps(), evidence);
                    if (retry.status() == TcStatus.PASSED) {
                        outcome = retry;
                    } else {
                        healSkip = "batch re-execute after heal failed: " + retry.failureReason();
                        outcome = retry;
                    }
                } else {
                    healSkip = healed.reason().isBlank() ? "batch heal exhausted" : healed.reason();
                }
            }
            String url = currentUrl(driverFactory);
            List<ProvenStep> stamped = stampPageNames(outcome.provenSteps(), url);
            stamped = ExpectedCoverageEnricher.enrich(tc, stamped);
            loginSteps = stampLoginNames(loginSteps);
            if (outcome.status() != TcStatus.PASSED) {
                return new TcDraft(
                        tc.tcId(), tc.title(), tc.steps(), tc.expectedResult(),
                        stamped.isEmpty() ? TcDraftStatus.TODO : TcDraftStatus.PARTIAL,
                        stamped, loginSteps, needsLogin, 0, "batch",
                        outcome.failureReason(),
                        outcome.evidenceDir() == null ? "" : outcome.evidenceDir().toString(),
                        0, url)
                        .withHeal(healTier, healSkip)
                        .withLoginFormUrl(loginFormUrl);
            }
            String reject = SemanticPassGate.rejectReason(tc, stamped, url);
            if (reject != null) {
                return new TcDraft(
                        tc.tcId(), tc.title(), tc.steps(), tc.expectedResult(),
                        TcDraftStatus.PARTIAL, stamped, loginSteps, needsLogin,
                        stamped.size(), "semantic-gate", reject, "", 0, url)
                        .withHeal(healTier, healSkip)
                        .withLoginFormUrl(loginFormUrl);
            }
            return applyVisualAssertion(tc, new TcDraft(
                    tc.tcId(), tc.title(), tc.steps(), tc.expectedResult(),
                    TcDraftStatus.PASSED, stamped, loginSteps, needsLogin,
                    -1, "", "", "", 0, url)
                    .withHeal(healTier, healSkip)
                    .withLoginFormUrl(loginFormUrl), execution, evidence);
        } catch (Exception e) {
            return todoDraft(tc, List.of(), needsLogin, loginSteps,
                    "Batch author error: " + e.getMessage(), "", 0, currentUrl(driverFactory))
                    .withLoginFormUrl(loginFormUrl);
        }
    }

    private StepAttempt attemptIntentWithRetry(
            ManualTestCase tc,
            ConversionJobRequest request,
            StepIntentBinder.IntentLine intent,
            int intentIndex,
            AuthoringService authoring,
            HealCascade healCascade,
            TcExecutionService execution,
            Path evidence,
            WebDriverFactory driverFactory,
            List<ProvenStep> provenSoFar,
            List<FailedLocator> failedLocators
    ) {
        String baseUrl = request == null ? "" : request.baseUrl();
        String lastReason = "";
        String evidenceDir = "";
        List<ProvenStep> lastPartial = List.of();
        int retriesUsed = 0;
        String healTier = "none";
        String healSkipReason = "";
        List<String> priorSteps = priorStepSummaries(provenSoFar);
        List<FailedLocator> failedThisIntent = failedLocators == null
                ? new ArrayList<>()
                : failedLocators;
        CandidateLivenessProbe probe = new SeleniumCandidateLivenessProbe(driverFactory);
        VisionAttemptLog.beginIntent();
        if (healCascade != null) healCascade.beginIntent();
        boolean recoveredThisIntent = false;
        int recoveryAttemptsThisIntent = 0;
        List<ProvenStep> recoveredNav = new ArrayList<>();
        List<ProvenStep> recoveredActions = new ArrayList<>();
        try {
        RetryBudget budget = new RetryBudget(MAX_RETRIES, MAX_INFRA_RETRIES, infraBackoffMs, infraSleeper);
        try {
        for (int attempt = 0; ; attempt++) {
            JobCancelSupport.checkCancelled(cancelCheck);
            if (attempt > 0) {
                RetryCategory category = RetryCategory.classify(lastReason);
                if (!budget.allowRetry(category)) {
                    break;
                }
                retriesUsed = budget.testRetriesUsed();
                progress.update(progress.current(), progress.total(),
                        "Phase1 " + tc.tcId() + " retry " + retriesUsed + "/" + MAX_RETRIES
                                + (category == RetryCategory.INFRA
                                        ? " (infra " + budget.infraRetriesUsed() + "/" + MAX_INFRA_RETRIES + ")"
                                        : "")
                                + " on step " + (intentIndex + 1));
            }
            try {
                NamedNavigationDiscovery.reveal(driverFactory.get(), intent);
                String html = ProveWiring.authoringHtml(driverFactory.get(), intent.text(), proveHtmlBudget());
                List<DomCandidate> live = ensureCatalog(currentUrl(driverFactory), html);
                List<ProvenStep> autoFillSteps = List.of();
                if (precisionBindService == null && intent.kind() == StepIntentBinder.IntentKind.CLICK) {
                    List<StepIntentBinder.IntentLine> typeIntents = StepIntentBinder.parseIntents(tc).stream()
                            .filter(i -> i.kind() == StepIntentBinder.IntentKind.TYPE_FIELD
                                    || i.kind() == StepIntentBinder.IntentKind.TYPE_USER
                                    || i.kind() == StepIntentBinder.IntentKind.TYPE_PASS)
                            .toList();
                    List<ProvenStep> autoFills = RequiredControlFiller.planFillsBeforeClick(
                            html, tc.tcId(), intent.text(), typeIntents, provenSoFar);
                    if (!autoFills.isEmpty()) {
                        TcOutcome fillOutcome = execution.execute(tc.tcId(), autoFills, evidence);
                        if (fillOutcome.status() != TcStatus.PASSED) {
                            lastReason = "Auto-fill before click failed: " + fillOutcome.failureReason();
                            lastPartial = fillOutcome.provenSteps();
                            evidenceDir = fillOutcome.evidenceDir() == null ? ""
                                    : fillOutcome.evidenceDir().toString();
                            continue;
                        }
                        autoFillSteps = fillOutcome.provenSteps();
                        PostActionSettle.afterAction();
                        html = ProveWiring.authoringHtml(driverFactory.get(), intent.text(), proveHtmlBudget());
                        live = ensureCatalog(currentUrl(driverFactory), html);
                    }
                }

                if (intent.kind() == StepIntentBinder.IntentKind.ASSERT_VISIBLE) {
                    String rewritten = LiveHeadingQuote.maybeRewrite(intent.text(), html);
                    if (!rewritten.equals(intent.text())) {
                        intent = new StepIntentBinder.IntentLine(
                                StepIntentBinder.IntentKind.ASSERT_VISIBLE, rewritten, intent.testData());
                    }
                }

                if (intent.kind() == StepIntentBinder.IntentKind.ASSERT_VISIBLE
                        && CompoundAssert.isCompound(intent.text())) {
                    return attemptCompoundSoftAssert(
                            tc, intent, authoring, execution, evidence, driverFactory, provenSoFar,
                            failedThisIntent, autoFillSteps, retriesUsed, healTier, recoveredNav,
                            recoveredActions, baseUrl, recoveredThisIntent);
                }

                String memoryHost = hostOf(currentUrl(driverFactory));
                if (memoryHost.isBlank()) {
                    memoryHost = hostOf(baseUrl);
                }
                // Key by live page path — Excel open-path (/login) poisoned cart clicks with login-button.
                String pagePath = pathOf(currentUrl(driverFactory));
                Optional<ProvenStep> remembered = mayUseLocatorMemory()
                        ? locatorMemory.recall(memoryHost, pagePath, intent, tc.tcId()) : Optional.empty();
                if (remembered.isPresent()
                        && StepIntentBinder.spendsMustAvoidPriorFills(intent)
                        && StepIntentBinder.isSpentLocator(remembered.get(), provenSoFar)) {
                    locatorMemory.forget(memoryHost, pagePath, intent);
                    LogsManager.info("LOCATOR_MEMORY: dropped spent locator for " + intent.text());
                    remembered = Optional.empty();
                }
                if (remembered.isPresent()) {
                    ProvenStep ready = rewriteRememberedLocator(
                            withInventedValue(remembered.get(), intent), html);
                    if (refusesSubmitNavigation(intent, List.of(ready))) {
                        locatorMemory.forget(memoryHost, pagePath, intent);
                        LogsManager.info("SUBMIT_REFUSE: dropped remembered non-submit click for "
                                + intent.text());
                    } else {
                        LogsManager.info("LOCATOR_MEMORY: using "
                                + ready.locatorStrategy() + ":" + ready.locatorValue()
                                + " for " + intent.text());
                        TcOutcome memOutcome = execution.execute(tc.tcId(), List.of(ready), evidence);
                        if (memOutcome.status() == TcStatus.PASSED) {
                            List<ProvenStep> combined = new ArrayList<>(autoFillSteps);
                            combined.addAll(memOutcome.provenSteps());
                            if (retryAfterPageRecovery(
                                    combined, tc, baseUrl, intent, driverFactory, recoveredThisIntent)) {
                                locatorMemory.forget(memoryHost, pagePath, intent);
                                recoveredThisIntent = true;
                                keepRecoveredNavigate(recoveredNav, combined);
                                lastReason = "Recovered to Excel open-path; retry bind";
                                continue;
                            }
                            return settledSuccess(
                                    withRecoveredPrefix(recoveredNav, recoveredActions, combined),
                                    retriesUsed, healTier, healSkipReason,
                                    driverFactory, intent.text());
                        }
                        locatorMemory.forget(memoryHost, pagePath, intent);
                        LogsManager.info("LOCATOR_MEMORY: dropped after execute fail for " + intent.text());
                    }
                } else if (precisionBindService != null && precisionReuseLocatorMemory) {
                    logLocatorMemorySkip(locatorMemorySkipContext(
                            "single step — no saved locator", memoryHost, pagePath, intent)
                            + " (falling back to Cursor groundRank)");
                }

                byte[] healPng = execution.capturePngBytes();
                Path bindShot = writeHealScreenshot(evidence, tc.tcId(), intentIndex, healPng);
                boolean precisionPath = precisionBindService != null;
                // Bind only — heal cascade owns Ollama + Cursor after bind/execute failure
                List<ProvenStep> stepBatch;
                if (precisionPath) {
                    logPrecisionLocatorMemoryDisabledOnce();
                    PrecisionBindResult precision = bindWithPrecision(
                            authoring, tc.tcId(), intent, html, bindShot, priorSteps,
                            provenSoFar, failedThisIntent, healPng, probe);
                    stepBatch = precision.steps();
                    if (precision.fellBack()) {
                        precisionTracker.noteTcFallback(precision.fallbackReason());
                        precisionPath = false;
                    }
                    if (precision.tier() != null && !precision.tier().isBlank()) {
                        healTier = mergeHealTier(healTier, precision.tier());
                    }
                } else {
                    stepBatch = authoring.authorIntent(
                            tc.tcId(), intent, html, healPng, false, provenSoFar, failedThisIntent, live);
                }
                boolean bindFailed = stepBatch.isEmpty()
                        || stepBatch.stream().anyMatch(s -> !s.validated())
                        || refusesSubmitNavigation(intent, stepBatch);
                boolean ollamaUsed = false;
                boolean cursorUsed = false;
                if (bindFailed && !precisionPath && VisionGroundingConfig.enabled()
                        && VisionTriggers.isEligible(intent)
                        && VisionTriggers.isWeakBind(stepBatch)) {
                    bindQuality.visionAsked++;
                    java.util.Optional<java.util.List<ProvenStep>> grounded = VisionProveHook.tryLayer15(
                            tc.tcId(), intent, html, healPng, stepBatch, bindFailed,
                            failedThisIntent, authoring,
                            new SeleniumGroundingBrowser(driverFactory.get()),
                            VisionGroundingConfig.createProvider(),
                            live);
                    if (grounded.isPresent()) {
                        if (refusesSubmitNavigation(intent, grounded.get())) {
                            LogsManager.info("SUBMIT_REFUSE: vision grounded a non-submit navigation for "
                                    + intent.text());
                        } else {
                            stepBatch = pageCatalog.stabilizeSteps(grounded.get(), intent);
                            stepBatch = NestedPreferredHook.rewrite(stepBatch, html);
                            bindFailed = false;
                            healTier = mergeHealTier(healTier, "vision");
                            LogsManager.info("VISION_GROUND: READY intent=" + intent.text());
                        }
                    }
                }
                if (bindFailed) {
                    lastReason = stepBatch.isEmpty()
                            ? "No DOM candidate for intent " + intent.kind() + ": " + intent.text()
                            : stepBatch.stream().filter(s -> !s.validated()).findFirst()
                            .map(ProvenStep::rationale).orElse("unknown");
                    if (!mayHeal(intent, lastReason)) {
                        return StepAttempt.fail(lastReason, autoFillSteps, evidenceDir,
                                retriesUsed, healTier, lastReason);
                    }
                    if (!recoveredThisIntent
                            && TargetPageRecovery.tryRecover(driverFactory, baseUrl, tc, intent, html)) {
                        recoveredThisIntent = true;
                        lastReason = "Recovered to Excel open-path; retry bind";
                        continue;
                    }
                    Path shot = writeHealScreenshot(evidence, tc.tcId(), intentIndex, healPng);
                    prepareHealPresence(healCascade, driverFactory);
                    HealResult healed = healCascade.heal(
                            tc.tcId(), intent, html, healPng, lastReason, shot, !precisionPath, priorSteps,
                            true, provenSoFar, failedThisIntent, probe);
                    if (!healed.ok()) {
                        lastPartial = autoFillSteps;
                        lastReason = healed.reason().isBlank() ? lastReason : healed.reason();
                        healSkipReason = lastReason;
                        continue;
                    }
                    RecoveryOutcome recovery = tryRecoveryHeal(
                            healed, tc, execution, evidence, recoveryAttemptsThisIntent, recoveredActions,
                            request);
                    if (recovery == RecoveryOutcome.RECOVERY_RETRY) {
                        recoveryAttemptsThisIntent++;
                        healTier = mergeHealTier(healTier, "recovery");
                        continue;
                    }
                    if (recovery == RecoveryOutcome.RECOVERY_FAILED) {
                        recoveryAttemptsThisIntent++;
                        lastPartial = autoFillSteps;
                        lastReason = "Recovery steps failed";
                        healSkipReason = lastReason;
                        continue;
                    }
                    stepBatch = healed.steps();
                    healTier = mergeHealTier(healTier, healed.tierUsed());
                    if ("ollama".equals(healed.tierUsed()) || "vision".equals(healed.tierUsed())) {
                        ollamaUsed = true;
                    } else if ("cursor".equals(healed.tierUsed()) || "invent".equals(healed.tierUsed())) {
                        cursorUsed = true;
                    }
                }

                if (refusesSubmitNavigation(intent, stepBatch)) {
                    LogsManager.info("SUBMIT_REFUSE: skip execute for " + intent.text()
                            + " locator=" + (stepBatch.isEmpty() ? "" : stepBatch.get(0).locatorValue()));
                    lastReason = "SUBMIT_REFUSE: Submit bound to a non-submit navigation href";
                    lastPartial = autoFillSteps;
                    continue;
                }
                TcOutcome stepOutcome = execution.execute(tc.tcId(), stepBatch, evidence);
                if (stepOutcome.status() != TcStatus.PASSED) {
                    lastReason = stepOutcome.failureReason();
                    failedThisIntent.addAll(recordFailures(stepBatch, lastReason));
                    List<ProvenStep> partial = new ArrayList<>(autoFillSteps);
                    partial.addAll(stepOutcome.provenSteps());
                    lastPartial = partial;
                    evidenceDir = stepOutcome.evidenceDir() == null ? ""
                            : stepOutcome.evidenceDir().toString();
                    if (!mayHeal(intent, lastReason)) {
                        return StepAttempt.fail(lastReason, partial, evidenceDir,
                                retriesUsed, healTier, lastReason);
                    }

                    // Cap: Ollama once + Cursor once. Escalate to Cursor after Ollama execute-fail (D3).
                    if (!ollamaUsed && !cursorUsed) {
                        String freshHtml = HtmlSlimmer.slim(PageSnapshot.html(driverFactory.get()), proveHtmlBudget());
                        byte[] failPng = execution.capturePngBytes();
                        Path shot = writeHealScreenshot(evidence, tc.tcId(), intentIndex, failPng);
                        prepareHealPresence(healCascade, driverFactory);
                        HealResult healed = healCascade.heal(
                                tc.tcId(), intent, freshHtml, failPng, lastReason, shot, !precisionPath,
                                priorSteps, true, provenSoFar, failedThisIntent, probe);
                        RecoveryOutcome recovery = tryRecoveryHeal(
                                healed, tc, execution, evidence, recoveryAttemptsThisIntent, recoveredActions,
                                request);
                        if (recovery == RecoveryOutcome.RECOVERY_RETRY) {
                            recoveryAttemptsThisIntent++;
                            healTier = mergeHealTier(healTier, "recovery");
                            continue;
                        }
                        if (recovery == RecoveryOutcome.RECOVERY_FAILED) {
                            recoveryAttemptsThisIntent++;
                            healSkipReason = "Recovery steps failed";
                            lastReason = healSkipReason;
                            continue;
                        }
                        if (healed.ok() && !refusesSubmitNavigation(intent, healed.steps())) {
                            healTier = mergeHealTier(healTier, healed.tierUsed());
                            if ("ollama".equals(healed.tierUsed()) || "vision".equals(healed.tierUsed())) {
                                ollamaUsed = true;
                            } else if ("cursor".equals(healed.tierUsed())
                                    || "invent".equals(healed.tierUsed())) {
                                cursorUsed = true;
                            }
                            TcOutcome retryOutcome = execution.execute(tc.tcId(), healed.steps(), evidence);
                            if (retryOutcome.status() == TcStatus.PASSED) {
                                List<ProvenStep> combined = new ArrayList<>(autoFillSteps);
                                combined.addAll(retryOutcome.provenSteps());
                                if (retryAfterPageRecovery(
                                        combined, tc, baseUrl, intent, driverFactory, recoveredThisIntent)) {
                                    recoveredThisIntent = true;
                                    keepRecoveredNavigate(recoveredNav, combined);
                                    lastReason = "Recovered to Excel open-path; retry bind";
                                    continue;
                                }
                                return settledSuccess(
                                        withRecoveredPrefix(recoveredNav, recoveredActions, combined),
                                        retriesUsed, healTier, "",
                                        driverFactory, intent.text());
                            }
                            lastReason = retryOutcome.failureReason();
                            failedThisIntent.addAll(recordFailures(healed.steps(), lastReason));
                            List<ProvenStep> healedPartial = new ArrayList<>(autoFillSteps);
                            healedPartial.addAll(retryOutcome.provenSteps());
                            lastPartial = healedPartial;
                            evidenceDir = retryOutcome.evidenceDir() == null ? evidenceDir
                                    : retryOutcome.evidenceDir().toString();
                        } else {
                            healSkipReason = healed.reason().isBlank()
                                    ? "HEAL_EXHAUSTED: " + lastReason
                                    : healed.reason();
                            lastReason = healSkipReason;
                            continue;
                        }
                    }

                    if (!cursorUsed) {
                        String html2 = HtmlSlimmer.slim(PageSnapshot.html(driverFactory.get()), proveHtmlBudget());
                        byte[] png2 = execution.capturePngBytes();
                        Path shot2 = writeHealScreenshot(evidence, tc.tcId(), intentIndex, png2);
                        prepareHealPresence(healCascade, driverFactory);
                        HealResult cursorHeal = healCascade.heal(
                                tc.tcId(), intent, html2, png2,
                                "re-execute after Ollama heal failed: " + lastReason,
                                shot2, false, priorSteps, true, provenSoFar, failedThisIntent, probe);
                        EscalateHealDecision escalate = decideEscalateHeal(
                                cursorHeal, recoveryAttemptsThisIntent);
                        if (escalate == EscalateHealDecision.RUN_RECOVERY_AND_RETRY_INTENT
                                || escalate == EscalateHealDecision.RECOVERY_ALREADY_ATTEMPTED_FAIL) {
                            RecoveryOutcome recovery = tryRecoveryHeal(
                                    cursorHeal, tc, execution, evidence, recoveryAttemptsThisIntent, recoveredActions,
                                    request);
                            if (recovery == RecoveryOutcome.RECOVERY_RETRY) {
                                recoveryAttemptsThisIntent++;
                                healTier = mergeHealTier(healTier, "recovery");
                                continue;
                            }
                            recoveryAttemptsThisIntent++;
                            healSkipReason = "Recovery steps failed";
                            lastReason = healSkipReason;
                            continue;
                        }
                        if (escalate == EscalateHealDecision.EXECUTE_AS_INTENT_FIX
                                && !refusesSubmitNavigation(intent, cursorHeal.steps())) {
                            cursorUsed = true;
                            healTier = mergeHealTier(healTier, cursorHeal.tierUsed());
                            TcOutcome cursorRetry = execution.execute(
                                    tc.tcId(), cursorHeal.steps(), evidence);
                            if (cursorRetry.status() == TcStatus.PASSED) {
                                List<ProvenStep> combined = new ArrayList<>(autoFillSteps);
                                combined.addAll(cursorRetry.provenSteps());
                                if (retryAfterPageRecovery(
                                        combined, tc, baseUrl, intent, driverFactory, recoveredThisIntent)) {
                                    recoveredThisIntent = true;
                                    keepRecoveredNavigate(recoveredNav, combined);
                                    lastReason = "Recovered to Excel open-path; retry bind";
                                    continue;
                                }
                                return settledSuccess(
                                        withRecoveredPrefix(recoveredNav, recoveredActions, combined),
                                        retriesUsed, healTier, "",
                                        driverFactory, intent.text());
                            }
                            lastReason = "HEAL_EXHAUSTED: re-execute after Cursor heal failed: "
                                    + cursorRetry.failureReason();
                            healSkipReason = lastReason;
                            failedThisIntent.addAll(recordFailures(cursorHeal.steps(), lastReason));
                            List<ProvenStep> healedPartial = new ArrayList<>(autoFillSteps);
                            healedPartial.addAll(cursorRetry.provenSteps());
                            lastPartial = healedPartial;
                            evidenceDir = cursorRetry.evidenceDir() == null ? evidenceDir
                                    : cursorRetry.evidenceDir().toString();
                        } else {
                            healSkipReason = cursorHeal.reason().isBlank()
                                    ? "HEAL_EXHAUSTED: Cursor skip after Ollama execute-fail"
                                    : cursorHeal.reason();
                            lastReason = healSkipReason;
                        }
                    } else if (cursorUsed) {
                        lastReason = "HEAL_EXHAUSTED: execute still failed after Cursor heal: " + lastReason;
                        healSkipReason = lastReason;
                    }
                    continue;
                }
                List<ProvenStep> combined = new ArrayList<>(autoFillSteps);
                combined.addAll(stepOutcome.provenSteps());
                if (retryAfterPageRecovery(
                        combined, tc, baseUrl, intent, driverFactory, recoveredThisIntent)) {
                    recoveredThisIntent = true;
                    keepRecoveredNavigate(recoveredNav, combined);
                    lastReason = "Recovered to Excel open-path; retry bind";
                    continue;
                }
                return settledSuccess(
                        withRecoveredPrefix(recoveredNav, recoveredActions, combined),
                        retriesUsed, healTier, healSkipReason,
                        driverFactory, intent.text());
            } catch (Exception e) {
                lastReason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                lastPartial = List.of();
            }
        }
        try {
            ProveWiring.bindFailure(driverFactory.get(), evidence, tc.tcId(), intentIndex, intent.text(),
                    intent.kind() == null ? null : intent.kind().name(), intent.testData(), lastReason);
        } catch (RuntimeException ignored) {
            // A9 evidence is best effort (flag off by default)
        }
        return StepAttempt.fail(lastReason, lastPartial, evidenceDir, retriesUsed, healTier, healSkipReason);
        } finally {
            tcTestRetries += budget.testRetriesUsed();
            tcTestFailures += budget.testFailures();
            tcInfraRetries += budget.infraRetriesUsed();
        }
        } finally {
            VisionAttemptLog.endIntent();
        }
    }

    /**
     * Cursor last-layer navigate, or a form-submit that left the Excel path, is recovery —
     * retry the original intent on the recovered page instead of treating the leave as PASS.
     */
    private boolean retryAfterPageRecovery(
            List<ProvenStep> executed,
            ManualTestCase tc,
            String baseUrl,
            StepIntentBinder.IntentLine intent,
            WebDriverFactory driverFactory,
            boolean recoveredThisIntent) {
        if (onlyExcelPathNavigate(executed)) {
            return true;
        }
        if (recoveredThisIntent) {
            return false;
        }
        try {
            String html = HtmlSlimmer.slim(PageSnapshot.html(driverFactory.get()), proveHtmlBudget());
            return TargetPageRecovery.tryRecover(driverFactory, baseUrl, tc, intent, html);
        } catch (RuntimeException e) {
            return false;
        }
    }

    static boolean onlyExcelPathNavigate(List<ProvenStep> steps) {
        if (steps == null || steps.isEmpty()) {
            return false;
        }
        return steps.stream().allMatch(s -> s != null && "navigate".equalsIgnoreCase(s.action()));
    }

    private static void keepRecoveredNavigate(List<ProvenStep> recoveredNav, List<ProvenStep> executed) {
        if (recoveredNav == null || executed == null) {
            return;
        }
        for (ProvenStep step : executed) {
            if (step != null && "navigate".equalsIgnoreCase(step.action())) {
                recoveredNav.add(step);
            }
        }
    }

    private static List<ProvenStep> withRecoveredNavigate(
            List<ProvenStep> recoveredNav, List<ProvenStep> executed) {
        if (recoveredNav == null || recoveredNav.isEmpty()) {
            return executed;
        }
        List<ProvenStep> out = new ArrayList<>(recoveredNav);
        if (executed != null) {
            out.addAll(executed);
        }
        return out;
    }

    /** Prefix navigate recovery + heal recovery actions before the intent's proven steps. */
    private static List<ProvenStep> withRecoveredPrefix(
            List<ProvenStep> recoveredNav,
            List<ProvenStep> recoveredActions,
            List<ProvenStep> executed) {
        List<ProvenStep> out = new ArrayList<>();
        if (recoveredNav != null) {
            out.addAll(recoveredNav);
        }
        if (recoveredActions != null) {
            out.addAll(recoveredActions);
        }
        if (executed != null) {
            out.addAll(executed);
        }
        return out;
    }

    /**
     * Build failed-locator entries from a batch that just failed execute.
     * Package-visible for unit tests.
     */
    static List<FailedLocator> recordFailures(List<ProvenStep> attempted, String executeError) {
        if (attempted == null || attempted.isEmpty()) {
            return List.of();
        }
        String summary = executeError == null ? "" : executeError.trim();
        if (summary.length() > 200) {
            summary = summary.substring(0, 200);
        }
        java.util.LinkedHashMap<String, FailedLocator> byKey = new java.util.LinkedHashMap<>();
        for (ProvenStep step : attempted) {
            if (step == null || step.locatorValue() == null || step.locatorValue().isBlank()) {
                continue;
            }
            String strategy = step.locatorStrategy() == null ? "" : step.locatorStrategy();
            String key = strategy.toLowerCase() + ":" + step.locatorValue();
            byKey.putIfAbsent(key, new FailedLocator(strategy, step.locatorValue(), summary));
        }
        return List.copyOf(byKey.values());
    }

    private static void prepareHealPresence(HealCascade healCascade, WebDriverFactory driverFactory) {
        if (healCascade == null) {
            return;
        }
        try {
            healCascade.setPresenceHtml(PageSnapshot.html(driverFactory.get()));
        } catch (Exception e) {
            healCascade.setPresenceHtml("");
        }
    }

    private enum RecoveryOutcome { NOT_RECOVERY, RECOVERY_RETRY, RECOVERY_FAILED }

    /** How Cursor escalate-after-Ollama should treat a heal result (package-visible for tests). */
    enum EscalateHealDecision {
        RUN_RECOVERY_AND_RETRY_INTENT,
        RECOVERY_ALREADY_ATTEMPTED_FAIL,
        EXECUTE_AS_INTENT_FIX,
        HEAL_FAILED
    }

    static final int MAX_RECOVERY_ATTEMPTS = 2;

    static boolean isRecoveryTier(HealResult healed) {
        return healed != null && healed.ok() && "recovery".equals(healed.tierUsed());
    }

    /**
     * Recovery-tier heals must never be executed as the original intent batch.
     * Classic cursor/invent fixes use {@link EscalateHealDecision#EXECUTE_AS_INTENT_FIX}.
     */
    static EscalateHealDecision decideEscalateHeal(HealResult cursorHeal, int recoveryAttemptsUsed) {
        if (cursorHeal == null || !cursorHeal.ok()) {
            return EscalateHealDecision.HEAL_FAILED;
        }
        if (isRecoveryTier(cursorHeal)) {
            return recoveryAttemptsUsed >= MAX_RECOVERY_ATTEMPTS
                    ? EscalateHealDecision.RECOVERY_ALREADY_ATTEMPTED_FAIL
                    : EscalateHealDecision.RUN_RECOVERY_AND_RETRY_INTENT;
        }
        return EscalateHealDecision.EXECUTE_AS_INTENT_FIX;
    }

    private RecoveryOutcome tryRecoveryHeal(
            HealResult healed,
            ManualTestCase tc,
            TcExecutionService execution,
            Path evidence,
            int recoveryAttemptsUsed,
            List<ProvenStep> recoveredActionsOut,
            ConversionJobRequest request
    ) {
        if (!healed.ok() || !"recovery".equals(healed.tierUsed())) {
            return RecoveryOutcome.NOT_RECOVERY;
        }
        if (recoveryAttemptsUsed >= MAX_RECOVERY_ATTEMPTS) {
            return RecoveryOutcome.RECOVERY_FAILED;
        }
        TcOutcome outcome = execution.execute(tc.tcId(), healed.steps(), evidence);
        if (outcome.status() != TcStatus.PASSED) {
            LogsManager.info("HEAL_RECOVERY_FAILED: " + outcome.failureReason());
            return RecoveryOutcome.RECOVERY_FAILED;
        }
        if (recoveredActionsOut != null) {
            List<ProvenStep> proven = outcome.provenSteps();
            recoveredActionsOut.addAll(proven == null || proven.isEmpty() ? healed.steps() : proven);
        }
        writeHealRecoveryEvidence(evidence, tc.tcId(), healed);
        applyHealWorkbookPatchBestEffort(healWorkbookApplier, request, tc.tcId(), healed);
        PostActionSettle.afterAction();
        LogsManager.info("HEAL_RECOVERY: executed " + healed.steps().size()
                + " steps for " + tc.tcId() + "; retrying intent");
        return RecoveryOutcome.RECOVERY_RETRY;
    }

    /** Best-effort generated-workbook patch after successful recovery; never throws. */
    static void applyHealWorkbookPatchBestEffort(
            HealWorkbookApplier applier,
            ConversionJobRequest request,
            String tcId,
            HealResult healed
    ) {
        if (applier == null || request == null || healed == null || !healed.ok()) {
            return;
        }
        if (!"recovery".equals(healed.tierUsed())) {
            return;
        }
        try {
            applier.apply(
                    request.projectId(),
                    tcId,
                    healed.steps(),
                    healed.automationNotes(),
                    request.baseUrl());
        } catch (Exception e) {
            LogsManager.warn("HEAL_WORKBOOK_PATCH: " + e.getMessage());
        }
    }

    private static void writeHealRecoveryEvidence(Path evidenceRoot, String tcId, HealResult healed) {
        if (evidenceRoot == null || healed == null || !healed.ok()) {
            return;
        }
        try {
            Path dir = evidenceRoot.resolve(tcId);
            Files.createDirectories(dir);
            JSONObject obj = new JSONObject();
            obj.put("mode", "recovery");
            obj.put("thought", healed.recoveryThought());
            obj.put("automationNotes", new JSONArray(healed.automationNotes()));
            JSONArray steps = new JSONArray();
            for (ProvenStep step : healed.steps()) {
                JSONObject item = new JSONObject();
                item.put("action", step.action());
                item.put("locatorStrategy", step.locatorStrategy());
                item.put("locatorValue", step.locatorValue());
                item.put("value", step.value() == null ? "" : step.value());
                steps.put(item);
            }
            obj.put("recoverySteps", steps);
            Files.writeString(dir.resolve("heal-recovery.json"), obj.toString(2));
            if (!healed.automationNotes().isEmpty()) {
                Files.writeString(dir.resolve("automation-notes.txt"),
                        String.join("\n", healed.automationNotes()));
            }
        } catch (Exception e) {
            LogsManager.warn("HEAL_RECOVERY_EVIDENCE: " + e.getMessage());
        }
    }

    private static Path writeHealScreenshot(Path evidenceRoot, String tcId, int intentIndex, byte[] png) {
        if (png == null || png.length == 0 || evidenceRoot == null) {
            return null;
        }
        try {
            Files.createDirectories(evidenceRoot);
            Path shot = evidenceRoot.resolve(
                    "heal-" + sanitize(tcId) + "-i" + intentIndex + "-" + System.nanoTime() + ".png");
            Files.write(shot, png);
            return shot;
        } catch (Exception e) {
            return null;
        }
    }

    private static String sanitize(String tcId) {
        if (tcId == null || tcId.isBlank()) {
            return "tc";
        }
        return tcId.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    static boolean shouldDemoteForVisual(
            VisionAssertionResult result, String assertionText, String currentUrl) {
        if (result == null || result.status() == VisionAssertionStatus.PASS) {
            return false;
        }
        if (result.status() == VisionAssertionStatus.UNCERTAIN) {
            String err = result.error() == null ? "" : result.error().toLowerCase();
            // Honesty-gate rejections must not leave a silent green TC
            return err.contains("placeholder")
                    || err.contains("echoed")
                    || err.contains("too short")
                    || err.contains("confidence below");
        }
        if (VisionAssertionGate.looksLikeRegistrationClaim(assertionText)
                && VisionAssertionGate.looksLikeLoginUrl(currentUrl)) {
            return true;
        }
        return result.confidence() >= VisionAssertionGate.MIN_PASS_CONFIDENCE;
    }

    static boolean refusesSubmitNavigation(
            StepIntentBinder.IntentLine intent, List<ProvenStep> steps) {
        if (intent == null || !StepIntentBinder.wantsFormSubmit(intent.text()) || steps == null) {
            return false;
        }
        for (ProvenStep step : steps) {
            if (step == null || !"click".equalsIgnoreCase(step.action())) {
                continue;
            }
            if (StepIntentBinder.looksLikeNonSubmitNavigationLocator(step.locatorValue())) {
                return true;
            }
        }
        return false;
    }

    private static Path memoryFile(ConversionJobRequest request) {
        if (request == null || request.storeRoot() == null) {
            return null;
        }
        if (request.tenantId() != null) {
            return DomainLocatorMemory.sharedFile(
                    request.storeRoot(), request.tenantId(), request.baseUrl());
        }
        Path shared = DomainLocatorMemory.sharedFile(request.storeRoot(), request.baseUrl());
        if (shared != null) {
            return shared;
        }
        if (request.projectId() == null || request.projectId().isBlank()) {
            return null;
        }
        return new ProjectStore(request.storeRoot(), request.baseUrl(), request.tenantId())
                .projectRoot(request.projectId())
                .resolve(DomainLocatorMemory.FILE_NAME);
    }

    private void saveLocatorMemory() {
        locatorMemory.save(locatorMemoryFile);
    }

    
    private List<DomCandidate> ensureCatalog(String url, String html) {
        int before = pageCatalog.generation();
        List<DomCandidate> live = pageCatalog.ensure(url, html);
        if (pageCatalog.generation() > before) {
            bindQuality.catalogRebuild++;
        } else {
            bindQuality.catalogHit++;
        }
        return live;
    }

    private void flushBindQuality(String evidenceDir) {
        LogsManager.info(bindQuality.summaryLine());
        if (evidenceDir == null || evidenceDir.isBlank()) {
            return;
        }
        try {
            bindQuality.writeJson(Path.of(evidenceDir).resolve("bind-quality.json"));
        } catch (Exception e) {
            LogsManager.info("BIND_QUALITY write failed: " + e.getMessage());
        }
    }
    private void rememberSuccessfulLocator(
            ConversionJobRequest request,
            WebDriverFactory driverFactory,
            ManualTestCase tc,
            StepIntentBinder.IntentLine intent,
            List<ProvenStep> steps, String bindingPageUrl) {
        if (steps == null || steps.isEmpty() || intent == null) {
            return;
        }
        String host = hostOf(bindingPageUrl);
        if (host.isBlank()) {
            host = hostOf(request == null ? "" : request.baseUrl());
        }
        String pagePath = pathOf(bindingPageUrl);
        locatorMemory.remember(host, pagePath, intent, steps.get(steps.size() - 1));
        saveLocatorMemory();
        PageLocatorCatalog.commitProvenSteps(pageCatalog, steps);
    }

    private static String pathOf(String url) {
        if (url == null || url.isBlank()) {
            return "/";
        }
        try {
            java.net.URI uri = java.net.URI.create(url.trim());
            String path = uri.getPath();
            if (path == null || path.isBlank()) {
                return "/";
            }
            return path;
        } catch (Exception e) {
            return "/";
        }
    }

    private static ProvenStep withInventedValue(ProvenStep step, StepIntentBinder.IntentLine intent) {
        if (step == null || intent == null) {
            return step;
        }
        String action = step.action() == null ? "" : step.action().toLowerCase(Locale.ROOT);
        if (!("type".equals(action) || "select".equals(action))) {
            return step;
        }
        String inputType = intent.kind() == StepIntentBinder.IntentKind.TYPE_PASS ? "password" : "text";
        String locatorHint = (step.locatorValue() == null ? "" : step.locatorValue())
                + " " + (intent.text() == null ? "" : intent.text());
        if (DummyValueInventor.looksLikeOtpHint(locatorHint)
                && intent.kind() == StepIntentBinder.IntentKind.TYPE_PASS) {
            inputType = "text";
        }
        String value = DummyValueInventor.fromStepOrInvent(
                intent.text(), intent.testData(), "input", inputType, "", intent.text(), intent.text());
        if (!DummyValueInventor.looksLikeOtpHint(locatorHint)) {
            value = StepIntentBinder.resolveLoginTypedValue(intent.kind(), intent.text(), value);
        }
        return new ProvenStep(
                step.tcId(), step.pageName(), step.actionType(), step.action(),
                step.locatorStrategy(), step.locatorValue(), value,
                step.assertionType(), step.assertionExpected(), step.validated(),
                step.rationale(), step.screenshotRelPath(), step.locatorCandidates(), step.dropCandidates(), step.sideEffects(), step.secret());
    }

    private static boolean isOpenNavigationIntent(StepIntentBinder.IntentLine intent) {
        if (intent == null || intent.text() == null) {
            return false;
        }
        String t = intent.text().toLowerCase(Locale.ROOT);
        return t.contains("open the") && t.contains(" at /");
    }

    /** Explicit navigation begins a new planning snapshot. */
    private static boolean breaksPrecisionBatch(StepIntentBinder.IntentLine intent) {
        if (intent == null) {
            return true;
        }
        if (isOpenNavigationIntent(intent)) {
            return true;
        }
        if (intent.kind() == StepIntentBinder.IntentKind.ASSERT_VISIBLE) {
            return true;
        }
        return false;
    }

    static int precisionBatchEnd(List<StepIntentBinder.IntentLine> intents, int start) {
        if (start >= intents.size()) {
            return start;
        }
        if (breaksPrecisionBatch(intents.get(start))) {
            return start + 1;
        }
        int end = start + 1;
        int max = PrecisionBindService.MAX_INTENTS_PER_GROUNDRANK_BATCH;
        while (end < intents.size() && end - start < max) {
            if (breaksPrecisionBatch(intents.get(end))) {
                break;
            }
            end++;
        }
        return end;
    }

    private PrecisionBatchStepAttempt attemptPrecisionBatch(
            ManualTestCase tc,
            ConversionJobRequest request,
            List<StepIntentBinder.IntentLine> batchSlice,
            int intentIndex,
            AuthoringService authoring,
            TcExecutionService execution,
            Path evidence,
            WebDriverFactory driverFactory,
            List<ProvenStep> provenSoFar,
            List<FailedLocator> failedThisTc
    ) {
        if (precisionBindService == null || batchSlice == null || batchSlice.size() < 2) {
            return null;
        }
        if (precisionReuseLocatorMemory) {
            PrecisionBatchStepAttempt fromMemory = attemptPrecisionBatchFromMemory(
                    tc, request, batchSlice, intentIndex, execution, evidence, driverFactory, provenSoFar);
            if (fromMemory != null) {
                return fromMemory;
            }
        } else {
            logPrecisionLocatorMemoryDisabledOnce();
        }
        try {
            String html = HtmlSlimmer.slim(PageSnapshot.html(driverFactory.get()), proveHtmlBudget());
            byte[] png = execution.capturePngBytes();
            Path shot = writeHealScreenshot(evidence, tc.tcId(), intentIndex, png);
            List<String> priorSteps = priorStepSummaries(provenSoFar);
            CandidateLivenessProbe probe = new SeleniumCandidateLivenessProbe(driverFactory);

            PrecisionBatchBindOutcome outcome = precisionBindService.bindIntentBatch(
                    tc.tcId(), batchSlice, html, shot, priorSteps, provenSoFar, failedThisTc, probe);
            if (!(outcome instanceof PrecisionBatchBindOutcome.BatchBound bound)) {
                return null;
            }
            List<ProvenStep> allSteps = new ArrayList<>();
            List<List<ProvenStep>> perIntent = new ArrayList<>();
            for (PrecisionBatchBindOutcome.IntentSteps row : bound.boundInOrder()) {
                if (row.batchIndex() < 0 || row.batchIndex() >= batchSlice.size()) {
                    return null;
                }
                StepIntentBinder.IntentLine rowIntent = batchSlice.get(row.batchIndex());
                List<ProvenStep> resolved = new ArrayList<>();
                for (ProvenStep step : row.steps()) {
                    resolved.add(withInventedValue(step, rowIntent));
                }
                if (refusesSubmitNavigation(rowIntent, resolved)) {
                    return null;
                }
                perIntent.add(resolved);
                allSteps.addAll(resolved);
            }
            if (allSteps.isEmpty()) {
                return null;
            }
            if (!html.equals(HtmlSlimmer.slim(PageSnapshot.html(driverFactory.get()), proveHtmlBudget()))) return null;
            PrecisionPageGuard guard = new PrecisionPageGuard(driverFactory.get(), perIntent);
            return executePrecisionBound(tc.tcId(), perIntent, execution, evidence,
                    bound.tier(), cancelCheck, () -> currentUrl(driverFactory),
                    steps -> PostActionSettle.afterSteps(driverFactory.get(), steps), guard::valid);
        } catch (Exception e) {
            LogsManager.info("PRECISION_GROUNDRANK_BATCH: error " + e.getMessage());
            return null;
        }
    }

    private StepAttempt attemptCompoundSoftAssert(
            ManualTestCase tc,
            StepIntentBinder.IntentLine intent,
            AuthoringService authoring,
            TcExecutionService execution,
            Path evidence,
            WebDriverFactory driverFactory,
            List<ProvenStep> provenSoFar,
            List<FailedLocator> failedThisIntent,
            List<ProvenStep> autoFillSteps,
            int retriesUsed,
            String healTier,
            List<ProvenStep> recoveredNav,
            List<ProvenStep> recoveredActions,
            String baseUrl,
            boolean recoveredThisIntent
    ) {
        List<String> clauses = CompoundAssert.splitClauses(intent.text());
        LogsManager.info("COMPOUND_ASSERT: soft check " + clauses.size()
                + " clauses (Keel bind, not Cursor)");
        List<ProvenStep> provenCombined = new ArrayList<>(autoFillSteps);
        int bound = 0;
        int passed = 0;
        int skipped = 0;
        List<String> executeFails = new ArrayList<>();
        String evidenceDir = "";
        for (String clause : clauses) {
            JobCancelSupport.checkCancelled(cancelCheck);
            String html = HtmlSlimmer.slim(PageSnapshot.html(driverFactory.get()), proveHtmlBudget());
            byte[] healPng = execution.capturePngBytes();
            String rewrittenClause = LiveHeadingQuote.maybeRewrite(clause, html);
            StepIntentBinder.IntentLine sub = new StepIntentBinder.IntentLine(
                    StepIntentBinder.IntentKind.ASSERT_VISIBLE, rewrittenClause, intent.testData());
            List<ProvenStep> steps;
            try {
                steps = authoring.authorIntent(
                        tc.tcId(), sub, html, healPng, false, provenSoFar, failedThisIntent);
            } catch (Exception e) {
                skipped++;
                LogsManager.info("COMPOUND_ASSERT: skip — bind error: " + trim(clause, 80));
                continue;
            }
            if (steps == null || steps.isEmpty() || steps.stream().anyMatch(s -> !s.validated())) {
                skipped++;
                LogsManager.info("COMPOUND_ASSERT: skip — could not bind: " + trim(clause, 100));
                continue;
            }
            bound++;
            TcOutcome out = execution.execute(tc.tcId(), steps, evidence);
            if (out.status() == TcStatus.PASSED) {
                passed++;
                provenCombined.addAll(out.provenSteps());
                PostActionSettle.afterSteps(driverFactory.get(), out.provenSteps());
            } else {
                String msg = trim(clause, 70) + ": " + trim(out.failureReason(), 120);
                executeFails.add(msg);
                LogsManager.info("COMPOUND_ASSERT: fail — " + msg);
                if (out.evidenceDir() != null) {
                    evidenceDir = out.evidenceDir().toString();
                }
            }
        }
        if (bound == 0) {
            return StepAttempt.fail(
                    "COMPOUND_ASSERT: no sub-clause could be bound (" + clauses.size() + " parts)",
                    provenCombined, evidenceDir, retriesUsed, healTier, "");
        }
        if (!executeFails.isEmpty() || skipped > 0) {
            if (skipped > 0) executeFails.add(skipped + " required clause(s) could not be bound");
            return StepAttempt.fail(
                    "COMPOUND_ASSERT failed: " + String.join("; ", executeFails),
                    provenCombined, evidenceDir, retriesUsed, healTier, "");
        }
        LogsManager.info("COMPOUND_ASSERT: passed " + passed + "/" + clauses.size()
                + " (bound=" + bound + ", skipped=" + skipped + ")");
        List<ProvenStep> merged = withRecoveredPrefix(recoveredNav, recoveredActions, provenCombined);
        if (retryAfterPageRecovery(merged, tc, baseUrl, intent, driverFactory, recoveredThisIntent)) {
            return StepAttempt.fail(
                    "COMPOUND_ASSERT passed sub-clauses but page recovery required",
                    merged, evidenceDir, retriesUsed, healTier, "");
        }
        String skipNote = skipped > 0
                ? "COMPOUND_ASSERT: skipped " + skipped + " unbound clause(s)"
                : "";
        return settledSuccess(
                merged, retriesUsed, mergeHealTier(healTier, "compound-assert-soft"), skipNote,
                driverFactory, intent.text());
    }

    /** Nested-hook upgrade for a locator recalled before authorIntent. */
    static ProvenStep rewriteRememberedLocator(ProvenStep remembered, String liveHtml) {
        if (remembered == null) {
            return null;
        }
        return NestedPreferredHook.rewrite(remembered, liveHtml);
    }

    private String rememberedLiveHtml(WebDriverFactory driverFactory) {
        try {
            return HtmlSlimmer.slim(PageSnapshot.html(driverFactory.get()), proveHtmlBudget());
        } catch (RuntimeException e) {
            LogsManager.info("LOCATOR_MEMORY: nested-hook rewrite skipped — " + e.getMessage());
            return "";
        }
    }

    private boolean mayUseLocatorMemory() {
        return precisionBindService == null || precisionReuseLocatorMemory;
    }

    private PrecisionBatchStepAttempt attemptPrecisionBatchFromMemory(
            ManualTestCase tc,
            ConversionJobRequest request,
            List<StepIntentBinder.IntentLine> batchSlice,
            int intentIndex,
            TcExecutionService execution,
            Path evidence,
            WebDriverFactory driverFactory,
            List<ProvenStep> provenSoFar
    ) {
        String baseUrl = request == null ? "" : request.baseUrl();
        String memoryHost = hostOf(currentUrl(driverFactory));
        if (memoryHost.isBlank()) {
            memoryHost = hostOf(baseUrl);
        }
        String pagePath = pathOf(currentUrl(driverFactory));
        String liveHtml = rememberedLiveHtml(driverFactory);
        List<ProvenStep> allSteps = new ArrayList<>();
        List<List<ProvenStep>> perIntent = new ArrayList<>();
        for (int i = 0; i < batchSlice.size(); i++) {
            StepIntentBinder.IntentLine intent = batchSlice.get(i);
            Optional<ProvenStep> remembered = locatorMemory.recall(memoryHost, pagePath, intent, tc.tcId());
            if (remembered.isEmpty()) {
                logLocatorMemorySkip(locatorMemorySkipContext(
                        "precision batch — no saved locator for step " + (i + 1) + "/"
                                + batchSlice.size(),
                        memoryHost, pagePath, intent)
                        + " (falling back to Cursor batch)");
                if (!perIntent.isEmpty()) break;
                return null;
            }
            if (StepIntentBinder.spendsMustAvoidPriorFills(intent)
                    && StepIntentBinder.isSpentLocator(remembered.get(), java.util.stream.Stream.concat(provenSoFar.stream(), allSteps.stream()).toList())) {
                logLocatorMemorySkip("precision batch — spent locator for "
                        + trim(intent.text(), 80) + " (" + locatorMemorySkipContext(
                        "", memoryHost, pagePath, intent) + ") (falling back to Cursor batch)");
                if (!perIntent.isEmpty()) break;
                return null;
            }
            ProvenStep ready = rewriteRememberedLocator(
                    withInventedValue(remembered.get(), intent), liveHtml);
            if (refusesSubmitNavigation(intent, List.of(ready))) {
                logLocatorMemorySkip("precision batch — remembered click is not a submit control for "
                        + trim(intent.text(), 80) + " (falling back to Cursor batch)");
                if (!perIntent.isEmpty()) break;
                return null;
            }
            perIntent.add(List.of(ready));
            allSteps.add(ready);
        }
        if (allSteps.isEmpty()) {
            logLocatorMemorySkip("precision batch — no steps resolved from memory "
                    + "(falling back to Cursor batch)");
            return null;
        }
        LogsManager.info("LOCATOR_MEMORY: precision batch " + allSteps.size() + " steps from memory");
        PrecisionPageGuard guard = new PrecisionPageGuard(driverFactory.get(), perIntent);
        return executePrecisionBound(tc.tcId(), perIntent, execution, evidence,
                "locator-memory", cancelCheck, () -> currentUrl(driverFactory),
                    steps -> PostActionSettle.afterSteps(driverFactory.get(), steps), guard::valid);
    }

    // Once execution starts, never fall back to replaying the original batch.
    static PrecisionBatchStepAttempt executePrecisionBound(String tcId,
            List<List<ProvenStep>> perIntent, TcExecutionService execution, Path evidence,
            String tier, BooleanSupplier cancelled, java.util.function.Supplier<String> pageState) {
        return executePrecisionBound(tcId, perIntent, execution, evidence, tier, cancelled, pageState, PostActionSettle::afterAction);
    }

    static PrecisionBatchStepAttempt executePrecisionBound(String tcId,
            List<List<ProvenStep>> perIntent, TcExecutionService execution, Path evidence,
            String tier, BooleanSupplier cancelled, java.util.function.Supplier<String> pageState, Runnable settle) {
        return executePrecisionBound(tcId, perIntent, execution, evidence, tier, cancelled, pageState, settle, steps -> true);
    }

    static PrecisionBatchStepAttempt executePrecisionBound(String tcId,
            List<List<ProvenStep>> perIntent, TcExecutionService execution, Path evidence,
            String tier, BooleanSupplier cancelled, java.util.function.Supplier<String> pageState, Runnable settle,
            java.util.function.Predicate<List<ProvenStep>> targetsValid) {
        return executePrecisionBound(tcId, perIntent, execution, evidence, tier, cancelled, pageState,
                steps -> settle.run(), targetsValid);
    }

    static PrecisionBatchStepAttempt executePrecisionBound(String tcId,
            List<List<ProvenStep>> perIntent, TcExecutionService execution, Path evidence,
            String tier, BooleanSupplier cancelled, java.util.function.Supplier<String> pageState,
            java.util.function.Consumer<List<ProvenStep>> settle,
            java.util.function.Predicate<List<ProvenStep>> targetsValid) {
        List<ProvenStep> proven = new ArrayList<>();
        int consumed = 0;
        try {
            String initialState = pageState.get();
            for (List<ProvenStep> steps : perIntent) {
                JobCancelSupport.checkCancelled(cancelled);
                if (!targetsValid.test(steps)) {
                    LogsManager.info("PRECISION_BATCH: remaining targets changed; consumed=" + consumed);
                    if (consumed == 0) return null;
                    break;
                }
                TcOutcome outcome = execution.execute(tcId, steps, evidence);
                proven.addAll(outcome.provenSteps());
                consumed++;
                if (outcome.status() != TcStatus.PASSED) {
                    return new PrecisionBatchStepAttempt(true, consumed,
                            StepAttempt.fail("PRECISION_BATCH_EXECUTION_FAILED: " + outcome.failureReason(),
                                    proven, evidence.toString(), 0, tier, ""), perIntent);
                }
                settle.accept(steps);
                JobCancelSupport.checkCancelled(cancelled);
                if (!java.util.Objects.equals(initialState, pageState.get())) break;
            }
            return new PrecisionBatchStepAttempt(true, consumed,
                    StepAttempt.success(proven, 0, tier, ""), perIntent);
        } catch (Exception e) {
            return new PrecisionBatchStepAttempt(true, consumed,
                    StepAttempt.fail("PRECISION_BATCH_INTERRUPTED: " + e.getClass().getSimpleName(),
                            proven, evidence.toString(), 0, tier, ""), perIntent);
        }
    }

    private void logPrecisionLocatorMemoryDisabledOnce() {
        if (precisionLocatorMemoryDisabledLogged || precisionBindService == null
                || precisionReuseLocatorMemory) {
            return;
        }
        precisionLocatorMemoryDisabledLogged = true;
        logLocatorMemorySkip("disabled for Precision (enable \"Use known-good locators first\" "
                + "in project settings, then queue a new job)");
    }

    private static void logLocatorMemorySkip(String detail) {
        LogsManager.info("LOCATOR_MEMORY: skip — " + detail);
    }

    private static String locatorMemorySkipContext(
            String prefix,
            String host,
            String pagePath,
            StepIntentBinder.IntentLine intent) {
        String key = DomainLocatorMemory.intentKey(intent);
        String ctx = "host=" + (host == null || host.isBlank() ? "?" : host)
                + " path=" + (pagePath == null || pagePath.isBlank() ? "/" : pagePath)
                + " key=" + (key.isBlank() ? "?" : key);
        if (prefix == null || prefix.isBlank()) {
            return ctx;
        }
        return prefix + " " + ctx;
    }

    private static boolean batchEndedWithNavigationClick(StepIntentBinder.IntentLine intent) {
        if (intent == null) {
            return false;
        }
        if (intent.kind() == StepIntentBinder.IntentKind.CLICK_LOGIN) {
            return true;
        }
        if (intent.kind() != StepIntentBinder.IntentKind.CLICK) {
            return false;
        }
        String t = intent.text().toLowerCase(Locale.ROOT);
        return t.contains("sign in") || t.contains("log in") || t.contains("login")
                || t.contains("verify otp") || t.contains("submit");
    }

    record PrecisionBatchStepAttempt(
            boolean usedBatch,
            int intentsConsumed,
            StepAttempt attempt,
            List<List<ProvenStep>> stepsPerIntent
    ) {
    }

    private PrecisionBindResult bindWithPrecision(
            AuthoringService authoring,
            String tcId,
            StepIntentBinder.IntentLine intent,
            String html,
            Path screenshotPath,
            List<String> priorSteps,
            List<ProvenStep> provenSoFar,
            List<FailedLocator> failedThisIntent,
            byte[] healPng,
            CandidateLivenessProbe probe
    ) throws Exception {
        PrecisionBindOutcome outcome = precisionBindService.bindIntent(
                tcId, intent, html, screenshotPath, priorSteps, provenSoFar, failedThisIntent, probe);
        if (outcome instanceof PrecisionBindOutcome.Bound bound) {
            return new PrecisionBindResult(bound.steps(), bound.tier(), false, "");
        }
        if (outcome instanceof PrecisionBindOutcome.NeedsSolve needs) {
            PrecisionBindOutcome solved = precisionBindService.solveOnce(
                    tcId, intent, needs.candidates(), needs.shortlist(), needs.table(),
                    needs.htmlExcerpt(), screenshotPath, priorSteps);
            if (solved instanceof PrecisionBindOutcome.Bound bound) {
                return new PrecisionBindResult(bound.steps(), bound.tier(), false, "");
            }
            if (solved instanceof PrecisionBindOutcome.FallbackKeel fallback) {
                return keelFallbackBind(authoring, tcId, intent, html, healPng, provenSoFar,
                        failedThisIntent, fallback.reason());
            }
        }
        if (outcome instanceof PrecisionBindOutcome.FallbackKeel fallback) {
            return keelFallbackBind(authoring, tcId, intent, html, healPng, provenSoFar,
                    failedThisIntent, fallback.reason());
        }
        return keelFallbackBind(authoring, tcId, intent, html, healPng, provenSoFar,
                failedThisIntent, "PROVIDER_ERROR");
    }

    private PrecisionBindResult keelFallbackBind(
            AuthoringService authoring,
            String tcId,
            StepIntentBinder.IntentLine intent,
            String html,
            byte[] healPng,
            List<ProvenStep> provenSoFar,
            List<FailedLocator> failedThisIntent,
            String reason
    ) throws Exception {
        LogsManager.info("PRECISION_FALLBACK: Keel bind for " + intent.text() + " (" + reason + ")");
        List<ProvenStep> steps = authoring.authorIntent(
                tcId, intent, html, healPng, false, provenSoFar, failedThisIntent);
        return new PrecisionBindResult(steps, "keel-fallback", true, reason);
    }

    private TcDraft stampPrecision(TcDraft draft) {
        if (draft == null) {
            return null;
        }
        int callsNow = precisionBindService == null ? 0 : precisionBindService.callsUsed();
        return draft.withPrecisionJob(
                precisionTracker.engine.wireValue(),
                precisionTracker.tcCallsUsed(callsNow),
                precisionTracker.tcFallback(),
                precisionTracker.tcFallbackReason());
    }

    private static String hostOf(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        try {
            String host = URI.create(url.trim()).getHost();
            return host == null ? "" : host.toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    private static TcDraft applyVisualAssertion(
            ManualTestCase tc,
            TcDraft draft,
            TcExecutionService execution,
            Path evidence) {
        if (draft == null || draft.status() != TcDraftStatus.PASSED) {
            return draft;
        }
        if (!VisionAssertionGate.shouldRun(tc)) {
            return draft;
        }
        byte[] png = execution == null ? new byte[0] : execution.capturePngBytes();
        VisionGroundingProvider provider = VisionGroundingConfig.createAssertionProvider();
        VisionAssertionResult result;
        try {
            result = VisionAssertionGate.evaluate(tc, png, provider, draft.lastPageUrl());
        } catch (RuntimeException e) {
            LogsManager.error("VISION_ASSERT: " + e.getMessage());
            result = VisionAssertionResult.uncertain(
                    e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
        if (result == null) {
            return draft;
        }
        Path evDir;
        String folder = execution == null
                ? sanitize(tc.tcId())
                : execution.currentEvidenceFolder(tc.tcId());
        if (folder == null || folder.isBlank()) {
            folder = sanitize(tc.tcId());
        }
        evDir = evidence == null ? Path.of("evidence", folder) : evidence.resolve(folder);
        VisionAssertionEvidence.write(evDir, tc.tcId(), tc.visualAssertion(), result, png);
        LogsManager.info("VISION_ASSERT: tc=" + tc.tcId()
                + " status=" + result.status()
                + " conf=" + result.confidence()
                + " provider=" + VisionGroundingConfig.providerId()
                + " model=" + VisionGroundingConfig.model());
        if (!shouldDemoteForVisual(result, tc.visualAssertion(), draft.lastPageUrl())) {
            return draft;
        }
        String reason = "VISUAL_ASSERT:" + result.status().name();
        return new TcDraft(
                draft.tcId(), draft.title(), draft.stepsText(), draft.expectedResult(),
                TcDraftStatus.PARTIAL, draft.provenSteps(), draft.loginSteps(),
                draft.needsLoginBeforeMethod(),
                Math.max(0, draft.provenSteps().size()),
                "visual-assert",
                reason,
                evDir.toString(),
                draft.retryCountOnBlocker(),
                draft.lastPageUrl(),
                draft.healTier(),
                draft.healSkipReason(),
                draft.loginFormUrl());
    }

    /**
     * Fallback only: keep per-step names from {@link TcExecutionService}; fill blanks with
     * {@code url} stem. Never overwrite a real page name with the case-final URL.
     * Legacy "Login"/"LoginForm"/"TargetLogin" map to the URL stem so login form steps share one page.
     */
    private static List<ProvenStep> stampPageNames(List<ProvenStep> steps, String url) {
        String stem = PageClusterer.pageNameFromUrl(url);
        List<ProvenStep> out = new ArrayList<>();
        for (ProvenStep s : steps) {
            String name = PageNameNormalizer.canonical(s.pageName(), stem);
            out.add(s.withPageName(name));
        }
        return out;
    }

    /** Stamp login prelude with login-form URL stem; keep already-resolved page names. */
    private static List<ProvenStep> stampLoginNames(List<ProvenStep> loginSteps, String loginFormUrl) {
        if (loginSteps == null || loginSteps.isEmpty()) {
            return List.of();
        }
        String stem = PageClusterer.pageNameFromUrl(loginFormUrl);
        List<ProvenStep> out = new ArrayList<>();
        for (ProvenStep s : loginSteps) {
            String name = PageNameNormalizer.canonical(s.pageName(), stem);
            out.add(s.withPageName(name));
        }
        return out;
    }

    private static List<ProvenStep> stampLoginNames(List<ProvenStep> loginSteps) {
        return stampLoginNames(loginSteps, "");
    }

    private static TcDraft todoDraft(
            ManualTestCase tc,
            List<ProvenStep> steps,
            boolean needsLogin,
            List<ProvenStep> loginSteps,
            String reason,
            String evidenceDir,
            int retries,
            String url
    ) {
        return new TcDraft(
                tc.tcId(), tc.title(), tc.steps(), tc.expectedResult(),
                TcDraftStatus.TODO,
                steps == null ? List.of() : steps,
                loginSteps == null ? List.of() : loginSteps,
                needsLogin, 0, "", reason, evidenceDir, retries, url);
    }

    /** Replace job username/password literals with ${TARGET_*} for IR and emit. */
    static List<ProvenStep> scrubSecrets(List<ProvenStep> steps, ConversionJobRequest request) {
        if (steps == null || steps.isEmpty() || request == null) {
            return steps == null ? List.of() : steps;
        }
        String user = request.username() == null ? "" : request.username();
        String pass = request.password() == null ? "" : request.password();
        if (user.isBlank() && (pass == null || pass.isEmpty())) {
            return steps;
        }
        List<ProvenStep> out = new ArrayList<>();
        for (ProvenStep s : steps) {
            String v = s.value() == null ? "" : s.value();
            boolean secret = s.secret();
            if (!user.isBlank() && user.equals(v)) {
                v = "${TARGET_USERNAME}";
            } else if (pass != null && !pass.isEmpty() && pass.equals(v)) {
                v = "${TARGET_PASSWORD}";
                secret = true; // r2: value comes from a credential
            }
            secret = secret || delivery.ir.SecretStep.isSecretValue(v, java.util.List.of());
            out.add(new ProvenStep(
                    s.tcId(), s.pageName(), s.actionType(), s.action(),
                    s.locatorStrategy(), s.locatorValue(), v,
                    s.assertionType(), s.assertionExpected(), s.validated(), s.rationale(),
                    s.screenshotRelPath(), s.locatorCandidates(), s.dropCandidates(), s.sideEffects(), secret));
        }
        return out;
    }

    static TcDraft scrubDraftSecrets(TcDraft draft, ConversionJobRequest request) {
        if (draft == null || request == null) {
            return draft;
        }
        return new TcDraft(
                draft.tcId(), draft.title(), draft.stepsText(), draft.expectedResult(),
                draft.status(),
                scrubSecrets(draft.provenSteps(), request),
                scrubSecrets(draft.loginSteps(), request),
                draft.needsLoginBeforeMethod(),
                draft.blockerStepIndex(), draft.blockerIntent(), draft.failureReason(),
                draft.evidenceDir(), draft.retryCountOnBlocker(), draft.lastPageUrl(),
                draft.healTier(), draft.healSkipReason(), draft.loginFormUrl());
    }

    /** Skip a leaf when a Call-before TC in this job did not pass. */
    static boolean callBeforeBlocked(ManualTestCase tc, Set<String> failedIds) {
        if (tc == null || failedIds == null || failedIds.isEmpty()) {
            return false;
        }
        for (String beforeId : CallBefore.parse(tc.callBefore())) {
            if (failedIds.contains(beforeId)) {
                return true;
            }
        }
        return false;
    }

    /** Wipe/restart browser at each leaf-chain start (Call-before → leaf keep the session). */
    static boolean wipeSessionBeforeTc(int tcIndex, List<ManualTestCase> allCases) {
        List<Boolean> flags = CallBeforeExpander.freshSessionAt(allCases);
        if (flags == null || flags.isEmpty()) {
            return tcIndex <= 1;
        }
        int i = tcIndex - 1;
        if (i < 0 || i >= flags.size()) {
            return tcIndex <= 1;
        }
        return Boolean.TRUE.equals(flags.get(i));
    }

    private static boolean wipeAt(int tcIndex, List<Boolean> freshSession) {
        if (freshSession == null || freshSession.isEmpty()) {
            return tcIndex <= 1;
        }
        int i = tcIndex - 1;
        if (i < 0 || i >= freshSession.size()) {
            return tcIndex <= 1;
        }
        return Boolean.TRUE.equals(freshSession.get(i));
    }

    /** Excel open paths that would destroy an authenticated portal session. */
    static boolean isAuthOpenPath(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        String p = path.trim().toLowerCase(Locale.ROOT);
        return p.contains("/login") || p.contains("/signin") || p.contains("/sign-in")
                || p.contains("/auth") || p.equals("login") || p.equals("signin");
    }

    private static void ensureBrowserAlive(WebDriverFactory driverFactory) {
        org.openqa.selenium.WebDriver driver = driverFactory.get();
        try {
            if (driver.getWindowHandles() == null || driver.getWindowHandles().isEmpty()) {
                throw new IllegalStateException("no such window: no window handles");
            }
            driver.getCurrentUrl();
        } catch (RuntimeException e) {
            if (DeadBrowserSession.isDead(e)) {
                throw e;
            }
            // Some drivers throw before first navigation — continue with existing session.
        }
    }

    private static void openFreshPage(WebDriverFactory driverFactory, String baseUrl) {
        org.openqa.selenium.WebDriver driver = driverFactory.get();
        try {
            if (driver.getWindowHandles() == null || driver.getWindowHandles().isEmpty()) {
                throw new IllegalStateException("no such window: no window handles");
            }
        } catch (RuntimeException e) {
            if (DeadBrowserSession.isDead(e)) {
                throw e;
            }
            // Some drivers throw on handles before first navigation — continue.
        }
        driver.manage().deleteAllCookies();
        try {
            org.openqa.selenium.JavascriptExecutor js =
                    (org.openqa.selenium.JavascriptExecutor) driver;
            js.executeScript("window.localStorage.clear(); window.sessionStorage.clear();");
        } catch (Exception ignored) {
            // Some drivers reject storage access on blank pages — navigate then clear again
        }
        WorkerNetworkGuard guard = new WorkerNetworkGuard(TargetNetworkPolicy.forJob(baseUrl));
        guard.requireNavigate(baseUrl);
        driver.get(baseUrl);
        try {
            guard.requireNavigate(driver.getCurrentUrl());
        } catch (TargetBlockedException redirected) {
            throw redirected;
        }
        LoginFormNavigator.waitForDocumentReady(driver, java.time.Duration.ofSeconds(12));
        try {
            org.openqa.selenium.JavascriptExecutor js =
                    (org.openqa.selenium.JavascriptExecutor) driver;
            js.executeScript("window.localStorage.clear(); window.sessionStorage.clear();");
        } catch (Exception ignored) {
            // Non-fatal: site may not use web storage
        }
    }

    private static String currentUrl(WebDriverFactory driverFactory) {
        try {
            return driverFactory.get().getCurrentUrl();
        } catch (Exception e) {
            return "";
        }
    }

    private static List<String> priorStepSummaries(List<ProvenStep> steps) {
        if (steps == null || steps.isEmpty()) {
            return List.of();
        }
        int from = Math.max(0, steps.size() - 12);
        return steps.subList(from, steps.size()).stream()
                .map(step -> {
                    String summary = safe(step.action()) + " "
                            + safe(step.locatorStrategy()) + " "
                            + safe(step.locatorValue());
                    if (step.value() != null && !step.value().isBlank()) {
                        summary += " = " + step.value();
                    }
                    return trim(summary.trim(), 240);
                })
                .toList();
    }

    /** invent > vision > cursor > ollama > none (riskiest tier wins the draft label) */
    private static String mergeHealTier(String a, String b) {
        return healTierRank(b) > healTierRank(a) ? (b == null ? "none" : b) : (a == null ? "none" : a);
    }

    private static int healTierRank(String tier) {
        if (tier == null) {
            return 0;
        }
        return switch (tier.toLowerCase()) {
            case "recovery" -> 6;
            case "invent" -> 5;
            case "vision" -> 4;
            case "cursor", "groundrank", "solve" -> 3;
            case "ollama" -> 2;
            case "retry" -> 1;
            default -> 0;
        };
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String trim(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    /** Sleep + document-ready settle so the next intent re-snapshots a settled page. */
    private static StepAttempt settledSuccess(
            List<ProvenStep> steps, int retries, String healTier, String skip,
            WebDriverFactory driverFactory, String intentText) {
        org.openqa.selenium.WebDriver driver = null;
        try {
            driver = driverFactory == null ? null : driverFactory.get();
        } catch (RuntimeException e) {
            driver = null;
        }
        PostActionSettle.afterSteps(driver, steps);
        return StepAttempt.success(steps, retries, healTier, skip);
    }

    record StepAttempt(
            boolean ok,
            List<ProvenStep> steps,
            List<ProvenStep> partialSteps,
            String reason,
            String evidenceDir,
            int retriesUsed,
            String healTier,
            String healSkipReason
    ) {
        static StepAttempt success(List<ProvenStep> steps, int retries, String healTier, String skip) {
            return new StepAttempt(true, steps, List.of(), "", "", retries,
                    healTier == null ? "none" : healTier,
                    skip == null ? "" : skip);
        }

        static StepAttempt fail(String reason, List<ProvenStep> partial, String evidence, int retries,
                                String healTier, String skip) {
            return new StepAttempt(false, List.of(),
                    partial == null ? List.of() : partial, reason, evidence, retries,
                    healTier == null ? "none" : healTier,
                    skip == null ? "" : skip);
        }
    }

    /** AE-D9: A11 observation budget for ProvePhase HTML excerpts (bind intent). */
    public static int proveHtmlBudget() {
        return ObservationBudget.forProve();
    }
}
