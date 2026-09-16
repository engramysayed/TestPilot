package delivery.hunt;

import delivery.excel.ManualTestCase;
import delivery.job.ConversionJobRequest;
import delivery.job.DeadBrowserSession;
import delivery.job.JobCancelledException;
import delivery.job.JobProgressTracker;
import delivery.job.PageSnapshot;
import delivery.store.PreferredHooksStore;
import drivers.WebDriverFactory;
import org.json.JSONArray;
import org.json.JSONObject;
import org.openqa.selenium.WebDriver;
import parsingLayer.HtmlSlimmer;
import utils.LogsManager;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;

/**
 * Live Bug Hunter: browser observe → planner → act loop until finish or cycle ceiling.
 */
public final class LiveHuntService {

    /** Contract hint for tests: navigate first; login is hunter-driven via credential tokens. */
    public static String openThenLoginOrderHint() {
        return "baseUrl-then-hunter-login";
    }

    public HuntJobResult run(
            HuntRequest request,
            List<ManualTestCase> selectedCases,
            Path huntRoot,
            ConversionJobRequest loginRequest,
            HuntPlanner planner,
            JobProgressTracker tracker,
            BooleanSupplier cancelCheck
    ) throws Exception {
        return run(request, selectedCases, huntRoot, loginRequest, planner, tracker, cancelCheck, null);
    }

