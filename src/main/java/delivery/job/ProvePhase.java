package delivery.job;

import delivery.authoring.AuthoringService;
import delivery.authoring.LocalLlmClient;
import delivery.authoring.LocatorValidator;
import delivery.authoring.LoginStepDetector;
import delivery.authoring.RequiredControlFiller;
import delivery.authoring.StepIntentBinder;
import delivery.authoring.DummyValueInventor;
import delivery.codegen.PageClusterer;
import delivery.codegen.PageNameNormalizer;
import delivery.codegen.ProvenStep;
import delivery.excel.ManualTestCase;
import delivery.store.DomainLocatorMemory;
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
import java.util.List;
import java.util.Locale;
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

    private final JobProgressTracker progress;
    private DomainLocatorMemory locatorMemory = new DomainLocatorMemory();
    private Path locatorMemoryFile;
    /** When set (Execute jobs), mirror each draft to durable execute-runs storage after write. */
    private Path mirrorRoot;
    private BooleanSupplier cancelCheck = () -> false;

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
        Path evidence = workDir.resolve("evidence");
        LocalLlmClient llm = new LocalLlmClient(request.localLlmBaseUrl(), request.localLlmModel());
        AuthoringService authoring = new AuthoringService(llm, new LocatorValidator());
        WebDriverFactory driverFactory = new WebDriverFactory();
        HealCascade healCascade = VisionGroundingConfig.enabled()
                ? new HealCascade(
                        authoring,
                        new CursorHealClient(),
                        VisionGroundingConfig.createProvider(),
                        () -> new SeleniumGroundingBrowser(driverFactory.get()))
                : new HealCascade(authoring, new CursorHealClient());
        TcExecutionService execution = new TcExecutionService(driverFactory);
        JobLoginService jobLogin = new JobLoginService();
        boolean jobHasCredentials = request.username() != null && !request.username().isBlank()
                && request.password() != null;
        // null = author every TC (NEW); non-null = UPDATE author set (others REUSED)
        List<TcDraft> out = new ArrayList<>();
        locatorMemory = new DomainLocatorMemory();
        locatorMemoryFile = memoryFile(request);
        locatorMemory.load(locatorMemoryFile);

        try {
            int index = 0;
            for (ManualTestCase tc : allCases) {
                JobCancelSupport.checkCancelled(cancelCheck);
                index++;
                int jobTotal = progress.effectiveTotal(allCases.size());
                progress.update(index, jobTotal, "Phase1 prove " + tc.tcId());
                if (tcIdsToAuthor != null && !tcIdsToAuthor.contains(tc.tcId())) {
                    TcDraft reused = new TcDraft(
                            tc.tcId(), tc.title(), tc.steps(), tc.expectedResult(),
                            TcDraftStatus.REUSED, List.of(), List.of(), false,
                            -1, "", "reused", "", 0, "");
                    drafts.write(reused);
                    mirrorDraft(workDir, reused.tcId());
                    out.add(reused);
                    progress.recordOutcome(TcDraftStatus.REUSED);
                    progress.update(index, jobTotal,
                            "Phase1 reused " + tc.tcId()
                                    + " — passed " + progress.passed()
                                    + ", blocked " + progress.todo());
                    continue;
                }

                // Fresh browser per TC so in-app session state cannot leak across cases
                if (index > 1) {
                    try {
                        driverFactory.restart();
                    } catch (Exception e) {
                        LogsManager.info("Driver restart failed before " + tc.tcId() + ": " + e.getMessage());
                    }
                }

                TcDraft draft = proveOne(tc, request, authoring, healCascade, execution, jobLogin,
                        driverFactory, evidence, jobHasCredentials, index, jobTotal);
                draft = scrubDraftSecrets(draft, request);
                drafts.write(draft);
                mirrorDraft(workDir, draft.tcId());
                out.add(draft);
                saveLocatorMemory();
                progress.recordOutcome(draft.status());
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
            int tcTotal
    ) {
        try {
            openFreshPage(driverFactory, request.baseUrl());
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

        execution.beginTc();
        healCascade.setExcelOpenPath(StepIntentBinder.firstOpenPath(
                tc.preconditions(), tc.steps()));

        // Open Excel path first so gated targets (redirect → login form) are visible before policy.
        LoginFormNavigator.navigateExcelOpenPathIfPresent(driverFactory, request.baseUrl(), tc);
        boolean formVisible = LoginFormNavigator.pageHasLoginForm(driverFactory);
        boolean needsLogin = LoginStepDetector.needsAuthenticatedSession(
                tc, jobHasCredentials, formVisible);

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
                String loginHtml = HtmlSlimmer.slim(PageSnapshot.html(driverFactory.get()), 50000);
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
                List<ProvenStep> loginForExec = resolveLoginSecrets(loginSteps, request);
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

        List<ProvenStep> provenAll = new ArrayList<>();
        List<FailedLocator> failedThisTc = new ArrayList<>();
        int intentIndex = 0;
        String maxHealTier = "none";
        String healSkipAccum = "";
        for (StepIntentBinder.IntentLine intent : intents) {
            progress.update(tcIndex, tcTotal,
                    "Phase1 " + tc.tcId() + " step " + (intentIndex + 1) + "/" + intents.size()
                            + ": " + trim(intent.text(), 60));

            StepAttempt attempt = attemptIntentWithRetry(
                    tc, request.baseUrl(), intent, intentIndex, authoring, healCascade, execution, evidence,
                    driverFactory, provenAll, failedThisTc);
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
            rememberSuccessfulLocator(request, driverFactory, tc, intent, attempt.steps());
            provenAll.addAll(attempt.steps());
            intentIndex++;
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
            String html = HtmlSlimmer.slim(PageSnapshot.html(driverFactory.get()), 50000);
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
                String freshHtml = HtmlSlimmer.slim(PageSnapshot.html(driverFactory.get()), 80000);
                byte[] failPng = execution.capturePngBytes();
                Path shot = writeHealScreenshot(evidence, tc.tcId(), 0, failPng);
                CandidateLivenessProbe probe = new SeleniumCandidateLivenessProbe(driverFactory);
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
            String baseUrl,
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
        boolean recoveredThisIntent = false;
        boolean recoveryAttemptedThisIntent = false;
        List<ProvenStep> recoveredNav = new ArrayList<>();
        List<ProvenStep> recoveredActions = new ArrayList<>();
        try {
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            if (attempt > 0) {
                retriesUsed = attempt;
                progress.update(progress.current(), progress.total(),
                        "Phase1 " + tc.tcId() + " retry " + attempt + "/" + MAX_RETRIES
                                + " on step " + (intentIndex + 1));
            }
            try {
                String html = HtmlSlimmer.slim(PageSnapshot.html(driverFactory.get()), 80000);
                List<ProvenStep> autoFillSteps = List.of();
                if (intent.kind() == StepIntentBinder.IntentKind.CLICK) {
                    List<StepIntentBinder.IntentLine> typeIntents = StepIntentBinder.parseIntents(tc).stream()
                            .filter(i -> i.kind() == StepIntentBinder.IntentKind.TYPE_FIELD
                                    || i.kind() == StepIntentBinder.IntentKind.TYPE_USER
                                    || i.kind() == StepIntentBinder.IntentKind.TYPE_PASS)
                            .toList();
                    List<ProvenStep> autoFills = RequiredControlFiller.planFillsBeforeClick(
                            html, tc.tcId(), intent.text(), typeIntents);
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
                        html = HtmlSlimmer.slim(PageSnapshot.html(driverFactory.get()), 80000);
                    }
                }

                String memoryHost = hostOf(currentUrl(driverFactory));
                if (memoryHost.isBlank()) {
                    memoryHost = hostOf(baseUrl);
                }
                String excelPath = StepIntentBinder.firstOpenPath(tc.preconditions(), tc.steps());
                Optional<ProvenStep> remembered = locatorMemory.recall(
                        memoryHost, excelPath, intent, tc.tcId());
                if (remembered.isPresent()) {
                    ProvenStep ready = withInventedValue(remembered.get(), intent);
                    if (refusesSubmitNavigation(intent, List.of(ready))) {
                        locatorMemory.forget(memoryHost, excelPath, intent);
                        LogsManager.info("SUBMIT_REFUSE: dropped remembered non-submit click for "
                                + intent.text());
                    } else {
                        TcOutcome memOutcome = execution.execute(tc.tcId(), List.of(ready), evidence);
                        if (memOutcome.status() == TcStatus.PASSED) {
                            List<ProvenStep> combined = new ArrayList<>(autoFillSteps);
                            combined.addAll(memOutcome.provenSteps());
                            if (retryAfterPageRecovery(
                                    combined, tc, baseUrl, intent, driverFactory, recoveredThisIntent)) {
                                locatorMemory.forget(memoryHost, excelPath, intent);
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
                        locatorMemory.forget(memoryHost, excelPath, intent);
                        LogsManager.info("LOCATOR_MEMORY: dropped after execute fail for " + intent.text());
                    }
                }

                byte[] healPng = execution.capturePngBytes();
                // Bind only — heal cascade owns Ollama + Cursor after bind/execute failure
                List<ProvenStep> stepBatch = authoring.authorIntent(
                        tc.tcId(), intent, html, healPng, false, provenSoFar, failedThisIntent);
                boolean bindFailed = stepBatch.isEmpty()
                        || stepBatch.stream().anyMatch(s -> !s.validated())
                        || refusesSubmitNavigation(intent, stepBatch);
                boolean ollamaUsed = false;
                boolean cursorUsed = false;
                if (bindFailed && VisionGroundingConfig.enabled()
                        && VisionTriggers.isEligible(intent)
                        && VisionTriggers.isWeakBind(stepBatch)) {
                    java.util.Optional<java.util.List<ProvenStep>> grounded = VisionProveHook.tryLayer15(
                            tc.tcId(), intent, html, healPng, stepBatch, bindFailed,
                            failedThisIntent, authoring,
                            new SeleniumGroundingBrowser(driverFactory.get()),
                            VisionGroundingConfig.createProvider());
                    if (grounded.isPresent()) {
                        if (refusesSubmitNavigation(intent, grounded.get())) {
                            LogsManager.info("SUBMIT_REFUSE: vision grounded a non-submit navigation for "
                                    + intent.text());
                        } else {
                            stepBatch = grounded.get();
                            bindFailed = false;
                            healTier = mergeHealTier(healTier, "vision");
                            LogsManager.info("VISION_GROUND: READY intent=" + intent.text());
                        }
                    }
                }
                if (bindFailed) {
                    if (!recoveredThisIntent
                            && TargetPageRecovery.tryRecover(driverFactory, baseUrl, tc, intent, html)) {
                        recoveredThisIntent = true;
                        lastReason = "Recovered to Excel open-path; retry bind";
                        continue;
                    }
                    lastReason = stepBatch.isEmpty()
                            ? "No DOM candidate for intent " + intent.kind() + ": " + intent.text()
                            : stepBatch.stream().filter(s -> !s.validated()).findFirst()
                            .map(ProvenStep::rationale).orElse("unknown");
                    Path shot = writeHealScreenshot(evidence, tc.tcId(), intentIndex, healPng);
                    HealResult healed = healCascade.heal(
                            tc.tcId(), intent, html, healPng, lastReason, shot, true, priorSteps,
                            true, provenSoFar, failedThisIntent, probe);
                    if (!healed.ok()) {
                        lastPartial = autoFillSteps;
                        lastReason = healed.reason().isBlank() ? lastReason : healed.reason();
                        healSkipReason = lastReason;
                        continue;
                    }
                    RecoveryOutcome recovery = tryRecoveryHeal(
                            healed, tc, execution, evidence, recoveryAttemptedThisIntent, recoveredActions);
                    if (recovery == RecoveryOutcome.RECOVERY_RETRY) {
                        recoveryAttemptedThisIntent = true;
                        healTier = mergeHealTier(healTier, "recovery");
                        continue;
                    }
                    if (recovery == RecoveryOutcome.RECOVERY_FAILED) {
                        recoveryAttemptedThisIntent = true;
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

                    // Cap: Ollama once + Cursor once. Escalate to Cursor after Ollama execute-fail (D3).
                    if (!ollamaUsed && !cursorUsed) {
                        String freshHtml = HtmlSlimmer.slim(PageSnapshot.html(driverFactory.get()), 80000);
                        byte[] failPng = execution.capturePngBytes();
                        Path shot = writeHealScreenshot(evidence, tc.tcId(), intentIndex, failPng);
                        HealResult healed = healCascade.heal(
                                tc.tcId(), intent, freshHtml, failPng, lastReason, shot, true, priorSteps,
                                true, provenSoFar, failedThisIntent, probe);
                        RecoveryOutcome recovery = tryRecoveryHeal(
                                healed, tc, execution, evidence, recoveryAttemptedThisIntent, recoveredActions);
                        if (recovery == RecoveryOutcome.RECOVERY_RETRY) {
                            recoveryAttemptedThisIntent = true;
                            healTier = mergeHealTier(healTier, "recovery");
                            continue;
                        }
                        if (recovery == RecoveryOutcome.RECOVERY_FAILED) {
                            recoveryAttemptedThisIntent = true;
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

                    if (ollamaUsed && !cursorUsed) {
                        String html2 = HtmlSlimmer.slim(PageSnapshot.html(driverFactory.get()), 80000);
                        byte[] png2 = execution.capturePngBytes();
                        Path shot2 = writeHealScreenshot(evidence, tc.tcId(), intentIndex, png2);
                        HealResult cursorHeal = healCascade.heal(
                                tc.tcId(), intent, html2, png2,
                                "re-execute after Ollama heal failed: " + lastReason,
                                shot2, false, priorSteps, true, provenSoFar, failedThisIntent, probe);
                        EscalateHealDecision escalate = decideEscalateHeal(
                                cursorHeal, recoveryAttemptedThisIntent);
                        if (escalate == EscalateHealDecision.RUN_RECOVERY_AND_RETRY_INTENT
                                || escalate == EscalateHealDecision.RECOVERY_ALREADY_ATTEMPTED_FAIL) {
                            RecoveryOutcome recovery = tryRecoveryHeal(
                                    cursorHeal, tc, execution, evidence, recoveryAttemptedThisIntent, recoveredActions);
                            if (recovery == RecoveryOutcome.RECOVERY_RETRY) {
                                recoveryAttemptedThisIntent = true;
                                healTier = mergeHealTier(healTier, "recovery");
                                continue;
                            }
                            recoveryAttemptedThisIntent = true;
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
        return StepAttempt.fail(lastReason, lastPartial, evidenceDir, retriesUsed, healTier, healSkipReason);
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
            String html = HtmlSlimmer.slim(PageSnapshot.html(driverFactory.get()), 80000);
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

    private enum RecoveryOutcome { NOT_RECOVERY, RECOVERY_RETRY, RECOVERY_FAILED }

    /** How Cursor escalate-after-Ollama should treat a heal result (package-visible for tests). */
    enum EscalateHealDecision {
        RUN_RECOVERY_AND_RETRY_INTENT,
        RECOVERY_ALREADY_ATTEMPTED_FAIL,
        EXECUTE_AS_INTENT_FIX,
        HEAL_FAILED
    }

    static boolean isRecoveryTier(HealResult healed) {
        return healed != null && healed.ok() && "recovery".equals(healed.tierUsed());
    }

    /**
     * Recovery-tier heals must never be executed as the original intent batch.
     * Classic cursor/invent fixes use {@link EscalateHealDecision#EXECUTE_AS_INTENT_FIX}.
     */
    static EscalateHealDecision decideEscalateHeal(HealResult cursorHeal, boolean recoveryAlreadyAttempted) {
        if (cursorHeal == null || !cursorHeal.ok()) {
            return EscalateHealDecision.HEAL_FAILED;
        }
        if (isRecoveryTier(cursorHeal)) {
            return recoveryAlreadyAttempted
                    ? EscalateHealDecision.RECOVERY_ALREADY_ATTEMPTED_FAIL
                    : EscalateHealDecision.RUN_RECOVERY_AND_RETRY_INTENT;
        }
        return EscalateHealDecision.EXECUTE_AS_INTENT_FIX;
    }

    private static RecoveryOutcome tryRecoveryHeal(
            HealResult healed,
            ManualTestCase tc,
            TcExecutionService execution,
            Path evidence,
            boolean recoveryAlreadyAttempted,
            List<ProvenStep> recoveredActionsOut
    ) {
        if (!healed.ok() || !"recovery".equals(healed.tierUsed())) {
            return RecoveryOutcome.NOT_RECOVERY;
        }
        if (recoveryAlreadyAttempted) {
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
        PostActionSettle.afterAction();
        LogsManager.info("HEAL_RECOVERY: executed " + healed.steps().size()
                + " steps for " + tc.tcId() + "; retrying intent");
        return RecoveryOutcome.RECOVERY_RETRY;
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
        if (request == null || request.storeRoot() == null || request.projectId() == null
                || request.projectId().isBlank()) {
            return null;
        }
        return new ProjectStore(request.storeRoot(), request.baseUrl())
                .projectRoot(request.projectId())
                .resolve("domain-locator-memory.json");
    }

    private void saveLocatorMemory() {
        locatorMemory.save(locatorMemoryFile);
    }

    private void rememberSuccessfulLocator(
            ConversionJobRequest request,
            WebDriverFactory driverFactory,
            ManualTestCase tc,
            StepIntentBinder.IntentLine intent,
            List<ProvenStep> steps) {
        if (steps == null || steps.isEmpty() || intent == null) {
            return;
        }
        String host = hostOf(currentUrl(driverFactory));
        if (host.isBlank()) {
            host = hostOf(request == null ? "" : request.baseUrl());
        }
        String excelPath = StepIntentBinder.firstOpenPath(
                tc == null ? "" : tc.preconditions(),
                tc == null ? "" : tc.steps());
        locatorMemory.remember(host, excelPath, intent, steps.get(steps.size() - 1));
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
        String value = DummyValueInventor.fromStepOrInvent(
                intent.text(), intent.testData(), "input", inputType, "", intent.text(), intent.text());
        return new ProvenStep(
                step.tcId(), step.pageName(), step.actionType(), step.action(),
                step.locatorStrategy(), step.locatorValue(), value,
                step.assertionType(), step.assertionExpected(), step.validated(),
                step.rationale(), step.screenshotRelPath());
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
        Path evDir = evidence == null
                ? Path.of("evidence", sanitize(tc.tcId()))
                : evidence.resolve(sanitize(tc.tcId()));
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

    /**
     * Resolve ${TARGET_*} placeholders for live Selenium only.
     * Never persist the returned list into IR / codegen.
     */
    private static List<ProvenStep> resolveLoginSecrets(
            List<ProvenStep> loginSteps, ConversionJobRequest request) {
        if (loginSteps == null || loginSteps.isEmpty()) {
            return List.of();
        }
        String user = request.username() == null ? "" : request.username();
        String pass = request.password() == null ? "" : request.password();
        List<ProvenStep> out = new ArrayList<>();
        for (ProvenStep s : loginSteps) {
            String v = s.value() == null ? "" : s.value();
            if ("${TARGET_USERNAME}".equals(v)) {
                v = user;
            } else if ("${TARGET_PASSWORD}".equals(v)) {
                v = pass;
            }
            out.add(new ProvenStep(
                    s.tcId(), s.pageName(), s.actionType(), s.action(),
                    s.locatorStrategy(), s.locatorValue(), v,
                    s.assertionType(), s.assertionExpected(), s.validated(), s.rationale(),
                    s.screenshotRelPath()));
        }
        return out;
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
            if (!user.isBlank() && user.equals(v)) {
                v = "${TARGET_USERNAME}";
            } else if (pass != null && !pass.isEmpty() && pass.equals(v)) {
                v = "${TARGET_PASSWORD}";
            }
            out.add(new ProvenStep(
                    s.tcId(), s.pageName(), s.actionType(), s.action(),
                    s.locatorStrategy(), s.locatorValue(), v,
                    s.assertionType(), s.assertionExpected(), s.validated(), s.rationale(),
                    s.screenshotRelPath()));
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
        driver.get(baseUrl);
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
            case "cursor" -> 3;
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
        PostActionSettle.afterNavigation(driver, null, intentText);
        return StepAttempt.success(steps, retries, healTier, skip);
    }

    private record StepAttempt(
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
}
