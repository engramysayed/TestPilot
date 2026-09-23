package delivery.acceptance;

import delivery.authoring.AuthoringEngine;
import delivery.authoring.PrecisionJobConfig;
import delivery.excel.ManualTestCase;
import delivery.excel.ManualTcExcelWriter;
import delivery.ir.TcDraft;
import delivery.ir.TcDraftStatus;
import delivery.job.ConversionJobRequest;
import delivery.job.ConversionJobResult;
import delivery.job.ConversionJobRunner;
import delivery.job.ReuseEligibility;
import delivery.store.ProjectStore;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Controlled local shop acceptance (not third-party / not Medusa).
 * Enable with {@code -Dkeel.localAcceptance=true}. Default suite skips.
 */
public class LocalControlledAcceptanceLiveSmokeTest {
    private static final Path SHOP_SOURCE = Path.of("D:/priv/testpilot/keel-local-acceptance");
    private static final Path SHOP_DIR = Path.of("target/local-acceptance", java.util.UUID.randomUUID().toString()).toAbsolutePath();
    private static final Path WEB = SHOP_DIR.resolve("web");
    private static final Path EVIDENCE = SHOP_DIR.resolve("evidence");
    private static final Path TEMPLATE = Path.of("customer-framework-template");
    private static final String PROJECT_DOM = "kla_dom";
    private static final String PROJECT_VIS = "kla_vis";
    private static final String PROJECT_PREC = "kla_prec";

    private Process shopProcess;
    private String baseUrl;
    private Path storeRoot;
    private Path runEvidence;
    private final StringBuilder report = new StringBuilder();

    @BeforeClass
    public void startShop() throws Exception {
        if (!Boolean.getBoolean("keel.localAcceptance")) {
            throw new SkipException("set -Dkeel.localAcceptance=true to run controlled local acceptance");
        }
        Assert.assertTrue(Files.isDirectory(SHOP_SOURCE.resolve("web")), "shop fixture missing");
        Files.createDirectories(SHOP_DIR);
        Files.copy(SHOP_SOURCE.resolve("ShopServer.java"), SHOP_DIR.resolve("ShopServer.java"));
        try (var files = Files.walk(SHOP_SOURCE.resolve("web"))) {
            for (Path source : files.toList()) {
                Path target = WEB.resolve(SHOP_SOURCE.resolve("web").relativize(source));
                if (Files.isDirectory(source)) Files.createDirectories(target);
                else Files.copy(source, target);
            }
        }
        Files.createDirectories(EVIDENCE);
        runEvidence = LocalAcceptanceEvidence.createRunDir(EVIDENCE);
        Files.createDirectories(SHOP_DIR.resolve("keel-store"));
        compileAndStartShop();
        storeRoot = SHOP_DIR.resolve("keel-store");
        Files.createDirectories(storeRoot);
        System.setProperty("BROWSER_HEADLESS", "true");
        System.setProperty("EXECUTION_TYPE", "HEADLESS");
        System.setProperty("delivery.browser.headless", "true");
        System.setProperty("delivery.final-revise.enabled", "false");
        System.setProperty("delivery.emit-compile-check", "true");
        System.setProperty("DELIVERY_POST_ACTION_WAIT_MS", "2000");
        System.clearProperty("CURSOR_API_KEY");
        System.setProperty("delivery.provider.allowlist", "ollama,vision");
        report.append("CURSOR_API_KEY system property cleared for this JVM. Env key present=")
                .append(envKeyPresent())
                .append(" (value not recorded). Cursor not on this-job allowlist.\n");
        report.append("Controlled local acceptance — execution log\n");
        report.append("NOT representative third-party validation. Launch HOLD.\n");
        report.append("evidenceDir=").append(runEvidence).append('\n');
        report.append("baseUrl=").append(baseUrl).append('\n');
        oracleWalk();
    }