    public HuntJobResult run(
            HuntRequest request,
            List<ManualTestCase> selectedCases,
            Path huntRoot,
            ConversionJobRequest loginRequest,
            HuntPlanner planner,
            JobProgressTracker tracker,
            BooleanSupplier cancelCheck,
            Path storeRoot
    ) throws Exception {
        request.normalize();
        if (planner == null) {
            throw new IllegalArgumentException("planner required");
        }
        Files.createDirectories(huntRoot);
        String brief = HuntBriefBuilder.build(request, selectedCases);
        Files.writeString(huntRoot.resolve("brief.md"), brief, StandardCharsets.UTF_8);
        HuntStepsJournal journal = new HuntStepsJournal(huntRoot);
        HuntCoverageMap coverage = new HuntCoverageMap(huntRoot);
        HuntStrategySequencer sequencer = new HuntStrategySequencer(request.isStrategiesEnabled());
        HuntDomMode domMode = HuntDomMode.parse(request.getDomMode());
        boolean hasLoginUsername = loginRequest != null
                && loginRequest.username() != null
                && !loginRequest.username().isBlank();
        List<String> preferredHooks = storeRoot == null
                ? List.of()
                : PreferredHooksStore.load(
                        storeRoot,
                        loginRequest == null ? null : loginRequest.tenantId(),
                        request.getBaseUrl());
        String preferredHooksLine = PreferredHooksStore.join(preferredHooks);
        boolean loginFeature = HuntFeatureHints.looksLikeLoginFeature(
                request.getBaseUrl(), request.getUserStory(), brief);
        HuntSecretResolver secrets = HuntSecretResolver.of(
                hasLoginUsername ? loginRequest.username() : "",
                hasLoginUsername && loginRequest.password() != null ? loginRequest.password() : "",
                request.getOtp());
        boolean hasOtp = secrets.hasOtp();
        int groundedRejects = 0;
        int bugsAttempted = 0;
        int happyStreak = 0;
        boolean leftLoginOnce = false;

        if (tracker != null) {
            tracker.update(0, request.getCycleCeiling(), "Opening browser");
        }
        checkCancel(cancelCheck);

        List<Map<String, Object>> allBugs = new ArrayList<>();
        List<Map<String, Object>> allScenarios = new ArrayList<>();
        String stopReason = "ITERATION_CAP";
        String networkStatus = "unsupported";
        int cyclesUsed = 0;
        int iterationsUsed = 0;
        int oracleBugCount = 0;
        boolean stopEntireHunt = false;

        HuntRunLog.info("job=" + request.getJobId()
                + " baseUrl=" + request.getBaseUrl()
                + " iterations=" + request.getIterationCeiling()
                + " cyclesPerIteration=" + request.getCycleCeiling()
                + " actionCap=" + request.getActionCapPerCycle()
                + " planner=" + request.getPlanner()
                + " strategies=" + request.isStrategiesEnabled()
                + " hasCreds=" + hasLoginUsername
                + " hasOtp=" + hasOtp
                + " loginFeature=" + loginFeature
                + " hooks=" + (preferredHooksLine.isBlank() ? "none" : preferredHooksLine));

        if (loginFeature) {
            coverage.seedLoginGaps();
        }

        try (PreferredHooksStore.Scope ignoredHooks = PreferredHooksStore.activate(preferredHooks);
             HuntBrowserSession session = HuntBrowserSession.open(
                request.getBaseUrl(), loginRequest, hasLoginUsername, preferredHooks)) {
            networkStatus = session.networkStatus();
            HuntRunLog.info("browser open network=" + networkStatus);
            WebDriver driver = session.driver();
            openSiteThenMaybeLogin(driver, session.driverFactory(), request, loginRequest,
                    hasLoginUsername, huntRoot, journal, preferredHooks);

            HuntActionExecutor actions = HuntActionExecutor.forSession(session);
            actions.setSecretResolver(secrets);

            iterationLoop:
            for (int iteration = 1; iteration <= request.getIterationCeiling() && !stopEntireHunt; iteration++) {
                iterationsUsed = iteration;
                Path iterDir = huntRoot.resolve("iterations").resolve(String.format(
                        java.util.Locale.ROOT, "iteration-%02d", iteration));
                Files.createDirectories(iterDir);
                HuntIterationJournal iterJournal = new HuntIterationJournal(iterDir, iteration);
                String priorIterResults = iteration > 1
                        ? HuntIterationJournal.readResults(huntRoot, iteration - 1) : "";

                HuntRunLog.info("iteration " + iteration + "/" + request.getIterationCeiling() + " start");

                boolean iterationEndedByPlanner = false;
                for (int cycle = 1; cycle <= request.getCycleCeiling(); cycle++) {
                checkCancel(cancelCheck);
                cyclesUsed++;
                if (tracker != null) {
                    tracker.update(cycle, request.getCycleCeiling(),
                            "Iteration " + iteration + " cycle " + cycle);
                }

                Path cycleDir = iterDir.resolve("cycles").resolve(String.format(
                        java.util.Locale.ROOT, "cycle-%02d", cycle));
                Files.createDirectories(cycleDir);

                driver = session.driver();
                HuntNetworkCapture network = session.network();

                if (!ensureAliveOrRestart(session, journal, cycle, false)) {
                    stopReason = "BROWSER_DEAD";
                    iterJournal.flushResults(stopReason);
                    stopEntireHunt = true;
                    break iterationLoop;
                }
                driver = session.driver();
                network = session.network();

                String slim = HtmlSlimmer.slim(PageSnapshot.html(driver), 80000);
                Files.writeString(cycleDir.resolve("dom-slim.txt"), slim, StandardCharsets.UTF_8);

                byte[] png = actions.screenshotPng();
                Path shot = cycleDir.resolve("screenshot.png");
                if (png != null && png.length > 0) {
                    Files.write(shot, png);
                } else {
                    Files.writeString(cycleDir.resolve("screenshot.missing.txt"),
                            "screenshot unavailable", StandardCharsets.UTF_8);
                    shot = null;
                }

                List<Map<String, Object>> netFails = network != null && network.supported()
                        ? network.snapshotAndClear()
                        : List.of();
                Files.writeString(cycleDir.resolve("network-failures.json"),
                        new JSONArray(netFails).toString(2), StandardCharsets.UTF_8);

                HuntPageMap map = HuntPageMapBuilder.build(
                        driver.getCurrentUrl(), driver.getTitle(), slim);
                Files.writeString(cycleDir.resolve("page-map.json"),
                        map.toJsonObject().toString(2), StandardCharsets.UTF_8);
                Files.writeString(cycleDir.resolve("page-map.md"),
                        map.toPromptMd(), StandardCharsets.UTF_8);

                String heading = map.headings().isEmpty() ? "" : map.headings().get(0);
                List<String> urlsBefore = coverage.visitedUrls();
                coverage.noteVisit(map.url(), map.title(), heading);

                boolean includeSlim = HuntDomMode.shouldIncludeSlim(domMode, map);
                actions.setGuard(new HuntActionGuard(map, slim));

                skipSessionIfNoLogin(sequencer, coverage, hasLoginUsername);

                String strategyHint = request.isStrategiesEnabled()
                        ? sequencer.forPrompt()
                        : "mode=explore";
                HuntRunLog.cycleStart(cycle, request.getCycleCeiling(), map.url(), strategyHint);

                iterJournal.flushPlan();
                HuntPlanner.Context ctx = new HuntPlanner.Context(
                        brief,
                        cycle,
                        request.getCycleCeiling(),
                        request.getScenarioCap(),
                        allScenarios.size(),
                        request.getActionCapPerCycle(),
                        slim,
                        shot,
                        netFails,
                        journal.forPrompt(),
                        request.getPlanner(),
                        map.toPromptMd(),
                        includeSlim,
                        coverage.forPrompt(),
                        strategyHint,
                        request.getDomMode(),
                        preferredHooksLine,
                        hasLoginUsername,
                        hasLoginUsername ? loginRequest.username() : "",
                        hasOtp,
                        loginFeature,
                        iteration,
                        request.getIterationCeiling(),
                        iterJournal.planMarkdown(),
                        priorIterResults
                );
                String userPrompt = OllamaHuntPlanner.buildUserPrompt(ctx);
                // TestPilot-style evidence: exact prompt + response as text + screenshot
                Files.writeString(cycleDir.resolve("planner-prompt.txt"),
                        OllamaHuntPlanner.systemPrompt() + "\n\n---\n\n" + userPrompt,
                        StandardCharsets.UTF_8);
                Files.writeString(cycleDir.resolve("planner-request.md"), userPrompt, StandardCharsets.UTF_8);

                if (tracker != null) {
                    tracker.update(cycle, request.getCycleCeiling(),
                            "Waiting for planner (cycle " + cycle + ")…");
                }
                HuntRunLog.info("cycle " + cycle + " waiting for planner...");

                HuntPlannerDecision decision = planner.plan(ctx);
                int planned = decision.actions() == null ? 0 : decision.actions().size();
                HuntRunLog.planner(cycle, decision.decision().name().toLowerCase(),
                        planned, decision.rationale());
                if (tracker != null) {
                    tracker.update(cycle, request.getCycleCeiling(),
                            "Running " + planned + " action(s) for cycle " + cycle);
                }
                String responseText = decision.rawJson().isBlank()
                        ? new org.json.JSONObject()
                        .put("decision", decision.decision().name().toLowerCase())
                        .put("rationale", decision.rationale())
                        .toString(2)
                        : decision.rawJson();
                Files.writeString(cycleDir.resolve("planner-response.txt"), responseText, StandardCharsets.UTF_8);
                Files.writeString(cycleDir.resolve("planner-response.json"), responseText, StandardCharsets.UTF_8);

                List<Map<String, Object>> actionLog = List.of();
                if (decision.decision() != HuntPlannerDecision.Decision.FINISH
                        && decision.actions() != null
                        && !decision.actions().isEmpty()) {
                    actionLog = actions.executeAll(decision.actions(), request.getActionCapPerCycle());
                }
                HuntActionExecutor.writeActionsLog(cycleDir, actionLog);
                groundedRejects += countGroundedRejects(actionLog);
                coverage.recordActions(actionLog);
                updateCoverageGaps(coverage, actionLog);

                journal.appendCycleHeader(cycle,
                        decision.decision().name().toLowerCase(),
                        decision.rationale());
                journal.appendActions(cycle, actionLog);

                List<Map<String, Object>> plannerBugs = decision.bugs();
                for (Map<String, Object> bug : plannerBugs) {
                    attachEvidence(bug, cycle, shot);
                    bugsAttempted++;
                    if (HuntBugDedupe.addUnique(allBugs, bug)) {
                        // kept
                    }
                }

                driver = session.driver();
                String postSlim;
                HuntPageMap postMap;
                try {
                    postSlim = HtmlSlimmer.slim(PageSnapshot.html(driver), 80000);
                    postMap = HuntPageMapBuilder.build(
                            driver.getCurrentUrl(), driver.getTitle(), postSlim);
                } catch (Exception postEx) {
                    if (!DeadBrowserSession.isDead(postEx)) {
                        throw postEx;
                    }
                    LogsManager.warn("HUNT_POST_CYCLE_DEAD: cycle=" + cycle + " — " + postEx.getMessage());
                    if (!ensureAliveOrRestart(session, journal, cycle, false)) {
                        stopReason = "BROWSER_DEAD";
                        iterJournal.flushResults(stopReason);
                        stopEntireHunt = true;
                        break iterationLoop;
                    }
                    driver = session.driver();
                    postSlim = HtmlSlimmer.slim(PageSnapshot.html(driver), 80000);
                    postMap = HuntPageMapBuilder.build(
                            driver.getCurrentUrl(), driver.getTitle(), postSlim);
                }

                List<String> alertTexts = new ArrayList<>(postMap.alerts());
                if (HuntAlertPoller.networkSuggestsAuthFailure(netFails)) {
                    HuntRunLog.info("cycle " + cycle + " polling alerts after auth failure status");
                    for (String a : HuntAlertPoller.poll(driver, HuntAlertPoller.DEFAULT_TIMEOUT_MS)) {
                        if (!alertTexts.contains(a)) {
                            alertTexts.add(a);
                        }
                    }
                }

                if (!HuntFeatureHints.looksLikeLoginUrl(postMap.url())) {
                    leftLoginOnce = true;
                }

                HuntOracle.PageSignals pageSignals = new HuntOracle.PageSignals(
                        postMap.url(),
                        HuntOracle.mainTextLength(postSlim),
                        urlsBefore,
                        request.isStrategiesEnabled() ? sequencer.current().mode() : "explore",
                        HuntOracle.lastActionNavigateOrClick(actionLog),
                        loginFeature);
                List<Map<String, Object>> oracleBugs = HuntOracle.collect(
                        cycle,
                        netFails,
                        alertTexts,
                        actionLog,
                        journal.reproSlice(),
                        plannerBugs,
                        pageSignals);
                Files.writeString(cycleDir.resolve("oracle.json"),
                        new JSONObject(HuntOracle.signalSnapshot(
                                netFails, alertTexts, pageSignals, oracleBugs)).toString(2),
                        StandardCharsets.UTF_8);
                for (Map<String, Object> bug : oracleBugs) {
                    attachEvidence(bug, cycle, shot);
                    bugsAttempted++;
                    if (HuntBugDedupe.addUnique(allBugs, bug)) {
                        oracleBugCount++;
                    }
                }
                int remaining = request.getScenarioCap() - allScenarios.size();
                for (Map<String, Object> sc : decision.scenarios()) {
                    if (remaining <= 0) {
                        break;
                    }
                    allScenarios.add(sc);
                    iterJournal.noteScenario(sc);
                    remaining--;
                }
                iterJournal.flushPlan();

                HuntRunLog.cycleDone(cycle, allBugs.size(), allScenarios.size());

                if (request.isStrategiesEnabled()) {
                    String mode = sequencer.current().mode();
                    if ("happy".equals(mode)) {
                        happyStreak++;
                    } else {
                        happyStreak = 0;
                    }
                    if (HuntFeatureHints.shouldAdvanceHappy(
                            mode, happyStreak, postMap.url(), hasLoginUsername, leftLoginOnce)
                            && !sequencer.isLast()) {
                        String prev = mode;
                        sequencer.advance();
                        coverage.markStrategyDone(prev);
                        happyStreak = 0;
                        HuntRunLog.info("strategy advance " + prev + "->" + sequencer.current().mode()
                                + " reason=happy_blocked_on_login");
                    }
                }

                if (request.isStrategiesEnabled() && sequencer.isLast()
                        && allScenarios.size() >= request.getScenarioCap()) {
                    sequencer.completeCurrent();
                    coverage.markStrategyDone("invent");
                }

                HuntStopRules.Recovery recovery = HuntStopRules.recoverFromStuck(
                        coverage,
                        request.isStrategiesEnabled(),
                        request.isStrategiesEnabled() ? sequencer : null);
                if (recovery.triggered()) {
                    HuntRunLog.info("cycle " + cycle + " repeated failures — blocked="
                            + recovery.blockedLocators()
                            + (recovery.advancedFrom().isBlank() ? " (continuing)"
                            : " strategy " + recovery.advancedFrom() + "->" + recovery.advancedTo()));
                }
                Optional<String> completeStop = HuntStopRules.completeStop(
                        request.isStrategiesEnabled(),
                        request.isStrategiesEnabled() ? sequencer : null,
                        allScenarios.size(),
                        request.getScenarioCap(),
                        allBugs.size());
                if (completeStop.isPresent()) {
                    stopReason = completeStop.get();
                    break;
                }
                if (decision.decision() == HuntPlannerDecision.Decision.FINISH_ITERATION) {
                    stopReason = "FINISH_ITERATION";
                    iterationEndedByPlanner = true;
                    iterJournal.appendExecutionNote("Planner finish_iteration at cycle " + cycle
                            + ": " + decision.rationale());
                    iterJournal.flushResults(stopReason);
                    HuntRunLog.info("iteration " + iteration + " finished by planner");
                    break;
                }
                if (decision.decision() == HuntPlannerDecision.Decision.FINISH) {
                    stopReason = HuntStopRules.resolveFinishStopReason(
                            request.isStrategiesEnabled(),
                            request.isStrategiesEnabled() ? sequencer : null,
                            allScenarios.size(),
                            request.getScenarioCap(),
                            allBugs.size());
                    stopEntireHunt = true;
                    iterJournal.appendExecutionNote("Planner finish_hunt at cycle " + cycle
                            + ": " + decision.rationale());
                    iterJournal.flushResults(stopReason);
                    break iterationLoop;
                }
                }
                if (!iterationEndedByPlanner) {
                    iterJournal.appendExecutionNote("Cycle cap reached for iteration " + iteration);
                    iterJournal.flushResults("CYCLE_CAP");
                }
            }
            if (iterationsUsed >= request.getIterationCeiling()
                    && !stopEntireHunt
                    && !"FINISH".equals(stopReason)
                    && !"COMPLETE".equals(stopReason)
                    && !"BROWSER_DEAD".equals(stopReason)) {
                stopReason = "ITERATION_CAP";
            }
        }

        try {
            Files.writeString(huntRoot.resolve("bug-dedupe.json"),
                    new JSONObject(HuntBugDedupe.stats(allBugs, bugsAttempted)).toString(2),
                    StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }

        allBugs = runBugTriage(planner, allBugs, journal, coverage, huntRoot);

        HuntRunLog.finished(stopReason, cyclesUsed, allBugs.size(), allScenarios.size());
        Path zip = HuntPackWriter.writePack(
                huntRoot, request, brief, stopReason, allBugs, allScenarios, cyclesUsed, networkStatus,
                groundedRejects, sequencer.completed(), oracleBugCount, coverage.visitedUrlCount());
        if (tracker != null) {
            tracker.update(request.getCycleCeiling(), request.getCycleCeiling(), "Hunter pack ready");
            tracker.setScores(allBugs.size(), allScenarios.size());
        }
        return new HuntJobResult(
                zip,
                allBugs.size(),
                allScenarios.size(),
                "Hunt finished (" + stopReason + ") — "
                        + allBugs.size() + " bug(s), " + allScenarios.size() + " scenario(s)",
                stopReason,
                networkStatus
        );
    }

    /**
     * Always open the project base URL first. Credentials (if any) are passed to the planner
     * as tokens — the hunter performs login through UI actions, not {@link JobLoginService}.
     */
    static void openSiteThenMaybeLogin(
            WebDriver driver,
            WebDriverFactory driverFactory,
            HuntRequest request,
            ConversionJobRequest loginRequest,
            boolean hasLoginUsername,
            Path huntRoot,
            HuntStepsJournal journal
    ) throws Exception {
        openSiteThenMaybeLogin(driver, driverFactory, request, loginRequest,
                hasLoginUsername, huntRoot, journal, List.of());
    }

    static void openSiteThenMaybeLogin(
            WebDriver driver,
            WebDriverFactory driverFactory,
            HuntRequest request,
            ConversionJobRequest loginRequest,
            boolean hasLoginUsername,
            Path huntRoot,
            HuntStepsJournal journal,
            List<String> preferredHooks
    ) throws Exception {
        String base = request.getBaseUrl() == null ? "" : request.getBaseUrl().trim();
        if (!base.isBlank()) {
            HuntRunLog.info("open site " + base);
            driver.get(base);
        }
        String prelude = "Opened site only — hunter-driven login (no server auto-login).\n"
                + "url=" + safeUrl(driver) + "\n";
        if (hasLoginUsername && loginRequest != null) {
            prelude += "credentials=available via ${TARGET_USERNAME}/${TARGET_PASSWORD} in planner prompt\n";
            if (preferredHooks != null && !preferredHooks.isEmpty()) {
                prelude += "hooks=" + String.join(",", preferredHooks) + "\n";
            }
            HuntRunLog.info("open site only; creds passed to hunter url=" + safeUrl(driver));
        } else {
            HuntRunLog.info("open site only (no credential profile) url=" + safeUrl(driver));
        }
        Files.writeString(huntRoot.resolve("login-prelude.txt"), prelude, StandardCharsets.UTF_8);
    }

    private static void updateCoverageGaps(HuntCoverageMap coverage, List<Map<String, Object>> actionLog)
            throws Exception {
        if (actionLog == null) {
            return;
        }
        for (Map<String, Object> row : actionLog) {
            String type = String.valueOf(row.get("type")).toLowerCase();
            String status = String.valueOf(row.get("status")).toLowerCase();
            String value = String.valueOf(row.get("value"));
            if ("type".equals(type) && "ok".equals(status)
                    && HuntSecretResolver.containsToken(value)) {
                coverage.markTestedHint("Valid login");
            }
            if ("assert_text".equals(type) && ("ok".equals(status) || "fail".equals(status))) {
                String text = String.valueOf(row.get("text"));
                if (text.toLowerCase().contains("username") && text.toLowerCase().contains("required")) {
                    coverage.markTestedHint("Empty username");
                }
                if (text.toLowerCase().contains("password") && text.toLowerCase().contains("required")) {
                    coverage.markTestedHint("Empty password");
                }
                if (text.toLowerCase().contains("invalid")) {
                    coverage.markTestedHint("Invalid credentials");
                }
            }
            if ("type".equals(type) && HuntSecretResolver.containsToken(value)
                    && value.toUpperCase().contains("OTP")) {
                coverage.markTestedHint("OTP");
            }
        }
    }

    private static List<Map<String, Object>> runBugTriage(
            HuntPlanner planner,
            List<Map<String, Object>> allBugs,
            HuntStepsJournal journal,
            HuntCoverageMap coverage,
            Path huntRoot
    ) {
        if (allBugs == null || allBugs.isEmpty() || planner == null) {
            return allBugs == null ? List.of() : allBugs;
        }
        try {
            String prompt = HuntBugTriage.triagePrompt(allBugs, journal.reproSlice(), coverage.forPrompt());
            String raw = planner.triageBugs(prompt);
            Map<String, Object> audit = new LinkedHashMap<>();
            audit.put("attempted", true);
            if (raw == null || raw.isBlank()) {
                audit.put("status", "skipped");
                audit.put("reason", "empty triage response");
                Files.writeString(huntRoot.resolve("bug-triage.json"),
                        new JSONObject(audit).toString(2), StandardCharsets.UTF_8);
                HuntRunLog.info("bug triage skipped (empty)");
                return allBugs;
            }
            HuntBugTriage.Decision decision = HuntBugTriage.parse(raw);
            List<Map<String, Object>> kept = HuntBugTriage.apply(allBugs, decision);
            audit.put("status", "ok");
            audit.put("before", allBugs.size());
            audit.put("after", kept.size());
            audit.put("raw", decision.rawJson());
            audit.put("dropCount", decision.drops().size());
            audit.put("mergeCount", decision.merges().size());
            Files.writeString(huntRoot.resolve("bug-triage.json"),
                    new JSONObject(audit).toString(2), StandardCharsets.UTF_8);
            HuntRunLog.info("bug triage before=" + allBugs.size() + " after=" + kept.size());
            return kept;
        } catch (Exception e) {
            try {
                Map<String, Object> audit = new LinkedHashMap<>();
                audit.put("status", "skipped");
                audit.put("reason", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
                Files.writeString(huntRoot.resolve("bug-triage.json"),
                        new JSONObject(audit).toString(2), StandardCharsets.UTF_8);
            } catch (Exception ignored) {
            }
            HuntRunLog.warn("bug triage skipped: " + e.getMessage());
            return allBugs;
        }
    }

    private static String abbreviateOneLine(String s, int max) {
        if (s == null) {
            return "";
        }
        String one = s.replace('\r', ' ').replace('\n', ' ').trim();
        if (one.length() <= max) {
            return one;
        }
        return one.substring(0, max) + "...";
    }

    private static String safeUrl(WebDriver driver) {
        try {
            String u = driver.getCurrentUrl();
            return u == null ? "" : u;
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * If Chrome/CDP died mid-hunt, restart once via the shared restart budget and reopen base URL.
     * Returns false when recovery is exhausted or restart fails.
     */
    static boolean ensureAliveOrRestart(
            HuntBrowserControls session,
            HuntStepsJournal journal,
            int cycle,
            boolean softLogin
    ) throws Exception {
        try {
            session.driver().getCurrentUrl();
            return true;
        } catch (Exception e) {
            if (!DeadBrowserSession.isDead(e)) {
                throw e;
            }
            LogsManager.warn("HUNT_BROWSER_DEAD: cycle=" + cycle + " — attempting restart. " + e.getMessage());
            HuntRunLog.warn("browser dead at cycle " + cycle + " -- attempting restart");
            Map<String, Object> restart = session.restart(softLogin);
            boolean ok = Boolean.TRUE.equals(restart.get("ok"));
            String reason = String.valueOf(restart.getOrDefault("reason",
                    ok ? "restarted" : "restart failed"));
            if (ok) {
                HuntRunLog.info("auto restart ok url=" + restart.getOrDefault("url", ""));
            } else {
                HuntRunLog.warn("auto restart failed: " + reason);
            }
            if (journal != null) {
                journal.appendCycleHeader(cycle, ok ? "browser_restart" : "browser_dead",
                        ok
                                ? "Chrome session died; auto restart_browser login=" + softLogin
                                + " url=" + restart.getOrDefault("url", "")
                                : "Chrome session died; could not restart (" + reason + ")");
            }
            return ok;
        }
    }

    private static void attachEvidence(Map<String, Object> bug, int cycle, Path shot) {
        if (bug == null) {
            return;
        }
        if (shot != null) {
            bug.put("evidenceHint", "cycles/cycle-" + String.format(java.util.Locale.ROOT, "%02d", cycle)
                    + "/screenshot.png");
        }
    }

    private static void checkCancel(BooleanSupplier cancelCheck) {
        if (cancelCheck != null && cancelCheck.getAsBoolean()) {
            throw new JobCancelledException();
        }
    }

    static void skipSessionIfNoLogin(HuntStrategySequencer sequencer,
                                             HuntCoverageMap coverage,
                                             boolean hasLoginUsername) throws Exception {
        if (hasLoginUsername || !sequencer.strategiesEnabled()) {
            return;
        }
        if ("session".equals(sequencer.current().mode())) {
            coverage.markStrategyDone("session");
            if (!sequencer.isLast()) {
                sequencer.advance();
            }
        }
    }

    private static int countGroundedRejects(List<Map<String, Object>> actionLog) {
        if (actionLog == null || actionLog.isEmpty()) {
            return 0;
        }
        int n = 0;
        for (Map<String, Object> row : actionLog) {
            if (!"rejected".equals(String.valueOf(row.get("status")))) {
                continue;
            }
            String reason = String.valueOf(row.get("reason"));
            if (reason.contains("ungrounded_locator")) {
                n++;
            }
        }
        return n;
    }
}
