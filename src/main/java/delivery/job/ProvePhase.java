package delivery.job;

import delivery.authoring.AuthoringService;
import delivery.authoring.LocalLlmClient;
import delivery.authoring.LocatorValidator;
import delivery.authoring.LoginStepDetector;
import delivery.authoring.RequiredControlFiller;
import delivery.authoring.StepIntentBinder;
import delivery.codegen.PageClusterer;
import delivery.codegen.ProvenStep;
import delivery.excel.ManualTestCase;
import delivery.heal.CursorHealClient;
import delivery.heal.HealCascade;
import delivery.heal.HealResult;
import delivery.ir.TcDraft;
import delivery.ir.TcDraftStatus;
import delivery.ir.TcDraftStore;
import drivers.WebDriverFactory;
import parsingLayer.HtmlSlimmer;
import utils.LogsManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Phase 1: parse/execute/validate each TC against the live browser, persist IR drafts.
 * Retries a failed bind/execute up to {@link #MAX_RETRIES} times with fresh DOM.
 */
public class ProvePhase {
    /** Extra attempts after the first failure (plan: retry 2 times max). */
    public static final int MAX_RETRIES = 2;

    private final JobProgressTracker progress;

    public ProvePhase(JobProgressTracker progress) {
        this.progress = progress == null ? new JobProgressTracker() : progress;
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
        HealCascade healCascade = new HealCascade(authoring, new CursorHealClient());
        WebDriverFactory driverFactory = new WebDriverFactory();
        TcExecutionService execution = new TcExecutionService(driverFactory);
        JobLoginService jobLogin = new JobLoginService();
        boolean jobHasCredentials = request.username() != null && !request.username().isBlank()
                && request.password() != null;
        // null = author every TC (NEW); non-null = UPDATE author set (others REUSED)
        List<TcDraft> out = new ArrayList<>();

        try {
            int index = 0;
            for (ManualTestCase tc : allCases) {
                index++;
                progress.update(index, allCases.size(), "Phase1 prove " + tc.tcId());
                if (tcIdsToAuthor != null && !tcIdsToAuthor.contains(tc.tcId())) {
                    TcDraft reused = new TcDraft(
                            tc.tcId(), tc.title(), tc.steps(), tc.expectedResult(),
                            TcDraftStatus.REUSED, List.of(), List.of(), false,
                            -1, "", "reused", "", 0, "");
                    drafts.write(reused);
                    out.add(reused);
                    progress.recordOutcome(TcDraftStatus.REUSED);
                    progress.update(index, allCases.size(),
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
                        driverFactory, evidence, jobHasCredentials, index, allCases.size());
                draft = scrubDraftSecrets(draft, request);
                drafts.write(draft);
                out.add(draft);
                progress.recordOutcome(draft.status());
                progress.update(index, allCases.size(),
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
            driverFactory.get().manage().deleteAllCookies();
            try {
                org.openqa.selenium.JavascriptExecutor js =
                        (org.openqa.selenium.JavascriptExecutor) driverFactory.get();
                js.executeScript("window.localStorage.clear(); window.sessionStorage.clear();");
            } catch (Exception ignored) {
                // Some drivers reject storage access on blank pages — navigate then clear again
            }
            driverFactory.get().get(request.baseUrl());
            try {
                org.openqa.selenium.JavascriptExecutor js =
                        (org.openqa.selenium.JavascriptExecutor) driverFactory.get();
                js.executeScript("window.localStorage.clear(); window.sessionStorage.clear();");
            } catch (Exception ignored) {
                // Non-fatal: site may not use web storage
            }
        } catch (Exception e) {
            return todoDraft(tc, List.of(), false, List.of(),
                    "Browser navigate failed: " + e.getMessage(), "", 0, "");
        }

        execution.beginTc();

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
                String loginHtml = HtmlSlimmer.slim(driverFactory.get().getPageSource(), 50000);
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
        boolean negativeLogin = LoginStepDetector.isLoginFailureCase(tc);
        List<StepIntentBinder.IntentLine> intents = StepIntentBinder.parseIntents(bodyTc).stream()
                .filter(i -> {
                    if (negativeLogin) {
                        // Negative login must still type credentials and click submit/login
                        return true;
                    }
                    return i.kind() != StepIntentBinder.IntentKind.TYPE_USER
                            && i.kind() != StepIntentBinder.IntentKind.TYPE_PASS
                            && i.kind() != StepIntentBinder.IntentKind.CLICK_LOGIN;
                })
                .filter(i -> !StepIntentBinder.isNavigationalLoginOrUrl(
                        i.text() == null ? "" : i.text().toLowerCase()))
                .filter(i -> !StepIntentBinder.isOpenPathNavigateIntent(i.text()))
                .toList();

        if (intents.isEmpty()) {
            return proveBatchFallback(tc, bodyTc, authoring, healCascade, execution, evidence,
                    needsLogin, loginSteps, loginFormUrl, driverFactory, tcIndex, tcTotal);
        }

        List<ProvenStep> provenAll = new ArrayList<>();
        int intentIndex = 0;
        String maxHealTier = "none";
        String healSkipAccum = "";
        for (StepIntentBinder.IntentLine intent : intents) {
            progress.update(tcIndex, tcTotal,
                    "Phase1 " + tc.tcId() + " step " + (intentIndex + 1) + "/" + intents.size()
                            + ": " + trim(intent.text(), 60));

            StepAttempt attempt = attemptIntentWithRetry(
                    tc, intent, intentIndex, authoring, healCascade, execution, evidence,
                    driverFactory, provenAll);
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
            provenAll.addAll(attempt.steps());
            intentIndex++;
        }

        String url = currentUrl(driverFactory);
        provenAll = stampPageNames(provenAll, url);
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
        return new TcDraft(
                tc.tcId(), tc.title(), tc.steps(), tc.expectedResult(),
                TcDraftStatus.PASSED, provenAll, loginSteps, needsLogin,
                -1, "", "", "", 0, url)
                .withHeal(maxHealTier, healSkipAccum)
                .withLoginFormUrl(loginFormUrl);
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
            String html = HtmlSlimmer.slim(driverFactory.get().getPageSource(), 50000);
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
                String freshHtml = HtmlSlimmer.slim(driverFactory.get().getPageSource(), 80000);
                byte[] failPng = execution.capturePngBytes();
                Path shot = writeHealScreenshot(evidence, tc.tcId(), 0, failPng);
                HealResult healed = healCascade.heal(
                        tc.tcId(), healIntent, freshHtml, failPng,
                        outcome.failureReason(), shot, true,
                        priorStepSummaries(outcome.provenSteps()), false);
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
            return new TcDraft(
                    tc.tcId(), tc.title(), tc.steps(), tc.expectedResult(),
                    TcDraftStatus.PASSED, stamped, loginSteps, needsLogin,
                    -1, "", "", "", 0, url)
                    .withHeal(healTier, healSkip)
                    .withLoginFormUrl(loginFormUrl);
        } catch (Exception e) {
            return todoDraft(tc, List.of(), needsLogin, loginSteps,
                    "Batch author error: " + e.getMessage(), "", 0, currentUrl(driverFactory))
                    .withLoginFormUrl(loginFormUrl);
        }
    }

    private StepAttempt attemptIntentWithRetry(
            ManualTestCase tc,
            StepIntentBinder.IntentLine intent,
            int intentIndex,
            AuthoringService authoring,
            HealCascade healCascade,
            TcExecutionService execution,
            Path evidence,
            WebDriverFactory driverFactory,
            List<ProvenStep> provenSoFar
    ) {
        String lastReason = "";
        String evidenceDir = "";
        List<ProvenStep> lastPartial = List.of();
        int retriesUsed = 0;
        String healTier = "none";
        String healSkipReason = "";
        List<String> priorSteps = priorStepSummaries(provenSoFar);
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            if (attempt > 0) {
                retriesUsed = attempt;
                progress.update(progress.current(), progress.total(),
                        "Phase1 " + tc.tcId() + " retry " + attempt + "/" + MAX_RETRIES
                                + " on step " + (intentIndex + 1));
            }
            try {
                String html = HtmlSlimmer.slim(driverFactory.get().getPageSource(), 80000);
                List<ProvenStep> autoFillSteps = List.of();
                if (intent.kind() == StepIntentBinder.IntentKind.CLICK) {
                    List<ProvenStep> autoFills = RequiredControlFiller.planFillsBeforeClick(
                            html, tc.tcId(), intent.text());
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
                        html = HtmlSlimmer.slim(driverFactory.get().getPageSource(), 80000);
                    }
                }

                byte[] healPng = execution.capturePngBytes();
                // Bind only — heal cascade owns Ollama + Cursor after bind/execute failure
                List<ProvenStep> stepBatch = authoring.authorIntent(
                        tc.tcId(), intent, html, healPng, false);
                boolean bindFailed = stepBatch.isEmpty()
                        || stepBatch.stream().anyMatch(s -> !s.validated());
                boolean ollamaUsed = false;
                boolean cursorUsed = false;
                if (bindFailed) {
                    lastReason = stepBatch.isEmpty()
                            ? "No DOM candidate for intent " + intent.kind() + ": " + intent.text()
                            : stepBatch.stream().filter(s -> !s.validated()).findFirst()
                            .map(ProvenStep::rationale).orElse("unknown");
                    Path shot = writeHealScreenshot(evidence, tc.tcId(), intentIndex, healPng);
                    HealResult healed = healCascade.heal(
                            tc.tcId(), intent, html, healPng, lastReason, shot, true, priorSteps);
                    if (!healed.ok()) {
                        lastPartial = autoFillSteps;
                        lastReason = healed.reason().isBlank() ? lastReason : healed.reason();
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

                TcOutcome stepOutcome = execution.execute(tc.tcId(), stepBatch, evidence);
                if (stepOutcome.status() != TcStatus.PASSED) {
                    lastReason = stepOutcome.failureReason();
                    List<ProvenStep> partial = new ArrayList<>(autoFillSteps);
                    partial.addAll(stepOutcome.provenSteps());
                    lastPartial = partial;
                    evidenceDir = stepOutcome.evidenceDir() == null ? ""
                            : stepOutcome.evidenceDir().toString();

                    // Cap: Ollama once + Cursor once. Escalate to Cursor after Ollama execute-fail (D3).
                    if (!ollamaUsed && !cursorUsed) {
                        String freshHtml = HtmlSlimmer.slim(driverFactory.get().getPageSource(), 80000);
                        byte[] failPng = execution.capturePngBytes();
                        Path shot = writeHealScreenshot(evidence, tc.tcId(), intentIndex, failPng);
                        HealResult healed = healCascade.heal(
                                tc.tcId(), intent, freshHtml, failPng, lastReason, shot, true, priorSteps);
                        if (healed.ok()) {
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
                                return StepAttempt.success(combined, retriesUsed, healTier, "");
                            }
                            lastReason = retryOutcome.failureReason();
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
                        String html2 = HtmlSlimmer.slim(driverFactory.get().getPageSource(), 80000);
                        byte[] png2 = execution.capturePngBytes();
                        Path shot2 = writeHealScreenshot(evidence, tc.tcId(), intentIndex, png2);
                        HealResult cursorHeal = healCascade.heal(
                                tc.tcId(), intent, html2, png2,
                                "re-execute after Ollama heal failed: " + lastReason,
                                shot2, false, priorSteps);
                        if (cursorHeal.ok()) {
                            cursorUsed = true;
                            healTier = mergeHealTier(healTier, cursorHeal.tierUsed());
                            TcOutcome cursorRetry = execution.execute(
                                    tc.tcId(), cursorHeal.steps(), evidence);
                            if (cursorRetry.status() == TcStatus.PASSED) {
                                List<ProvenStep> combined = new ArrayList<>(autoFillSteps);
                                combined.addAll(cursorRetry.provenSteps());
                                return StepAttempt.success(combined, retriesUsed, healTier, "");
                            }
                            lastReason = "HEAL_EXHAUSTED: re-execute after Cursor heal failed: "
                                    + cursorRetry.failureReason();
                            healSkipReason = lastReason;
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
                return StepAttempt.success(combined, retriesUsed, healTier, healSkipReason);
            } catch (Exception e) {
                lastReason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                lastPartial = List.of();
            }
        }
        return StepAttempt.fail(lastReason, lastPartial, evidenceDir, retriesUsed, healTier, healSkipReason);
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

    /**
     * Fallback only: keep per-step names from {@link TcExecutionService}; fill blanks with
     * {@code url} stem. Never overwrite a real page name with the case-final URL.
     * Legacy "Login"/"TargetLogin" map to the URL stem so login form steps share one page.
     */
    private static List<ProvenStep> stampPageNames(List<ProvenStep> steps, String url) {
        String fallback = PageClusterer.pageNameFromUrl(url);
        List<ProvenStep> out = new ArrayList<>();
        for (ProvenStep s : steps) {
            String name = s.pageName();
            if (name == null || name.isBlank() || "Page".equals(name)
                    || "Login".equalsIgnoreCase(name) || "TargetLogin".equals(name)) {
                name = fallback;
            }
            out.add(s.withPageName(name));
        }
        return out;
    }

    /** Stamp login prelude with login-form URL stem; keep already-resolved page names. */
    private static List<ProvenStep> stampLoginNames(List<ProvenStep> loginSteps, String loginFormUrl) {
        if (loginSteps == null || loginSteps.isEmpty()) {
            return List.of();
        }
        String page = PageClusterer.pageNameFromUrl(loginFormUrl);
        if (page == null || page.isBlank() || "Page".equals(page) || "Home".equals(page)) {
            page = "LoginForm";
        }
        List<ProvenStep> out = new ArrayList<>();
        for (ProvenStep s : loginSteps) {
            String name = s.pageName();
            if (name != null && !name.isBlank()
                    && !"Page".equals(name)
                    && !"Login".equalsIgnoreCase(name)
                    && !"TargetLogin".equals(name)) {
                out.add(s);
            } else {
                out.add(s.withPageName(page));
            }
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
            case "invent" -> 4;
            case "vision" -> 3;
            case "cursor" -> 2;
            case "ollama" -> 1;
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