    @AfterClass(alwaysRun = true)
    public void stopShop() throws Exception {
        try {
            Path dest = runEvidence != null ? runEvidence : EVIDENCE;
            Files.createDirectories(dest);
            Files.writeString(dest.resolve("run-log.txt"), report.toString(), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
        if (shopProcess != null && shopProcess.isAlive()) {
            shopProcess.destroy();
            shopProcess.waitFor(8, TimeUnit.SECONDS);
            if (shopProcess.isAlive()) {
                shopProcess.destroyForcibly();
            }
        }
    }

    @Test
    public void runControlledLocalAcceptance() throws Exception {
        Path newExcel = SHOP_DIR.resolve("pack-new.xlsx");
        Path updateExcel = SHOP_DIR.resolve("pack-update.xlsx");
        ManualTcExcelWriter.write(newExcel, newPack());
        ManualTcExcelWriter.write(updateExcel, updatePack());

        String newSha = "";
        String updateSha = "";
        ConversionJobResult newResult = null;
        ConversionJobResult updateResult = null;
        ConversionJobResult visResult = null;
        ConversionJobResult precResult = null;
        LocalAcceptanceReplay.MavenRun newReplay = new LocalAcceptanceReplay.MavenRun(
                -1, Map.of(), "", false, false, false);
        LocalAcceptanceReplay.MavenRun updateReplay = new LocalAcceptanceReplay.MavenRun(
                -1, Map.of(), "", false, false, false);
        boolean visionInvoked = false;
        List<String> failures = new ArrayList<>();

        try {
            newResult = runJob(PROJECT_DOM, newExcel, "NEW", AuthoringEngine.KEEL, false, true);
            recordJob("A-DOM-NEW", newResult, PROJECT_DOM);
            Path newZip = copyZip(newResult.zipFile(), "dom-new.zip");
            newSha = sha256(newZip);
            report.append("DOM NEW sha256=").append(newSha).append('\n');
            restartShopSamePort();
            newReplay = replay(newZip, "replay-dom-new");
            report.append("DOM NEW replay exit=").append(newReplay.exitCode())
                    .append(" timedOut=").append(newReplay.timedOut())
                    .append(" statuses=").append(newReplay.statuses()).append('\n');
            failures.addAll(prefix("DOM NEW replay", LocalAcceptanceReplayGates.violations(newReplay)));

            restartShopSamePort();
            updateResult = runJob(PROJECT_DOM, updateExcel, "UPDATE", AuthoringEngine.KEEL, false, true);
            recordJob("A-DOM-UPDATE", updateResult, PROJECT_DOM);
            recordReuse(PROJECT_DOM);
            Path updateZip = copyZip(updateResult.zipFile(), "dom-update.zip");
            updateSha = sha256(updateZip);
            report.append("DOM UPDATE sha256=").append(updateSha).append('\n');
            restartShopSamePort();
            updateReplay = replay(updateZip, "replay-dom-update");
            report.append("DOM UPDATE replay exit=").append(updateReplay.exitCode())
                    .append(" timedOut=").append(updateReplay.timedOut())
                    .append(" statuses=").append(updateReplay.statuses()).append('\n');
            failures.addAll(prefix("DOM UPDATE replay", LocalAcceptanceReplayGates.violations(updateReplay)));

            Path visLogs = Path.of("test-output/default-run/logs/logs.log");
            if (Files.isRegularFile(visLogs)) {
                Files.writeString(visLogs, "", StandardCharsets.UTF_8);
            }
            System.setProperty("delivery.vision.grounding.provider", "uitars");
            System.setProperty("delivery.vision.grounding.model", "ui-tars");
            Path visExcel = SHOP_DIR.resolve("pack-vision.xlsx");
            ManualTcExcelWriter.write(visExcel, List.of(caseVis01()));
            visResult = runJob(PROJECT_VIS, visExcel, "NEW", AuthoringEngine.KEEL, true, true);
            recordJob("B-VISION-NEW", visResult, PROJECT_VIS);
            String visLogText = Files.isRegularFile(visLogs)
                    ? Files.readString(visLogs, StandardCharsets.UTF_8) : "";
            Files.writeString(runEvidence.resolve("mode-b-logs-excerpt.txt"), tail(visLogText, 80_000),
                    StandardCharsets.UTF_8);
            visionInvoked = visLogText.contains("VISION_GROUND")
                    || visLogText.contains("VISION:")
                    || visLogText.contains("ui-tars");
            report.append("B visionInvoked=").append(visionInvoked).append('\n');
            if (!visionInvoked) {
                failures.add("UI-TARS was not invoked; a DOM-only pass does not validate grounding.");
            }

            Path precLogs = Path.of("test-output/default-run/logs/logs.log");
            if (Files.isRegularFile(precLogs)) {
                Files.writeString(precLogs, "", StandardCharsets.UTF_8);
            }
            System.clearProperty("CURSOR_API_KEY");
            precResult = runJob(
                    PROJECT_PREC, precisionPackExcel(), "NEW", AuthoringEngine.PRECISION, false, false);
            recordJob("C-PRECISION-UNSET-KEY", precResult, PROJECT_PREC);
            String precLogText = Files.isRegularFile(precLogs)
                    ? Files.readString(precLogs, StandardCharsets.UTF_8) : "";
            Files.writeString(runEvidence.resolve("mode-c-logs-excerpt.txt"), tail(precLogText, 40_000),
                    StandardCharsets.UTF_8);
            String msg = precResult.message() == null ? "" : precResult.message();
            String cVisible = msg + "\n" + precLogText;
            report.append("C message=").append(msg).append('\n');
            report.append("C Cursor execution: not tested. No credentials in this report.\n");
            if (!cVisible.contains("PRECISION_FALLBACK")) {
                failures.add("Precision with key unset must report PRECISION_FALLBACK: " + msg);
            }
            if (!(cVisible.contains("PROVIDER_UNAVAILABLE") || cVisible.contains("fallback"))) {
                failures.add("fallback reason must be visible: " + msg);
            }
        } finally {
            Path dest = runEvidence != null ? runEvidence : EVIDENCE;
            Files.createDirectories(dest);
            Files.writeString(dest.resolve("run-log.txt"), report.toString(), StandardCharsets.UTF_8);
            if (newResult != null && updateResult != null && visResult != null && precResult != null) {
                Files.writeString(dest.resolve("2026-09-21-local-acceptance-results.md"),
                        resultsMarkdown(newSha, updateSha, newResult, updateResult, visResult, precResult,
                                newReplay, updateReplay, visionInvoked),
                        StandardCharsets.UTF_8);
            }
        }
        if (!failures.isEmpty()) {
            Assert.fail(String.join("\n", failures));
        }
        if (newResult != null && updateResult != null && visResult != null && precResult != null) {
            Files.writeString(Path.of("docs/reviews/2026-09-21-local-acceptance-results.md"),
                    resultsMarkdown(newSha, updateSha, newResult, updateResult, visResult, precResult,
                            newReplay, updateReplay, visionInvoked),
                    StandardCharsets.UTF_8);
        }
    }

    /**
     * Bounded DOM NEW prove + one ZIP replay of login → cart → Pay later (simulated)
     * → account order → session expiry. No UPDATE, UI-TARS, Cursor, or full pack.
     */
    @Test
    public void scopedCheckoutChainDomProveAndReplay() throws Exception {
        scopedCheckoutChain(false);
    }

    @Test
    public void explicitAssertionsProveAndReplay() throws Exception {
        scopedCheckoutChain(true);
    }

    private void scopedCheckoutChain(boolean explicitOnly) throws Exception {
        verifyRestoredPayLaterFixture();
        Path excel = SHOP_DIR.resolve("pack-scoped-chain.xlsx");
        List<ManualTestCase> pack = scopedCheckoutChainPack();
        Assert.assertTrue(pack.stream().anyMatch(tc ->
                        "TC_CHK_02".equals(tc.tcId())
                                && tc.steps().contains("Click Pay later (simulated)")),
                "instruction must stay Click Pay later (simulated)");
        ManualTcExcelWriter.write(excel, pack);

        System.setProperty("delivery.vision.grounding.enabled", "false");
        System.setProperty("delivery.heal.invent.enabled", "false");
        System.setProperty("delivery.heal.vision-widen.enabled", "false");
        System.setProperty("delivery.cursor-heal.enabled", "false");
        System.clearProperty("CURSOR_API_KEY");

        ConversionJobResult prove = runJob("kla_chain", excel, "NEW", AuthoringEngine.KEEL, false, true);
        recordJob("SCOPED-DOM-NEW", prove, "kla_chain");
        Map<String, TcDraft> drafts = ReuseEligibility.loadStoredDrafts(findProjectRoot("kla_chain"));
        recordScopedProve(drafts);
        List<String> failures = new ArrayList<>();
        failures.addAll(proveChainViolations(drafts));
        if (explicitOnly) {
            TcDraft ord = drafts.get("TC_ORD_01");
            TcDraft ses = drafts.get("TC_SES_01");
            Assert.assertNotNull(ord, "persisted order IR");
            Assert.assertNotNull(ses, "persisted session IR");
            Assert.assertTrue(ord.provenSteps().stream().anyMatch(s -> "captureText".equals(s.assertionType())), "capture missing from real prove IR");
            Assert.assertTrue(ord.provenSteps().stream().anyMatch(s -> "capturedEquals".equals(s.assertionType())), "compare missing from real prove IR");
            Assert.assertEquals(ses.provenSteps().stream().filter(s -> "signedOut".equals(s.assertionType())).count(), 2L);
        } else {
            failures.addAll(verifyProveBusinessOutcomes(drafts));
        }

        Path zip = prove.zipFile();
        String sha = "";
        LocalAcceptanceReplay.MavenRun replayRun = new LocalAcceptanceReplay.MavenRun(
                -1, Map.of(), "zip missing", false, false, false);
        if (failures.isEmpty() && zip != null && Files.isRegularFile(zip)) {
            Path copied = copyZip(zip, "scoped-chain-dom-new.zip");
            sha = sha256(copied);
            report.append("SCOPED zip sha256=").append(sha).append('\n');
            restartShopSamePort();
            replayRun = replay(copied, "replay-scoped-chain", explicitOnly);
            report.append("SCOPED replay exit=").append(replayRun.exitCode())
                    .append(" timedOut=").append(replayRun.timedOut())
                    .append(" statuses=").append(replayRun.statuses()).append('\n');
            failures.addAll(prefix("SCOPED replay",
                    LocalAcceptanceReplayGates.scopedChainViolations(
                            replayRun, explicitOnly ? List.of("Order_on_account", "Session_expiry_overlay")
                                    : LocalAcceptanceReplayGates.SCOPED_CHECKOUT_CHAIN)));
            if (!explicitOnly) failures.addAll(verifyReplayBusinessOutcomes(replayRun, copied));
        } else {
            failures.add("DOM NEW zip was not produced; replay not run");
        }

        Path dest = runEvidence != null ? runEvidence : EVIDENCE;
        Files.createDirectories(dest);
        Files.writeString(dest.resolve("scoped-chain-results.md"),
                scopedResultsMarkdown(sha, prove, drafts, replayRun, failures),
                StandardCharsets.UTF_8);
        Files.writeString(dest.resolve("run-log.txt"), report.toString(), StandardCharsets.UTF_8);
        if (!failures.isEmpty()) {
            Assert.fail(String.join("\n", failures));
        }
    }

    private ConversionJobResult runJob(
            String projectId,
            Path excel,
            String mode,
            AuthoringEngine engine,
            boolean vision,
            boolean disableInvent
    ) throws Exception {
        System.setProperty("delivery.vision.grounding.enabled", vision ? "true" : "false");
        System.setProperty("delivery.vision.grounding.provider", "uitars");
        System.setProperty("delivery.vision.grounding.model", "ui-tars");
        System.setProperty("delivery.vision.assertions.enabled", "false");
        System.setProperty("delivery.heal.invent.enabled", disableInvent ? "false" : "true");
        System.setProperty("delivery.heal.vision-widen.enabled", vision ? "true" : "false");
        System.setProperty("delivery.cursor-heal.enabled", engine == AuthoringEngine.PRECISION ? "true" : "false");
        Path work = Files.createTempDirectory(SHOP_DIR, "work-" + projectId + "-" + mode + "-");
        ConversionJobRequest req = new ConversionJobRequest(
                projectId,
                excel,
                baseUrl,
                "",
                "",
                work,
                storeRoot,
                TEMPLATE,
                mode,
                "http://127.0.0.1:11434",
                "gemma4:e2b",
                false,
                false,
                engine,
                new PrecisionJobConfig(engine == AuthoringEngine.PRECISION, 50)
        );
        return new ConversionJobRunner().run(req);
    }

    private void recordJob(String label, ConversionJobResult result, String projectId) throws Exception {
        report.append('\n').append(label)
                .append(" status=").append(result.jobStatus())
                .append(" passed=").append(result.passed())
                .append(" todo=").append(result.todo())
                .append(" message=").append(result.message())
                .append('\n');
        Map<String, TcDraft> drafts = ReuseEligibility.loadStoredDrafts(findProjectRoot(projectId));
        for (Map.Entry<String, TcDraft> e : drafts.entrySet()) {
            TcDraft d = e.getValue();
            report.append("  ").append(d.tcId())
                    .append(" status=").append(d.status())
                    .append(" engine=").append(d.jobAuthoringEngine())
                    .append(" precisionFallback=").append(d.precisionFallback())
                    .append(" reason=").append(d.precisionFallbackReason())
                    .append(" heal=").append(d.healTier())
                    .append(" fail=").append(d.failureReason())
                    .append('\n');
        }
    }

    private void recordReuse(String projectId) throws Exception {
        Path root = findProjectRoot(projectId);
        Map<String, TcDraft> drafts = ReuseEligibility.loadStoredDrafts(root);
        report.append("UPDATE reuse snapshot for ").append(projectId).append('\n');
        for (TcDraft d : drafts.values()) {
            report.append("  ").append(d.tcId()).append('=').append(d.status()).append('\n');
        }
    }

    private Path findProjectRoot(String projectId) {
        return new ProjectStore(storeRoot, baseUrl).projectRoot(projectId);
    }

    private Path copyZip(Path zip, String name) throws Exception {
        Path dest = (runEvidence != null ? runEvidence : EVIDENCE).resolve(name);
        Files.copy(zip, dest, StandardCopyOption.REPLACE_EXISTING);
        return dest;
    }

    private LocalAcceptanceReplay.MavenRun replay(Path zip, String folder) throws Exception {
        return replay(zip, folder, false);
    }

    private LocalAcceptanceReplay.MavenRun replay(Path zip, String folder, boolean explicitOnly) throws Exception {
        Path dest = SHOP_DIR.resolve(folder);
        if (Files.exists(dest)) {
            Files.walk(dest).sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (Exception ignored) {}
            });
        }
        unzip(zip, dest);
        upsert(dest.resolve("src/main/resources/delivery-target.properties"), Map.of(
                "BROWSER_TYPE", "CHROME",
                "EXECUTION_TYPE", "LocalHeadless",
                "BROWSER_HEADLESS", "true",
                "DEFAULT_WAIT", "8",
                "TEXT_CONTAINS_WAIT_SECONDS", "8",
                "NOT_VISIBLE_WAIT_SECONDS", "8"));
        upsert(dest.resolve("src/main/resources/waits.properties"), Map.of(
                "DELIVERY_POST_ACTION_WAIT_MS", "2000"));
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        ProcessBuilder pb = new ProcessBuilder(windows ? "mvn.cmd" : "mvn", "-B", "clean", "test");
        if (explicitOnly) pb.command().add("-Dtest=TC_ORD_01,TC_SES_01");
        pb.directory(dest.toFile());
        Path evidence = runEvidence != null ? runEvidence : EVIDENCE;
        LocalAcceptanceReplay.MavenRun run = LocalAcceptanceReplay.run(
                pb, Duration.ofMinutes(25), evidence.resolve(folder + "-mvn.log"));
        return LocalAcceptanceReplay.attachReports(run, dest);
    }

