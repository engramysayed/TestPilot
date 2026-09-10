package delivery.hunt;

import delivery.excel.ManualTestCase;
import delivery.job.ConversionJobRequest;
import delivery.job.JobCancelledException;
import delivery.job.JobLoginService;
import delivery.job.JobProgressTracker;
import delivery.job.PageSnapshot;
import delivery.store.PreferredHooksStore;
import drivers.WebDriverFactory;
import org.json.JSONArray;
import org.json.JSONObject;
import org.openqa.selenium.WebDriver;
import parsingLayer.HtmlSlimmer;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;

/**
 * Live Bug Hunter: browser observe → planner → act loop until finish or cycle ceiling.
 */
public final class LiveHuntService {

    /** Contract hint for tests: navigate before optional login prelude. */
    public static String openThenLoginOrderHint() {
        return "baseUrl-then-optional-login";
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
        String preferredHooksLine = PreferredHooksStore.join(
                storeRoot == null ? List.of() : PreferredHooksStore.load(storeRoot, request.getBaseUrl()));
        int groundedRejects = 0;

        if (tracker != null) {
            tracker.update(0, request.getCycleCeiling(), "Opening browser");
        }
        checkCancel(cancelCheck);

        WebDriverFactory driverFactory = new WebDriverFactory();
        List<Map<String, Object>> allBugs = new ArrayList<>();
        List<Map<String, Object>> allScenarios = new ArrayList<>();
        String stopReason = "CYCLE_CAP";
        String networkStatus = "unsupported";
        int cyclesUsed = 0;
        int oracleBugCount = 0;

        try (HuntNetworkCapture network = HuntNetworkCapture.attach(driverFactory.get())) {
            networkStatus = network.statusLabel();
            WebDriver driver = driverFactory.get();
            openSiteThenMaybeLogin(driver, driverFactory, request, loginRequest, hasLoginUsername, huntRoot, journal);

            HuntActionExecutor actions = new HuntActionExecutor(driver);

            for (int cycle = 1; cycle <= request.getCycleCeiling(); cycle++) {
                checkCancel(cancelCheck);
                cyclesUsed = cycle;
                if (tracker != null) {
                    tracker.update(cycle, request.getCycleCeiling(), "Hunt cycle " + cycle);
                }

                Path cycleDir = huntRoot.resolve("cycles").resolve(String.format(
                        java.util.Locale.ROOT, "cycle-%02d", cycle));
                Files.createDirectories(cycleDir);

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

                List<Map<String, Object>> netFails = network.supported()
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
                        preferredHooksLine
                );
                String userPrompt = OllamaHuntPlanner.buildUserPrompt(ctx);
                // TestPilot-style evidence: exact prompt + response as text + screenshot
                Files.writeString(cycleDir.resolve("planner-prompt.txt"),
                        OllamaHuntPlanner.systemPrompt() + "\n\n---\n\n" + userPrompt,
                        StandardCharsets.UTF_8);
                Files.writeString(cycleDir.resolve("planner-request.md"), userPrompt, StandardCharsets.UTF_8);

                HuntPlannerDecision decision = planner.plan(ctx);
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

                journal.appendCycleHeader(cycle,
                        decision.decision().name().toLowerCase(),
                        decision.rationale());
                journal.appendActions(cycle, actionLog);

                List<Map<String, Object>> plannerBugs = decision.bugs();
                for (Map<String, Object> bug : plannerBugs) {
                    attachEvidence(bug, cycle, shot);
                    allBugs.add(bug);
                }

                String postSlim = HtmlSlimmer.slim(PageSnapshot.html(driver), 80000);
                HuntPageMap postMap = HuntPageMapBuilder.build(
                        driver.getCurrentUrl(), driver.getTitle(), postSlim);
                HuntOracle.PageSignals pageSignals = new HuntOracle.PageSignals(
                        driver.getCurrentUrl(),
                        HuntOracle.mainTextLength(postSlim),
                        urlsBefore,
                        request.isStrategiesEnabled() ? sequencer.current().mode() : "explore",
                        HuntOracle.lastActionNavigateOrClick(actionLog));
                List<Map<String, Object>> oracleBugs = HuntOracle.collect(
                        cycle,
                        netFails,
                        postMap.alerts(),
                        actionLog,
                        journal.reproSlice(),
                        plannerBugs,
                        pageSignals);
                Files.writeString(cycleDir.resolve("oracle.json"),
                        new JSONObject(HuntOracle.signalSnapshot(
                                netFails, postMap.alerts(), pageSignals, oracleBugs)).toString(2),
                        StandardCharsets.UTF_8);
                for (Map<String, Object> bug : oracleBugs) {
                    attachEvidence(bug, cycle, shot);
                    allBugs.add(bug);
                    oracleBugCount++;
                }
                int remaining = request.getScenarioCap() - allScenarios.size();
                for (Map<String, Object> sc : decision.scenarios()) {
                    if (remaining <= 0) {
                        break;
                    }
                    allScenarios.add(sc);
                    remaining--;
                }

                if (request.isStrategiesEnabled() && sequencer.isLast()
                        && allScenarios.size() >= request.getScenarioCap()) {
                    sequencer.completeCurrent();
                    coverage.markStrategyDone("invent");
                }

                Optional<String> stuckStop = HuntStopRules.afterActions(
                        coverage,
                        decision.decision(),
                        request.isStrategiesEnabled(),
                        request.isStrategiesEnabled() ? sequencer : null);
                if (stuckStop.isPresent()) {
                    stopReason = stuckStop.get();
                    break;
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
                if (decision.decision() == HuntPlannerDecision.Decision.FINISH) {
                    stopReason = HuntStopRules.resolveFinishStopReason(
                            request.isStrategiesEnabled(),
                            request.isStrategiesEnabled() ? sequencer : null,
                            allScenarios.size(),
                            request.getScenarioCap(),
                            allBugs.size());
                    break;
                }
            }
            if (cyclesUsed >= request.getCycleCeiling()
                    && !"FINISH".equals(stopReason)
                    && !"COMPLETE".equals(stopReason)
                    && !"STUCK".equals(stopReason)) {
                stopReason = "CYCLE_CAP";
            }
        } finally {
            try {
                driverFactory.quit();
            } catch (Exception ignored) {
            }
        }

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
     * Always open the project base URL first. Optional credential login runs after that.
     * Login failures are soft for Bug Hunter (continue on the open page) so login-feature hunts
     * and mismatched selectors do not abort before cycle 1.
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
        String base = request.getBaseUrl() == null ? "" : request.getBaseUrl().trim();
        if (!base.isBlank()) {
            driver.get(base);
        }
        if (!hasLoginUsername || loginRequest == null) {
            Files.writeString(huntRoot.resolve("login-prelude.txt"),
                    "Opened site only (no auto-login credentials).\nurl=" + safeUrl(driver) + "\n",
                    StandardCharsets.UTF_8);
            return;
        }
        try {
            new JobLoginService().loginIfNeeded(driverFactory, loginRequest);
            Files.writeString(huntRoot.resolve("login-prelude.txt"),
                    "Auto-login OK.\nurl=" + safeUrl(driver) + "\n",
                    StandardCharsets.UTF_8);
        } catch (Exception e) {
            String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            Files.writeString(huntRoot.resolve("login-prelude.txt"),
                    "Auto-login failed — continuing hunt on current page.\n"
                            + "url=" + safeUrl(driver) + "\n"
                            + "error=" + msg + "\n",
                    StandardCharsets.UTF_8);
            journal.appendCycleHeader(0, "login_prelude",
                    "Auto-login failed; hunt continues from open page. " + msg);
        }
    }

    private static String safeUrl(WebDriver driver) {
        try {
            String u = driver.getCurrentUrl();
            return u == null ? "" : u;
        } catch (Exception e) {
            return "";
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