    private static final Pattern ORDER_ID = Pattern.compile("KLA-\\d+");
    private static final List<String> SCOPED_TC_ORDER = List.of(
            "TC_ACC_02", "TC_CART_01", "TC_CHK_02", "TC_ORD_01", "TC_SES_01");

    private static List<ManualTestCase> scopedCheckoutChainPack() {
        return List.of(
                caseAcc02(),
                caseCart01(),
                withCallBefore(caseChk02(), "TC_CART_01"),
                caseOrd01(),
                caseSes01());
    }

    private static ManualTestCase withCallBefore(ManualTestCase tc, String callBefore) {
        return new ManualTestCase(
                tc.tcId(), tc.title(), tc.preconditions(), tc.steps(), tc.expectedResult(),
                tc.priority(), tc.tags(), tc.visualAssertion(), tc.testData(), tc.keelPath(),
                callBefore);
    }

    private void verifyRestoredPayLaterFixture() throws Exception {
        HttpResponse<String> page = httpGet(baseUrl + "/checkout.html");
        Assert.assertEquals(page.statusCode(), 200, page.body());
        Assert.assertTrue(page.body().contains("id=\"pay-simulated\""), page.body());
        Assert.assertTrue(page.body().contains("Pay later (simulated)"), page.body());
        report.append("fixture checkout.html id=pay-simulated text=Pay later (simulated)\n");
    }

    private void recordScopedProve(Map<String, TcDraft> drafts) {
        String firstFail = null;
        for (String id : SCOPED_TC_ORDER) {
            TcDraft d = drafts.get(id);
            if (d == null) {
                report.append("  ").append(id).append(" status=MISSING\n");
                if (firstFail == null) {
                    firstFail = id + "=MISSING";
                }
                continue;
            }
            String status = d.status() == null ? "MISSING" : d.status().name();
            report.append("  ").append(id)
                    .append(" status=").append(status)
                    .append(" lastPage=").append(d.lastPageUrl())
                    .append(" fail=").append(d.failureReason())
                    .append(" blocker=").append(d.blockerIntent())
                    .append(" heal=").append(d.healSkipReason())
                    .append('\n');
            if (firstFail == null && d.status() != TcDraftStatus.PASSED
                    && d.status() != TcDraftStatus.REUSED) {
                firstFail = id + "=" + status;
                report.append("  firstFailingCase=").append(firstFail)
                        .append(" step=").append(d.blockerStepIndex())
                        .append('\n');
            } else if (firstFail != null && d.status() != TcDraftStatus.PASSED
                    && d.status() != TcDraftStatus.REUSED) {
                report.append("  ").append(id).append(" blocked after ").append(firstFail).append('\n');
            }
        }
    }

    private static List<String> proveChainViolations(Map<String, TcDraft> drafts) {
        List<String> out = new ArrayList<>();
        String firstFail = null;
        for (String id : SCOPED_TC_ORDER) {
            TcDraft d = drafts.get(id);
            boolean pass = d != null && (d.status() == TcDraftStatus.PASSED
                    || d.status() == TcDraftStatus.REUSED);
            if (pass) {
                continue;
            }
            String shown = d == null ? "MISSING" : String.valueOf(d.status());
            if (firstFail == null) {
                firstFail = id + "=" + shown;
                String reason = d == null ? "draft missing" : d.failureReason();
                out.add("prove " + id + " was " + shown
                        + (reason == null || reason.isBlank() ? "" : ": " + reason));
            } else {
                out.add("prove " + id + " blocked after " + firstFail
                        + (d != null && d.failureReason() != null && !d.failureReason().isBlank()
                        ? " (" + d.failureReason() + ")" : ""));
            }
        }
        return out;
    }

    private List<String> verifyProveBusinessOutcomes(Map<String, TcDraft> drafts) throws Exception {
        List<String> out = new ArrayList<>();
        TcDraft chk = drafts.get("TC_CHK_02");
        if (chk == null || (chk.status() != TcDraftStatus.PASSED && chk.status() != TcDraftStatus.REUSED)) {
            return out;
        }
        String orderId = firstOrderId(chk.lastPageUrl());
        if (orderId == null) {
            out.add("prove TC_CHK_02 lastPageUrl did not contain a concrete order id: "
                    + chk.lastPageUrl());
            return out;
        }
        report.append("prove orderId=").append(orderId).append(" from ").append(chk.lastPageUrl()).append('\n');
        HttpResponse<String> order = httpGet(baseUrl + "/api/orders/" + orderId);
        if (order.statusCode() != 200 || !order.body().contains("\"" + orderId + "\"")) {
            out.add("prove shop GET /api/orders/" + orderId + " was " + order.statusCode()
                    + " " + order.body());
        } else if (!order.body().contains("buyer.a@example.test")) {
            out.add("prove order " + orderId + " was not owned by buyer.a@example.test: "
                    + order.body());
        } else {
            report.append("prove shop order json=").append(order.body()).append('\n');
        }
        LinkedHashSet<String> live = listLiveOrderIds();
        report.append("prove live shop orders=").append(live).append('\n');
        TcDraft ord = drafts.get("TC_ORD_01");
        if (ord != null && (ord.status() == TcDraftStatus.PASSED || ord.status() == TcDraftStatus.REUSED)) {
            String accountUrl = ord.lastPageUrl() == null ? "" : ord.lastPageUrl();
            if (!accountUrl.contains("account.html")) {
                out.add("prove TC_ORD_01 lastPageUrl was not the account page: " + accountUrl);
            }
            boolean mentioned = accountUrl.contains(orderId)
                    || snapshotMentions(findProjectRoot("kla_chain"), "TC_ORD_01", orderId);
            boolean onlyThisOrder = live.size() == 1 && live.contains(orderId);
            if (!mentioned && !onlyThisOrder) {
                out.add("prove TC_ORD_01 did not show the same id " + orderId
                        + "; liveOrders=" + live + " lastPage=" + accountUrl);
            } else {
                report.append("prove account same-id ").append(orderId)
                        .append(" mentioned=").append(mentioned)
                        .append(" onlyLiveOrder=").append(onlyThisOrder).append('\n');
            }
        }
        TcDraft ses = drafts.get("TC_SES_01");
        if (ses != null && (ses.status() == TcDraftStatus.PASSED || ses.status() == TcDraftStatus.REUSED)) {
            boolean overlay = snapshotMentions(findProjectRoot("kla_chain"), "TC_SES_01",
                    "Session expired")
                    || (ses.provenSteps() != null && ses.provenSteps().stream().anyMatch(s ->
                    s.assertionExpected() != null
                            && s.assertionExpected().toLowerCase().contains("session expired")));
            boolean identityGone = snapshotMentions(findProjectRoot("kla_chain"), "TC_SES_01",
                    "Not signed in")
                    || !snapshotMentions(findProjectRoot("kla_chain"), "TC_SES_01",
                    "buyer.a@example.test");
            if (!overlay) {
                out.add("prove TC_SES_01 did not record the session-expired overlay");
            }
            if (!identityGone) {
                out.add("prove TC_SES_01 still showed buyer.a@example.test after expiry");
            }
            if (overlay && identityGone) {
                report.append("prove session expiry overlay+identity cleared\n");
            }
        }
        return out;
    }

    private List<String> verifyReplayBusinessOutcomes(
            LocalAcceptanceReplay.MavenRun replayRun, Path zipCopied) throws Exception {
        List<String> out = new ArrayList<>();
        String log = replayRun.log() == null ? "" : replayRun.log();
        Path replayDir = SHOP_DIR.resolve("replay-scoped-chain");
        String combined = log;
        if (Files.isDirectory(replayDir)) {
            combined = combined + "\n" + readSmallFiles(replayDir.resolve("test-output"));
        }
        Matcher ids = ORDER_ID.matcher(combined);
        LinkedHashSet<String> found = new LinkedHashSet<>();
        while (ids.find()) {
            found.add(ids.group());
        }
        report.append("replay log order ids=").append(found).append('\n');
        String confirmed = null;
        for (String id : found) {
            HttpResponse<String> order = httpGet(baseUrl + "/api/orders/" + id);
            if (order.statusCode() == 200 && order.body().contains("buyer.a@example.test")) {
                confirmed = id;
                report.append("replay shop order ").append(id).append("=").append(order.body()).append('\n');
                break;
            }
        }
        Map<String, String> statuses = replayRun.statuses();
        if ("PASS".equalsIgnoreCase(String.valueOf(statuses.get("Simulated_payment_order")))) {
            if (confirmed == null) {
                out.add("replay created no shop order KLA-* owned by buyer.a@example.test; ids="
                        + found);
            }
        }
        if ("PASS".equalsIgnoreCase(String.valueOf(statuses.get("Order_on_account")))
                && confirmed != null) {
            boolean same = combined.contains(confirmed)
                    && (combined.contains("account.html") || combined.toLowerCase().contains("order "
                    + confirmed.toLowerCase()) || combined.contains("Order " + confirmed));
            if (!same && !snapshotMentions(replayDir, "TC_ORD_01", confirmed)
                    && !combined.contains(confirmed)) {
                out.add("replay Order_on_account did not show the same order id " + confirmed);
            }
        }
        if ("PASS".equalsIgnoreCase(String.valueOf(statuses.get("Session_expiry_overlay")))) {
            boolean overlay = combined.contains("Session expired")
                    || snapshotMentions(replayDir, "TC_SES_01", "Session expired");
            boolean stillAuthed = combined.contains("session-email\">buyer.a@example.test")
                    || combined.contains("account-email\">buyer.a@example.test");
            if (!overlay) {
                out.add("replay Session_expiry_overlay did not record the overlay text");
            }
            if (stillAuthed) {
                out.add("replay Session_expiry_overlay still showed the authenticated email");
            }
        }
        report.append("replay zip=").append(zipCopied).append('\n');
        return out;
    }

    private static boolean snapshotMentions(Path root, String tcId, String needle) {
        if (root == null || !Files.isDirectory(root) || needle == null || needle.isBlank()) {
            return false;
        }
        String key = tcId.replace("_", "");
        try (var walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> {
                        String n = p.getFileName().toString().toLowerCase();
                        String s = p.toString();
                        return s.contains(tcId) || s.contains(tcId.replace("_", "~5F"))
                                || n.contains(key.toLowerCase())
                                || n.endsWith(".json") || n.endsWith(".txt") || n.endsWith(".log")
                                || n.endsWith(".html") || n.endsWith(".xml") || n.endsWith(".jsonl");
                    })
                    .limit(400)
                    .anyMatch(p -> {
                        try {
                            String t = Files.readString(p, StandardCharsets.UTF_8);
                            return t.contains(needle);
                        } catch (Exception e) {
                            return false;
                        }
                    });
        } catch (Exception e) {
            return false;
        }
    }

    private static String readSmallFiles(Path root) {
        if (root == null || !Files.isDirectory(root)) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        try (var walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> {
                        String n = p.getFileName().toString().toLowerCase();
                        return n.endsWith(".log") || n.endsWith(".txt") || n.endsWith(".xml")
                                || n.endsWith(".html") || n.endsWith(".jsonl");
                    })
                    .limit(80)
                    .forEach(p -> {
                        try {
                            if (Files.size(p) > 2_000_000) {
                                return;
                            }
                            sb.append('\n').append(Files.readString(p, StandardCharsets.UTF_8));
                        } catch (Exception ignored) {
                        }
                    });
        } catch (Exception ignored) {
        }
        return sb.toString();
    }

    private LinkedHashSet<String> listLiveOrderIds() throws Exception {
        LinkedHashSet<String> live = new LinkedHashSet<>();
        for (int n = 1001; n <= 1020; n++) {
            String id = "KLA-" + n;
            HttpResponse<String> order = httpGet(baseUrl + "/api/orders/" + id);
            if (order.statusCode() == 200 && order.body().contains("\"" + id + "\"")) {
                live.add(id);
            }
        }
        return live;
    }

    private static String firstOrderId(String text) {
        if (text == null) {
            return null;
        }
        Matcher m = ORDER_ID.matcher(text);
        return m.find() ? m.group() : null;
    }

    private HttpResponse<String> httpGet(String url) throws Exception {
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        return http.send(HttpRequest.newBuilder(URI.create(url)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private String scopedResultsMarkdown(
            String sha,
            ConversionJobResult prove,
            Map<String, TcDraft> drafts,
            LocalAcceptanceReplay.MavenRun replayRun,
            List<String> failures
    ) {
        StringBuilder md = new StringBuilder();
        md.append("# Scoped checkout chain — results\n\n");
        md.append("Launch **HOLD**. Not a launch-gate close. No UPDATE / UI-TARS / Cursor / full pack.\n\n");
        md.append("Shop: `").append(baseUrl).append("`\n");
        md.append("Evidence: `").append(runEvidence).append("`\n");
        md.append("ZIP sha256: `").append(sha).append("`\n");
        md.append("Prove passed=").append(prove.passed()).append(" todo=").append(prove.todo())
                .append(" message=").append(prove.message()).append("\n\n");
        md.append("| TC | prove status | lastPageUrl |\n|---|---|---|\n");
        for (String id : SCOPED_TC_ORDER) {
            TcDraft d = drafts.get(id);
            md.append("| ").append(id).append(" | ")
                    .append(d == null ? "MISSING" : d.status())
                    .append(" | ").append(d == null ? "" : d.lastPageUrl()).append(" |\n");
        }
        md.append("\nReplay exit=").append(replayRun.exitCode())
                .append(" statuses=").append(replayRun.statuses()).append("\n\n");
        if (failures.isEmpty()) {
            md.append("Failures: none\n");
        } else {
            md.append("Failures:\n");
            for (String f : failures) {
                md.append("- ").append(f).append('\n');
            }
        }
        return md.toString();
    }

    private void oracleWalk() throws Exception {
        java.net.CookieManager cookies = new java.net.CookieManager();
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .cookieHandler(cookies)
                .build();
        HttpResponse<String> home = http.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        Assert.assertEquals(home.statusCode(), 200, home.body());
        Assert.assertTrue(home.body().contains("Accept cookies"), home.body());
        Assert.assertTrue(home.body().contains("Sign in now"), home.body());
        http.send(HttpRequest.newBuilder(URI.create(baseUrl + "/api/cookies"))
                        .POST(HttpRequest.BodyPublishers.ofString("{}"))
                        .header("Content-Type", "application/json").build(),
                HttpResponse.BodyHandlers.ofString());
        HttpResponse<String> bad = http.send(HttpRequest.newBuilder(URI.create(baseUrl + "/api/login"))
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"email\":\"buyer.a@example.test\",\"password\":\"WrongPass_xxx\"}"))
                        .header("Content-Type", "application/json").build(),
                HttpResponse.BodyHandlers.ofString());
        Assert.assertEquals(bad.statusCode(), 401, bad.body());
        Assert.assertTrue(bad.body().contains("Invalid email or password"), bad.body());
        HttpResponse<String> login = http.send(HttpRequest.newBuilder(URI.create(baseUrl + "/api/login"))
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"email\":\"buyer.a@example.test\",\"password\":\"SyntheticPass_A1!\"}"))
                        .header("Content-Type", "application/json").build(),
                HttpResponse.BodyHandlers.ofString());
        Assert.assertEquals(login.statusCode(), 200, login.body());
        HttpResponse<String> session = http.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/api/session")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        Assert.assertTrue(session.body().contains("buyer.a@example.test"), session.body());
        report.append("oracle HTTP home=200 session=").append(session.body()).append('\n');
    }

    private void compileAndStartShop() throws Exception {
        ProcessBuilder javac = new ProcessBuilder("javac", "ShopServer.java");
        javac.directory(SHOP_DIR.toFile());
        javac.redirectErrorStream(true);
        Process c = javac.start();
        String compileOut = new String(c.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        Assert.assertEquals(c.waitFor(), 0, compileOut);
        startShopProcess("127.0.0.1", "0");
    }

    private void restartShopSamePort() throws Exception {
        URI uri = URI.create(baseUrl);
        String host = uri.getHost() == null ? "127.0.0.1" : uri.getHost();
        int port = uri.getPort();
        if (shopProcess != null && shopProcess.isAlive()) {
            shopProcess.destroy();
            shopProcess.waitFor(8, TimeUnit.SECONDS);
            if (shopProcess.isAlive()) {
                shopProcess.destroyForcibly();
            }
        }
        Thread.sleep(400);
        startShopProcess(host, String.valueOf(port));
        report.append("shop restarted on ").append(baseUrl).append('\n');
    }

    private void startShopProcess(String host, String port) throws Exception {
        ProcessBuilder run = new ProcessBuilder("java", "ShopServer", "web", host, port);
        run.directory(SHOP_DIR.toFile());
        run.redirectErrorStream(true);
        shopProcess = run.start();
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(shopProcess.getInputStream(), StandardCharsets.UTF_8));
        long deadline = System.currentTimeMillis() + 20_000;
        String line;
        String started = null;
        while (System.currentTimeMillis() < deadline) {
            if (reader.ready()) {
                line = reader.readLine();
                if (line != null && line.startsWith("SHOP_URL=")) {
                    started = line.substring("SHOP_URL=".length()).trim();
                    break;
                }
            } else if (!shopProcess.isAlive()) {
                throw new IllegalStateException("shop exited: " + new String(
                        shopProcess.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
            } else {
                Thread.sleep(50);
            }
        }
        Assert.assertNotNull(started, "shop did not print SHOP_URL");
        baseUrl = started;
        new Thread(() -> {
            try {
                while (reader.readLine() != null) {
                    // drain
                }
            } catch (Exception ignored) {
            }
        }, "shop-stdout").start();
    }

    private Path precisionPackExcel() throws Exception {
        Path excel = SHOP_DIR.resolve("pack-precision.xlsx");
        ManualTcExcelWriter.write(excel, List.of(
                caseAcc02(),
                caseAcc03()
        ));
        return excel;
    }

    private static List<ManualTestCase> newPack() {
        return List.of(
                caseAcc01(),
                caseAcc03(),
                caseAcc04(),
                caseVis01(),
                caseVar01(),
                caseAcc02(),
                caseCart01(),
                caseCart02("2", "2"),
                caseCart03(),
                caseChk01(),
                caseChk02(),
                caseOrd01(),
                caseSes01()
        );
    }

    private static List<ManualTestCase> updatePack() {
        List<ManualTestCase> out = new ArrayList<>();
        for (ManualTestCase tc : newPack()) {
            if ("TC_CART_01".equals(tc.tcId())) {
                out.add(caseCart01Red());
            } else if ("TC_CART_02".equals(tc.tcId())) {
                out.add(caseCart02("3", "3"));
            } else {
                out.add(tc);
            }
        }
        return out;
    }

    private static ManualTestCase caseAcc01() {
        return tc("TC_ACC_01", "Create account", "",
                """
                        1. Click Accept cookies
                        2. Click Register
                        3. Enter in the Name field
                        4. Enter in the Email field
                        5. Enter 'SyntheticPass_N1!' in the Password field
                        6. Click Create account
                        7. Confirm the text Account created is visible
                        """,
                "Header shows buyer.new@example.test and Account created is visible",
                "\n\nBuyer New\nbuyer.new@example.test\n",
                "");
    }

    private static ManualTestCase caseAcc02() {
        return tc("TC_ACC_02", "Login", "",
                """
                        1. Click Accept cookies
                        2. Enter in the Email field
                        3. Enter 'SyntheticPass_A1!' in the Password field
                        4. Click Sign in now
                        5. Confirm the text buyer.a@example.test is visible
                        """,
                "Header shows buyer.a@example.test and the login form is gone",
                "\nbuyer.a@example.test\n",
                "");
    }

    private static ManualTestCase caseAcc03() {
        return tc("TC_ACC_03", "Invalid login", "",
                """
                        1. Click Accept cookies
                        2. Enter in the Email field
                        3. Enter 'WrongPass_xxx' in the Password field
                        4. Click Sign in now
                        5. Confirm the text Invalid email or password is visible
                        """,
                "Invalid email or password is shown after the delay; buyer is not signed in",
                "\nbuyer.a@example.test\n",
                "");
    }

    private static ManualTestCase caseAcc04() {
        return tc("TC_ACC_04", "Duplicate register", "",
                """
                        1. Click Accept cookies
                        2. Click Register
                        3. Enter in the Name field
                        4. Enter in the Email field
                        5. Enter 'SyntheticPass_A1!' in the Password field
                        6. Click Create account
                        7. Confirm the text Email already registered is visible
                        """,
                "Email already registered is shown; no new authenticated session for this email",
                "\n\nBuyer A\nbuyer.a@example.test\n",
                "");
    }

    private static ManualTestCase caseVar01() {
        return tc("TC_VAR_01", "Jacket Medium Blue", "",
                """
                        1. Click Accept cookies
                        2. Enter in the Email field
                        3. Enter 'SyntheticPass_A1!' in the Password field
                        4. Click Sign in now
                        5. Click View Trail Jacket
                        6. Click Size
                        7. Click Medium
                        8. Click Color
                        9. Click Blue
                        10. Click Add to cart
                        11. Confirm the text Added is visible
                        12. Click Cart
                        13. Confirm the text Trail Jacket is visible
                        14. Confirm the text Medium is visible
                        15. Confirm the text Blue is visible
                        """,
                "Cart line is Trail Jacket Medium Blue, not the hidden Add to cart decoy",
                "\nbuyer.a@example.test\n",
                "");
    }

    private static ManualTestCase caseCart01() {
        return caseCart01Color("Blue");
    }

    private static ManualTestCase caseCart01Red() {
        return caseCart01Color("Red");
    }

    private static ManualTestCase caseCart01Color(String color) {
        return tc("TC_CART_01", "Add two products", "",
                """
                        1. Click Accept cookies
                        2. Enter in the Email field
                        3. Enter 'SyntheticPass_A1!' in the Password field
                        4. Click Sign in now
                        5. Click View Trail Jacket
                        6. Click Size
                        7. Click Medium
                        8. Click Color
                        9. Click %s
                        10. Click Add to cart
                        11. Confirm the text Added is visible
                        12. Click Keel Local Shop
                        13. Click View Canvas Tote
                        14. Click Add to cart
                        15. Confirm the text Added is visible
                        16. Click Cart
                        17. Confirm the text Trail Jacket is visible
                        18. Confirm the text Canvas Tote is visible
                        """.formatted(color),
                "Cart shows two lines: Trail Jacket Medium %s and Canvas Tote".formatted(color),
                "\nbuyer.a@example.test\n",
                "");
    }

    private static ManualTestCase caseCart02(String qtyStep, String expectedQty) {
        return tc("TC_CART_02", "Change tote quantity", "Two lines in cart",
                """
                        1. Click Cart
                        2. Enter '%s' in the Tote quantity field
                        3. Confirm the text Canvas Tote is visible
                        """.formatted(qtyStep),
                "Tote quantity is " + expectedQty + " and Trail Jacket quantity remains 1",
                "",
                "TC_CART_01");
    }

    private static ManualTestCase caseCart03() {
        return tc("TC_CART_03", "Remove second Remove", "Two lines in cart",
                """
                        1. Click Cart
                        2. Click Remove tote
                        3. Confirm the text Trail Jacket is visible
                        """,
                "The tote line is gone; Trail Jacket remains",
                "",
                "TC_CART_02");
    }

    private static ManualTestCase caseChk01() {
        return tc("TC_CHK_01", "Checkout required address", "Cart has items",
                """
                        1. Click Cart
                        2. Click Checkout
                        3. Click Submit blank address
                        4. Confirm the text Address is required is visible
                        """,
                "After the delay, Address is required is visible and no Order KLA id is shown",
                "",
                "TC_CART_03");
    }

    private static ManualTestCase caseChk02() {
        return tc("TC_CHK_02", "Simulated payment order", "Cart has items",
                """
                        1. Click Cart
                        2. Click Checkout
                        3. Enter in the Address field
                        4. Enter in the City field
                        5. Enter in the Zip field
                        6. Click Pay later (simulated)
                        7. Confirm the text Order KLA- is visible
                        """,
                "Confirmation shows Order KLA- and buyer.a@example.test; no card fields",
                "\n\n1 Test Pilot Lane\nTestville\n00000\n",
                "TC_CHK_01");
    }

    private static ManualTestCase caseOrd01() {
        return tc("TC_ORD_01", "Order on account", "Order just placed",
                """
                        1. Capture from id=order-id as orderId using exact text
                        2. Click Account
                        3. Compare captured orderId on css=#order-list li using exact text
                        """,
                "Account order list shows the captured order id as exact text",
                "",
                "TC_CHK_02");
    }

    private static ManualTestCase caseSes01() {
        return tc("TC_SES_01", "Session expiry overlay", "Signed in",
                """
                        1. Click Account
                        2. Click Expire session now
                        3. Confirm the text Session expired — sign in again is visible
                        4. Assert signed-out on id=session-email expected empty
                        5. Assert signed-out on id=account-email expected text Not signed in
                        """,
                "Session expired overlay is visible and signed-out locators match empty header and Not signed in",
                "",
                "TC_ORD_01");
    }

    private static ManualTestCase caseVis01() {
        return tc("TC_VIS_01", "Unlabeled pay glyph", "",
                """
                        1. Click Accept cookies
                        2. Open /vision.html
                        3. Click the unmarked payment glyph
                        4. Confirm the text Glyph hit is visible
                        """,
                "UI-TARS is invoked for the empty bag button; Glyph hit is visible if the glyph is hit",
                "",
                "");
    }

    private static ManualTestCase tc(
            String id, String title, String pre, String steps, String expected, String data, String callBefore) {
        return new ManualTestCase(
                id, title, pre, steps, expected, "P1", "local-acceptance", "", data, "AUTOMATE", callBefore);
    }

    private String resultsMarkdown(
            String newSha,
            String updateSha,
            ConversionJobResult newResult,
            ConversionJobResult updateResult,
            ConversionJobResult visResult,
            ConversionJobResult precResult,
            LocalAcceptanceReplay.MavenRun newReplay,
            LocalAcceptanceReplay.MavenRun updateReplay,
            boolean visionInvoked
    ) throws Exception {
        StringBuilder md = new StringBuilder();
        md.append("# Controlled local acceptance — results\n\n");
        md.append("**Label:** controlled local fixture. **Not** representative third-party validation. ");
        md.append("Launch **HOLD**. Candidate `2721e6d` unchanged. No commit.\n\n");
        md.append("Shop: `").append(baseUrl).append("`\n\n");
        md.append("| Mode | passed | todo | message |\n|---|---|---|---|\n");
        row(md, "A DOM NEW", newResult);
        row(md, "A DOM UPDATE", updateResult);
        row(md, "B UI-TARS", visResult);
        row(md, "C Precision key unset", precResult);
        md.append("\nVision actually invoked: **").append(visionInvoked).append("**\n\n");
        md.append("Cursor execution: **not tested** (key unset). No credentials recorded.\n\n");
        md.append("| Artifact | SHA-256 |\n|---|---|\n");
        md.append("| DOM NEW zip | `").append(newSha).append("` |\n");
        md.append("| DOM UPDATE zip | `").append(updateSha).append("` |\n\n");
        md.append("NEW replay exit=").append(newReplay.exitCode()).append(" ").append(newReplay.statuses()).append("\n\n");
        md.append("UPDATE replay exit=").append(updateReplay.exitCode()).append(" ").append(updateReplay.statuses()).append("\n\n");
        md.append("IR snapshot:\n\n```\n");
        Path root = findProjectRoot(PROJECT_DOM);
        for (TcDraft d : ReuseEligibility.loadStoredDrafts(root).values()) {
            md.append(d.tcId()).append(' ').append(d.status()).append(' ')
                    .append(d.precisionFallback() ? d.precisionFallbackReason() : "")
                    .append('\n');
        }
        md.append("```\n\n");
        md.append("## Limitations\n\n");
        md.append("- Controlled local fixture only. Not representative third-party application validation.\n");
        md.append("- TYPE_PASS still maps to `${TARGET_PASSWORD}` unless the Excel step quotes a literal; pack uses quoted synthetic passwords.\n");
        md.append("- Generated ZIP replay does not emit Excel `Open /path` navigations; login form is on the home page so replay can type without that step.\n");
        md.append("- Identical repeated `Remove` labels are not ordinal-bound; tote uses `Remove tote`.\n");
        md.append("- ConversionJobRunner proves Excel order without re-expanding Call-before; dependents must be a contiguous chain or they start a fresh browser.\n");
        md.append("- `DELIVERY_POST_ACTION_WAIT_MS=2000` in this JVM only (delayed errors are 1200–1500ms).\n");
        md.append("- Launch remains **HOLD**. No commit, publish, candidate replace, or gate close.\n\n");
        md.append("See `").append(runEvidence != null ? runEvidence : EVIDENCE).append("/run-log.txt`.\n");
        return md.toString();
    }

    private static void row(StringBuilder md, String name, ConversionJobResult r) {
        md.append("| ").append(name).append(" | ").append(r.passed()).append(" | ")
                .append(r.todo()).append(" | ").append(r.message() == null ? "" : r.message().replace("|", "/"))
                .append(" |\n");
    }

    private static List<String> prefix(String label, List<String> items) {
        List<String> out = new ArrayList<>();
        for (String item : items) {
            out.add(label + ": " + item);
        }
        return out;
    }

    private static void unzip(Path zip, Path dest) throws Exception {
        Files.createDirectories(dest);
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                Path out = dest.resolve(entry.getName()).normalize();
                if (!out.startsWith(dest)) {
                    throw new IllegalStateException("zip slip");
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(out);
                } else {
                    Files.createDirectories(out.getParent());
                    Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static void upsert(Path file, Map<String, String> values) throws Exception {
        Map<String, String> merged = new LinkedHashMap<>();
        if (Files.isRegularFile(file)) {
            for (String line : Files.readString(file, StandardCharsets.UTF_8).split("\\R")) {
                int eq = line.indexOf('=');
                if (eq > 0 && !line.trim().startsWith("#")) {
                    merged.put(line.substring(0, eq).trim(), line.substring(eq + 1));
                }
            }
        }
        merged.putAll(values);
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : merged.entrySet()) {
            sb.append(e.getKey()).append('=').append(e.getValue()).append('\n');
        }
        Files.createDirectories(file.getParent());
        Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
    }

    private static boolean envKeyPresent() {
        String v = System.getenv("CURSOR_API_KEY");
        return v != null && !v.isBlank();
    }

    private static String sha256(Path file) throws Exception {
        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
        try (var in = Files.newInputStream(file)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) >= 0) {
                digest.update(buf, 0, n);
            }
        }
        StringBuilder hex = new StringBuilder();
        for (byte b : digest.digest()) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

    private static String tail(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(s.length() - max);
    }
}
