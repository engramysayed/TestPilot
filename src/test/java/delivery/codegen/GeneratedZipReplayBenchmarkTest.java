package delivery.codegen;

import delivery.authoring.AuthoringEngine;
import delivery.excel.ManualTestCase;
import delivery.ir.TcDraft;
import delivery.ir.TcDraftStatus;
import delivery.job.ConversionJobRequest;
import delivery.job.ConversionJobResult;
import delivery.job.EmitPhase;
import delivery.job.JobProgressTracker;
import delivery.job.OccurrenceIdentity;
import delivery.packager.FrameworkPackager;
import delivery.store.ProjectStore;
import org.json.JSONArray;
import org.json.JSONObject;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Downloaded NEW → {@code mvn clean test} → UPDATE → {@code mvn clean test} against
 * controlled local pages for the 2026-09-21 codegen fixes.
 *
 * <p><b>UPDATE pipeline label:</b> this benchmark regenerates fixture IR drafts and calls
 * {@link EmitPhase} with {@code overlayCustomerConfig}. It does <em>not</em> invoke
 * {@link delivery.job.ReuseEligibility#authorIds} or {@link delivery.job.ReuseEligibility#copyForReuse}.
 * Real UPDATE reuse is covered by {@link CodegenUpdateReuseIntegrationTest}.
 */
public class GeneratedZipReplayBenchmarkTest {
    static final String CANARY = "CANARY_PW_zipreplay_7f2c9a";
    static final String CANARY_ASSERT = "CANARY_ASSERT_zipreplay_7f2c9a";
    static final String CANARY_FAIL = "CANARY_FAIL_EXPECT_zipreplay_7f2c9a";

    private static final Path TEMPLATE = Path.of("customer-framework-template");
    private static final Path PAGES = Path.of("docs/reviews/codegen-review/zip-replay/pages");
    private static final Path EVIDENCE = Path.of("target/codegen-review/zip-benchmark");
    private static final String PROJECT_NEW = "codegen_zip_replay";
    private static final String PROJECT_FAIL = "codegen_zip_fail";

    private com.sun.net.httpserver.HttpServer server;
    private final AtomicBoolean bodyProbeHit = new AtomicBoolean();
    private String baseUrl;
    private Path workRoot;
    private Path storeRoot;
    private ConversionJobResult versionA;
    private ConversionJobResult versionB;
    private String versionASha256;
    private String versionBSha256;
    private String failSha256;
    private MavenRun newRun;
    private MavenRun updateRun;
    private MavenRun failRun;

    @BeforeClass
    public void startFixtureServer() throws Exception {
        Assert.assertTrue(Files.isDirectory(PAGES), "fixture pages missing: " + PAGES.toAbsolutePath());
        Files.createDirectories(EVIDENCE);
        server = com.sun.net.httpserver.HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        Path pages = PAGES.toAbsolutePath().normalize();
        server.createContext("/hit/", exchange -> {
            if ("/hit/body".equals(exchange.getRequestURI().getPath())) {
                bodyProbeHit.set(true);
            }
            byte[] ok = "ok".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
            exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, ok.length);
            exchange.getResponseBody().write(ok);
            exchange.close();
        });
        server.createContext("/", exchange -> {
            String rel = exchange.getRequestURI().getPath();
            if (rel == null || rel.equals("/")) {
                rel = "/index.html";
            }
            Path file = pages.resolve(rel.substring(1)).normalize();
            if (!file.startsWith(pages) || !Files.isRegularFile(file)) {
                exchange.sendResponseHeaders(HttpURLConnection.HTTP_NOT_FOUND, -1);
                exchange.close();
                return;
            }
            byte[] bytes = Files.readAllBytes(file);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        workRoot = Files.createTempDirectory("codegen-zip-replay");
        storeRoot = Files.createTempDirectory("codegen-zip-store");
    }

    @AfterClass(alwaysRun = true)
    public void stopFixtureServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    public void newDownloadExtractAndMvnCleanTest() throws Exception {
        List<ManualTestCase> cases = happyCases();
        versionA = emit("NEW", workRoot.resolve("new"), cases, happyDrafts(workRoot.resolve("new")), PROJECT_NEW);
        Assert.assertTrue(Files.isRegularFile(versionA.zipFile()), "NEW zip missing");
        versionASha256 = sha256(versionA.zipFile());
        Files.copy(versionA.zipFile(), EVIDENCE.resolve("new.zip"), StandardCopyOption.REPLACE_EXISTING);
        Files.writeString(EVIDENCE.resolve("new.sha256"), versionASha256 + "\n", StandardCharsets.UTF_8);

        Path project = workRoot.resolve("new/project");
        assertPrerequisiteKeys(project);
        assertCoverageSource(project);

        Path replay = unzip(versionA.zipFile(), workRoot.resolve("replay-new"));
        prepareReplay(replay);
        newRun = mvnCleanTest(replay);
        Files.writeString(EVIDENCE.resolve("new-mvn.log"), newRun.log, StandardCharsets.UTF_8);
        Assert.assertEquals(newRun.exitCode, 0, "NEW mvn clean test expected 0:\n" + tail(newRun.log));
        assertDownloadedCounts(replay, newRun, happyIds().size(), "NEW");
        for (String id : happyIds()) {
            Assert.assertEquals(statusOf(newRun.statuses, id), "PASS",
                    id + " expected PASS, got " + newRun.statuses + "\n" + tail(newRun.log));
        }
        assertCanaryAbsent(replay, "NEW");
        recordCanaryRemainder(replay, newRun.log, EVIDENCE.resolve("new-canary-remainder.txt"));
        assertAllureReporting(replay, EVIDENCE.resolve("new-allure-steps.json"),
                AllureExpect.passedType(), AllureExpect.passedSelect(), AllureExpect.passedAssert());
        Assert.assertTrue(countQuitMarkers(replay) >= happyIds().size(),
                "NEW replay should quit a browser per generated test, markers="
                        + countQuitMarkers(replay) + "\n" + tail(newRun.log));
    }

    /**
     * Fixture regeneration + emit overlay, not {@code ReuseEligibility} UPDATE reuse.
     */
    @Test(dependsOnMethods = "newDownloadExtractAndMvnCleanTest")
    public void updateDownloadExtractAndMvnCleanTest() throws Exception {
        List<ManualTestCase> cases = updateCases();
        versionB = emit("UPDATE", workRoot.resolve("update"), cases,
                updateDrafts(workRoot.resolve("update")), PROJECT_NEW);
        versionBSha256 = sha256(versionB.zipFile());
        Files.copy(versionB.zipFile(), EVIDENCE.resolve("update.zip"), StandardCopyOption.REPLACE_EXISTING);
        Files.writeString(EVIDENCE.resolve("update.sha256"), versionBSha256 + "\n", StandardCharsets.UTF_8);
        Assert.assertNotEquals(versionBSha256, versionASha256, "UPDATE zip must differ from NEW");

        Path replay = unzip(versionB.zipFile(), workRoot.resolve("replay-update"));
        Assert.assertTrue(Files.isRegularFile(
                replay.resolve("src/test/java/project/tests/generated/TC_UPD_SAVE.java")));
        prepareReplay(replay);
        updateRun = mvnCleanTest(replay);
        Files.writeString(EVIDENCE.resolve("update-mvn.log"), updateRun.log, StandardCharsets.UTF_8);
        Assert.assertEquals(updateRun.exitCode, 0, "UPDATE mvn clean test expected 0:\n" + tail(updateRun.log));
        List<String> ids = new ArrayList<>(happyIds());
        ids.add("TC_UPD_SAVE");
        assertDownloadedCounts(replay, updateRun, ids.size(), "UPDATE");
        for (String id : ids) {
            Assert.assertEquals(statusOf(updateRun.statuses, id), "PASS",
                    id + " expected PASS, got " + updateRun.statuses + "\n" + tail(updateRun.log));
        }
        assertCanaryAbsent(replay, "UPDATE");
        recordCanaryRemainder(replay, updateRun.log, EVIDENCE.resolve("update-canary-remainder.txt"));
        assertAllureReporting(replay, EVIDENCE.resolve("update-allure-steps.json"),
                AllureExpect.passedType(), AllureExpect.passedSelect(), AllureExpect.passedAssert());
        assertPrerequisiteKeys(workRoot.resolve("update/project"));
    }

    @Test(dependsOnMethods = "newDownloadExtractAndMvnCleanTest")
    public void deliberateFailuresExitNonzeroSkipBodyAndQuit() throws Exception {
        bodyProbeHit.set(false);
        List<ManualTestCase> cases = failCases();
        ConversionJobResult emitted = emit("NEW", workRoot.resolve("fail"), cases,
                failDrafts(workRoot.resolve("fail")), PROJECT_FAIL);
        failSha256 = sha256(emitted.zipFile());
        Files.copy(emitted.zipFile(), EVIDENCE.resolve("fail.zip"), StandardCopyOption.REPLACE_EXISTING);
        Files.writeString(EVIDENCE.resolve("fail.sha256"), failSha256 + "\n", StandardCharsets.UTF_8);

        Path replay = unzip(emitted.zipFile(), workRoot.resolve("replay-fail"));
        String leaf = Files.readString(
                replay.resolve("src/test/java/project/tests/generated/TC_FAIL_SETUP_LEAF.java"));
        Assert.assertTrue(leaf.contains("click_Body_Probe_Button") || leaf.contains("body-probe"), leaf);
        Assert.assertTrue(leaf.contains("@AfterMethod(alwaysRun = true)"), leaf);
        prepareReplay(replay);
        failRun = mvnCleanTest(replay);
        Files.writeString(EVIDENCE.resolve("fail-mvn.log"), failRun.log, StandardCharsets.UTF_8);
        Assert.assertNotEquals(failRun.exitCode, 0, "deliberate failures must fail Maven:\n" + tail(failRun.log));
        assertNoInternalSelfTests(replay);
        Assert.assertEquals(countGeneratedTests(replay), failIds().size(),
                "FAIL downloaded test count must match generated cases");
        Assert.assertEquals(statusOf(failRun.statuses, "TC_FAIL_CLICK"), "FAIL", failRun.statuses.toString());
        Assert.assertEquals(statusOf(failRun.statuses, "TC_FAIL_ABSENCE"), "FAIL", failRun.statuses.toString());
        Assert.assertEquals(statusOf(failRun.statuses, "TC_FAIL_EXPECT"), "FAIL", failRun.statuses.toString());
        Assert.assertEquals(statusOf(failRun.statuses, "TC_FAIL_TYPE"), "FAIL", failRun.statuses.toString());
        Assert.assertEquals(statusOf(failRun.statuses, "TC_FAIL_SELECT"), "FAIL", failRun.statuses.toString());
        Assert.assertEquals(statusOf(failRun.statuses, "TC_SETUP_BAD"), "FAIL", failRun.statuses.toString());
        Assert.assertEquals(statusOf(failRun.statuses, "TC_FAIL_SETUP_LEAF"), "SKIP",
                "leaf @Test body is skipped after configuration failure, not a failed test method: "
                        + failRun.statuses + " config=" + failRun.configFailures + "\n" + tail(failRun.log));
        Assert.assertTrue(failRun.configFailures.containsKey("TC_FAIL_SETUP_LEAF"),
                "leaf configuration failure must be recorded separately: " + failRun.configFailures);
        Assert.assertFalse(bodyProbeHit.get(), "leaf body click must not run after setup failure");
        Assert.assertTrue(countQuitMarkers(replay) > 0,
                "browsers must still quit after failures, markers=" + countQuitMarkers(replay));
        recordCanaryRemainder(replay, failRun.log, EVIDENCE.resolve("fail-canary-remainder.txt"));
        assertAllureReporting(replay, EVIDENCE.resolve("fail-allure-steps.json"),
                AllureExpect.failedType(), AllureExpect.failedSelect(), AllureExpect.failedAssert());
        Assert.assertTrue(failRun.log.contains(CANARY_FAIL) || remainderContains(replay, CANARY_FAIL),
                "failure messages/reports must include the assertion canary:\n" + tail(failRun.log));
    }

    @Test(dependsOnMethods = {
            "newDownloadExtractAndMvnCleanTest",
            "updateDownloadExtractAndMvnCleanTest",
            "deliberateFailuresExitNonzeroSkipBodyAndQuit"
    }, alwaysRun = true)
    public void writeWorkingTreeEvidence() throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("Codegen ZIP replay benchmark (working-tree evidence)\n");
        sb.append("UPDATE pipeline=fixture-IR regenerate + EmitPhase overlay; NOT ReuseEligibility.authorIds/copyForReuse\n");
        sb.append("baseUrl=").append(baseUrl).append('\n');
        sb.append("NEW sha256=").append(versionASha256).append('\n');
        sb.append("UPDATE sha256=").append(versionBSha256).append('\n');
        sb.append("FAIL sha256=").append(failSha256).append('\n');
        if (newRun != null) {
            sb.append("NEW mvn exit=").append(newRun.exitCode).append(" statuses=").append(newRun.statuses).append('\n');
        }
        if (updateRun != null) {
            sb.append("UPDATE mvn exit=").append(updateRun.exitCode)
                    .append(" statuses=").append(updateRun.statuses).append('\n');
        }
        if (failRun != null) {
            sb.append("FAIL mvn exit=").append(failRun.exitCode)
                    .append(" testMethods=").append(failRun.statuses)
                    .append(" configFailures=").append(failRun.configFailures).append('\n');
        }
        Files.writeString(EVIDENCE.resolve("summary.txt"), sb.toString(), StandardCharsets.UTF_8);
        Files.writeString(
                Path.of("docs/reviews/codegen-review/zip-benchmark-results.txt"),
                sb.toString(),
                StandardCharsets.UTF_8);
        Assert.assertNotNull(versionASha256, "NEW zip hash missing");
    }

    private ConversionJobResult emit(
            String mode, Path work, List<ManualTestCase> cases, List<TcDraft> drafts, String projectId)
            throws Exception {
        Files.createDirectories(work);
        Path excelPath = work.resolve("cases.xlsx");
        delivery.excel.ManualTcExcelWriter.write(excelPath, cases);
        delivery.ir.TcDraftStore ir = new delivery.ir.TcDraftStore(work);
        for (TcDraft draft : drafts) {
            ir.write(draft);
        }
        Path projectDir = work.resolve("project");
        FrameworkPackager packager = new FrameworkPackager();
        packager.copyTemplate(TEMPLATE, projectDir);
        ProjectStore store = new ProjectStore(storeRoot, baseUrl);
        if ("UPDATE".equals(mode) && store.hasFramework(projectId)) {
            packager.overlayCustomerConfig(store.projectRoot(projectId).resolve("framework"), projectDir);
        }
        ConversionJobRequest req = new ConversionJobRequest(
                projectId, excelPath, baseUrl, "demo", "demo-pass",
                work, storeRoot, TEMPLATE, mode,
                "http://127.0.0.1:11434", "qwen2.5", false, false, AuthoringEngine.KEEL);
        return new EmitPhase(new JobProgressTracker()).emit(
                req, cases, work, projectDir, work.getFileName().toString(), mode);
    }

    private List<TcDraft> happyDrafts(Path work) throws Exception {
        List<ManualTestCase> rows = happyCases();
        ProvenStep setupLoginEmail = type("TC_SETUP", "LoginPage", "email", "login@example.invalid");
        ProvenStep setupLoginPass = type("TC_SETUP", "LoginPage", "password", "setup-pass");
        ProvenStep setupLoginClick = click("TC_SETUP", "LoginPage", "sign-in");
        ProvenStep setupBody = type("TC_SETUP", "LoginPage", "email", "changed@example.invalid");
        ProvenStep leafLoginEmail = type("TC_LEAF_ELIDE", "LoginPage", "email", "leaf@example.invalid");
        ProvenStep leafLoginPass = type("TC_LEAF_ELIDE", "LoginPage", "password", "leaf-pass");
        ProvenStep leafLoginClick = click("TC_LEAF_ELIDE", "LoginPage", "sign-in");
        return List.of(
                passed("TC_SETUP", rows.get(0),
                        List.of(setupBody),
                        List.of(setupLoginEmail, setupLoginPass, setupLoginClick),
                        true, work),
                passed("TC_LEAF_ELIDE", rows.get(1),
                        List.of(click("TC_LEAF_ELIDE", "Order-Details", "first")),
                        List.of(leafLoginEmail, leafLoginPass, leafLoginClick),
                        true, work),
                passed("TC_LEAF_FULL", rows.get(2),
                        List.of(click("TC_LEAF_FULL", "OrderDetails", "second")),
                        List.of(), false, work),
                passed("TC_EDIT", rows.get(3),
                        List.of(
                                type("TC_EDIT", "Profile", "email", "first@example.invalid"),
                                type("TC_EDIT", "Profile", "email", "second@example.invalid")),
                        List.of(), false, work),
                passed("TC_SIMILAR", rows.get(4),
                        List.of(
                                type("TC_SIMILAR", "Profile", "first-name", "Hyphen"),
                                type("TC_SIMILAR", "Profile", "first_name", "Underscore")),
                        List.of(), false, work),
                passed("TC_PAGES", rows.get(5),
                        List.of(
                                click("TC_PAGES", "Order-Details", "first"),
                                click("TC_PAGES", "OrderDetails", "second")),
                        List.of(), false, work),
                passed("TC_A_B", rows.get(6),
                        List.of(click("TC_A_B", "Order-Details", "first")),
                        List.of(), false, work),
                passed("TC_A__B", rows.get(7),
                        List.of(click("TC_A__B", "OrderDetails", "second")),
                        List.of(), false, work),
                passed("TC_UNICODE", rows.get(8),
                        List.of(type("TC_UNICODE", "Profile", "name", "مرحبا café")),
                        List.of(), false, work),
                passed("TC_ABSENT_OK", rows.get(9),
                        List.of(notVisible("TC_ABSENT_OK", "Errors", ".flash")),
                        List.of(), false, work),
                passed("TC_CANARY", rows.get(10),
                        List.of(type("TC_CANARY", "Profile", "secret", CANARY)),
                        List.of(), false, work),
                passed("TC_READY", rows.get(11),
                        List.of(bodyText("TC_READY", "Home", CANARY_ASSERT)),
                        List.of(), false, work),
                passed("TC_SELECT", rows.get(12),
                        List.of(select("TC_SELECT", "Home", "size", "M")),
                        List.of(), false, work)
        );
    }

    private List<TcDraft> updateDrafts(Path work) throws Exception {
        List<TcDraft> drafts = new ArrayList<>(happyDrafts(work));
        List<ManualTestCase> rows = updateCases();
        drafts.add(passed("TC_UPD_SAVE", rows.get(rows.size() - 1),
                List.of(
                        click("TC_UPD_SAVE", "Home", "save"),
                        textContains("TC_UPD_SAVE", "Home", "status", "Saved")),
                List.of(), false, work));
        return drafts;
    }

    private List<TcDraft> failDrafts(Path work) throws Exception {
        List<ManualTestCase> rows = failCases();
        return List.of(
                passed("TC_FAIL_CLICK", rows.get(0),
                        List.of(click("TC_FAIL_CLICK", "Home", "missing-button")),
                        List.of(), false, work),
                passed("TC_FAIL_ABSENCE", rows.get(1),
                        List.of(notVisible("TC_FAIL_ABSENCE", "Errors", ".error")),
                        List.of(), false, work),
                passed("TC_SETUP_BAD", rows.get(2),
                        List.of(click("TC_SETUP_BAD", "Home", "missing-setup")),
                        List.of(
                                type("TC_SETUP_BAD", "LoginPage", "email", "setupbad@example.invalid"),
                                type("TC_SETUP_BAD", "LoginPage", "password", "setup-bad"),
                                click("TC_SETUP_BAD", "LoginPage", "sign-in")),
                        true, work),
                passed("TC_FAIL_SETUP_LEAF", rows.get(3),
                        List.of(click("TC_FAIL_SETUP_LEAF", "Home", "body-probe")),
                        List.of(), false, work),
                passed("TC_FAIL_EXPECT", rows.get(4),
                        List.of(bodyText("TC_FAIL_EXPECT", "Home", CANARY_FAIL)),
                        List.of(), false, work),
                passed("TC_FAIL_TYPE", rows.get(5),
                        List.of(type("TC_FAIL_TYPE", "Home", "missing-secret", "typed-not-secret")),
                        List.of(), false, work),
                passed("TC_FAIL_SELECT", rows.get(6),
                        List.of(select("TC_FAIL_SELECT", "Home", "missing-size", "M")),
                        List.of(), false, work)
        );
    }

    private static List<ManualTestCase> happyCases() {
        return List.of(
                caseRow("TC_SETUP", "Setup login then change email", "", "login then type changed email"),
                caseRow("TC_LEAF_ELIDE", "Leaf with own login", "TC_SETUP", "click first"),
                caseRow("TC_LEAF_FULL", "Leaf inlines setup login", "TC_SETUP", "click second"),
                caseRow("TC_EDIT", "Repeated email edits", "", "type email twice"),
                caseRow("TC_SIMILAR", "Similar control names", "", "type first-name and first_name"),
                caseRow("TC_PAGES", "Colliding page names", "", "click first and second"),
                caseRow("TC_A_B", "Distinct id A_B", "", "click first"),
                caseRow("TC_A__B", "Distinct id A__B", "", "click second"),
                caseRow("TC_UNICODE", "Unicode testdata", "", "type unicode name"),
                caseRow("TC_ABSENT_OK", "Hidden flash is absent", "", "assert flash not visible"),
                caseRow("TC_CANARY", "Password canary", "", "type secret"),
                caseRow("TC_READY", "Fixture heading", "", "assert heading"),
                caseRow("TC_SELECT", "Select size", "", "select size M")
        );
    }

    private static List<ManualTestCase> updateCases() {
        List<ManualTestCase> out = new ArrayList<>(happyCases());
        out.add(caseRow("TC_UPD_SAVE", "Save after update", "", "click save"));
        return out;
    }

    private static List<ManualTestCase> failCases() {
        return List.of(
                caseRow("TC_FAIL_CLICK", "Missing click", "", "click missing"),
                caseRow("TC_FAIL_ABSENCE", "Mixed visibility absence", "", "assert error not visible"),
                caseRow("TC_SETUP_BAD", "Bad setup click", "", "click missing setup"),
                caseRow("TC_FAIL_SETUP_LEAF", "Leaf after bad setup", "TC_SETUP_BAD", "click body probe"),
                caseRow("TC_FAIL_EXPECT", "Missing assertion expected", "", "assert fail canary"),
                caseRow("TC_FAIL_TYPE", "Missing type target", "", "type missing secret"),
                caseRow("TC_FAIL_SELECT", "Missing select target", "", "select missing size")
        );
    }

    private static List<String> happyIds() {
        return List.of(
                "TC_SETUP", "TC_LEAF_ELIDE", "TC_LEAF_FULL", "TC_EDIT", "TC_SIMILAR",
                "TC_PAGES", "TC_A_B", "TC_A__B", "TC_UNICODE", "TC_ABSENT_OK",
                "TC_CANARY", "TC_READY", "TC_SELECT");
    }

    private static List<String> failIds() {
        return List.of(
                "TC_FAIL_CLICK", "TC_FAIL_ABSENCE", "TC_SETUP_BAD", "TC_FAIL_SETUP_LEAF",
                "TC_FAIL_EXPECT", "TC_FAIL_TYPE", "TC_FAIL_SELECT");
    }

    private TcDraft passed(
            String id, ManualTestCase row, List<ProvenStep> proven, List<ProvenStep> login,
            boolean needsLogin, Path work) throws Exception {
        Path ev = work.resolve("evidence").resolve(OccurrenceIdentity.folder(id, 1));
        Files.createDirectories(ev);
        Files.writeString(ev.resolve("step-001.png"), id, StandardCharsets.UTF_8);
        return new TcDraft(
                id, row.title(), row.steps(), row.expectedResult(), TcDraftStatus.PASSED,
                proven, login, needsLogin, -1, "", "", ev.toString(), 0, baseUrl + "/")
                .withLoginFormUrl(baseUrl + "/");
    }

    private static ManualTestCase caseRow(String id, String title, String callBefore, String steps) {
        return new ManualTestCase(id, title, "", steps, "ok", "P1", "", "", "", "AUTOMATE", callBefore);
    }

    private static ProvenStep type(String tcId, String page, String id, String value) {
        return new ProvenStep(tcId, page, "elementAction", "type",
                "id", id, value, "", "", true, "intent:TYPE");
    }

    private static ProvenStep click(String tcId, String page, String id) {
        return new ProvenStep(tcId, page, "elementAction", "click",
                "id", id, "", "", "", true, "intent:CLICK");
    }

    private static ProvenStep select(String tcId, String page, String id, String option) {
        return new ProvenStep(tcId, page, "elementAction", "select",
                "id", id, option, "", "", true, "intent:SELECT");
    }

    private static ProvenStep notVisible(String tcId, String page, String css) {
        return new ProvenStep(tcId, page, "elementAction", "assert",
                "css", css, "", "notVisible", "", true, "intent:ASSERT");
    }

    private static ProvenStep textContains(String tcId, String page, String id, String expected) {
        return new ProvenStep(tcId, page, "elementAction", "assert",
                "id", id, "", "textContains", expected, true, "intent:ASSERT");
    }

    private static ProvenStep bodyText(String tcId, String page, String expected) {
        return new ProvenStep(tcId, page, "elementAction", "assert",
                "xpath", "//body", "", "textContains", expected, true, "intent:ASSERT");
    }

    private static void assertPrerequisiteKeys(Path project) throws Exception {
        Path props = project.resolve("src/test/resources/test-data/delivery-testdata.properties");
        String data = Files.readString(props, StandardCharsets.UTF_8);
        Assert.assertTrue(data.contains("login@example.invalid"), data);
        Assert.assertTrue(data.contains("changed@example.invalid"), data);
        Assert.assertTrue(data.contains("TC_SETUP.login."), data);
        Assert.assertTrue(data.contains("TC_SETUP.body."), data);
        String setup = Files.readString(project.resolve("src/test/java/project/tests/generated/TC_SETUP.java"));
        String elide = Files.readString(project.resolve("src/test/java/project/tests/generated/TC_LEAF_ELIDE.java"));
        String full = Files.readString(project.resolve("src/test/java/project/tests/generated/TC_LEAF_FULL.java"));
        Assert.assertTrue(setup.contains("TC_SETUP.login."), setup);
        Assert.assertTrue(setup.contains("TC_SETUP.body."), setup);
        Assert.assertTrue(elide.contains("TC_SETUP.body."), elide);
        Assert.assertFalse(elide.contains("TC_SETUP.login."), elide);
        Assert.assertTrue(full.contains("TC_SETUP.login."), full);
        Assert.assertTrue(full.contains("TC_SETUP.body."), full);
    }

    private static void assertCoverageSource(Path project) throws Exception {
        Path pages = project.resolve("src/main/java/project/pages");
        try (var list = Files.list(pages)) {
            long actions = list.filter(p -> p.getFileName().toString().endsWith("_Actions.java")).count();
            Assert.assertTrue(actions >= 2, "colliding page names must emit more than one Actions class");
        }
        Assert.assertTrue(Files.isRegularFile(
                project.resolve("src/test/java/project/tests/generated/TC_A_B.java")));
        Assert.assertTrue(Files.isRegularFile(
                project.resolve("src/test/java/project/tests/generated/TC_A__B.java")));
        String data = Files.readString(
                project.resolve("src/test/resources/test-data/delivery-testdata.properties"),
                StandardCharsets.UTF_8);
        Assert.assertTrue(data.contains("مرحبا café"), data);
        Assert.assertTrue(data.contains(CANARY), data);
        String canaryTest = Files.readString(
                project.resolve("src/test/java/project/tests/generated/TC_CANARY.java"));
        Assert.assertFalse(canaryTest.contains(CANARY), canaryTest);
        String ready = Files.readString(
                project.resolve("src/test/java/project/tests/generated/TC_READY.java"));
        Assert.assertTrue(ready.contains(CANARY_ASSERT),
                "assertion expected is inlined in generated tests:\n" + ready);
        String unicode = Files.readString(
                project.resolve("src/test/java/project/tests/generated/TC_UNICODE.java"));
        Assert.assertFalse(unicode.contains("مرحبا café"), unicode);
    }

    private static void assertCanaryAbsent(Path replay, String label) throws Exception {
        List<String> leaks = new ArrayList<>();
        try (var stream = Files.walk(replay)) {
            for (Path file : stream.filter(Files::isRegularFile).toList()) {
                String rel = replay.relativize(file).toString().replace('\\', '/');
                if (rel.endsWith("delivery-testdata.properties")) {
                    continue;
                }
                byte[] bytes = Files.readAllBytes(file);
                String text = new String(bytes, StandardCharsets.UTF_8);
                if (text.contains(CANARY) || indexOf(bytes, CANARY.getBytes(StandardCharsets.UTF_8)) >= 0) {
                    leaks.add(rel);
                }
            }
        }
        Assert.assertTrue(leaks.isEmpty(),
                label + " typed password canary leaked into logs/Allure/source: " + leaks);
    }

    private static void assertDownloadedCounts(Path replay, MavenRun run, int expectedCases, String label)
            throws Exception {
        assertNoInternalSelfTests(replay);
        int generated = countGeneratedTests(replay);
        Assert.assertEquals(generated, expectedCases,
                label + " generated test files must match cases, found " + generated);
        Assert.assertEquals(run.statuses.size(), expectedCases,
                label + " Surefire case count must match generated cases: " + run.statuses);
        int testsRun = parseTestsRun(run.log);
        Assert.assertEquals(testsRun, expectedCases,
                label + " Maven Tests run must match generated cases, log last Tests run=" + testsRun
                        + "\n" + tail(run.log));
    }

    private static void assertNoInternalSelfTests(Path project) throws Exception {
        Assert.assertFalse(Files.exists(
                project.resolve("src/test/java/project/validations/ValidationAssertAllTest.java")));
        Assert.assertFalse(Files.exists(project.resolve(
                "src/test/java/project/validations/support/FalseCustomerAssertionSample.java")));
    }

    private static int countGeneratedTests(Path project) throws Exception {
        Path generated = project.resolve("src/test/java/project/tests/generated");
        if (!Files.isDirectory(generated)) {
            return 0;
        }
        try (var list = Files.list(generated)) {
            return (int) list.filter(p -> p.getFileName().toString().endsWith(".java")).count();
        }
    }

    private static int parseTestsRun(String log) {
        int last = -1;
        if (log == null) {
            return last;
        }
        for (String line : log.split("\\R")) {
            int idx = line.indexOf("Tests run:");
            if (idx < 0) {
                continue;
            }
            String rest = line.substring(idx + "Tests run:".length()).trim();
            int comma = rest.indexOf(',');
            String num = comma < 0 ? rest : rest.substring(0, comma);
            try {
                last = Integer.parseInt(num.trim());
            } catch (NumberFormatException ignored) {
                // keep scanning
            }
        }
        return last;
    }

    /**
     * Records where synthetic tokens remain. Typed passwords must stay out of logs/Allure;
     * assertion expected values, failure XML, screenshots, testdata, and IR are intentional remainder.
     */
    private static void recordCanaryRemainder(Path replay, String log, Path out) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("Canary remainder (not complete redaction)\n");
        appendHits(sb, "CANARY_PW", CANARY, replay, log);
        appendHits(sb, "CANARY_ASSERT", CANARY_ASSERT, replay, log);
        appendHits(sb, "CANARY_FAIL", CANARY_FAIL, replay, log);
        Files.createDirectories(out.getParent());
        Files.writeString(out, sb.toString(), StandardCharsets.UTF_8);
    }

    private static void appendHits(StringBuilder sb, String label, String token, Path replay, String log)
            throws Exception {
        sb.append(label).append('=').append(token).append('\n');
        List<String> files = findToken(replay, token);
        if (log != null && log.contains(token)) {
            files.add("(maven log)");
        }
        if (files.isEmpty()) {
            sb.append("  (not found in replay tree or maven log)\n");
        } else {
            for (String file : files) {
                sb.append("  ").append(file).append('\n');
            }
        }
    }

    private static boolean remainderContains(Path replay, String token) throws Exception {
        return !findToken(replay, token).isEmpty();
    }

    private static List<String> findToken(Path replay, String token) throws Exception {
        List<String> hits = new ArrayList<>();
        byte[] needle = token.getBytes(StandardCharsets.UTF_8);
        try (var stream = Files.walk(replay)) {
            for (Path file : stream.filter(Files::isRegularFile).toList()) {
                String rel = replay.relativize(file).toString().replace('\\', '/');
                byte[] bytes = Files.readAllBytes(file);
                String text = new String(bytes, StandardCharsets.UTF_8);
                if (text.contains(token) || indexOf(bytes, needle) >= 0) {
                    hits.add(rel);
                }
            }
        }
        return hits;
    }

    private static void prepareReplay(Path project) throws Exception {
        upsert(project.resolve("src/main/resources/delivery-target.properties"), Map.of(
                "BROWSER_TYPE", "CHROME",
                "EXECUTION_TYPE", "LocalHeadless",
                "BROWSER_HEADLESS", "true",
                "OpenAllureAfterExecution", "true",
                "DEFAULT_WAIT", "4",
                "TEXT_CONTAINS_WAIT_SECONDS", "4",
                "NOT_VISIBLE_WAIT_SECONDS", "4"));
        upsert(project.resolve("src/main/resources/enviroment.properties"), Map.of(
                "EXECUTION_TYPE", "LocalHeadless",
                "OpenAllureAfterExecution", "true"));
        upsert(project.resolve("src/main/resources/waits.properties"), Map.of(
                "DEFAULT_WAIT", "4",
                "TEXT_CONTAINS_WAIT_SECONDS", "4",
                "NOT_VISIBLE_WAIT_SECONDS", "4"));
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

    private MavenRun mvnCleanTest(Path projectDir) throws Exception {
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        List<String> cmd = List.of(windows ? "mvn.cmd" : "mvn", "-B", "clean", "test");
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(projectDir.toFile());
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        StringBuilder out = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                out.append(line).append('\n');
            }
        }
        boolean finished = proc.waitFor(20, TimeUnit.MINUTES);
        if (!finished) {
            proc.destroyForcibly();
            throw new IllegalStateException("mvn clean test timed out after 20m");
        }
        Map<String, String> statuses = new LinkedHashMap<>();
        Map<String, String> configFailures = new LinkedHashMap<>();
        Path reports = projectDir.resolve("test-output/target/surefire-reports");
        if (!Files.isDirectory(reports)) {
            reports = projectDir.resolve("target/surefire-reports");
        }
        parseTestNgResults(reports.resolve("testng-results.xml"), statuses, configFailures);
        if (statuses.isEmpty()) {
            parseSurefire(reports, statuses, configFailures);
        }
        return new MavenRun(proc.exitValue(), statuses, configFailures, out.toString());
    }

    private static Map<String, String> parseSurefire(Path reports) throws Exception {
        Map<String, String> out = new LinkedHashMap<>();
        parseSurefire(reports, out, new LinkedHashMap<>());
        return out;
    }

    private static void parseSurefire(
            Path reports, Map<String, String> statuses, Map<String, String> configFailures) throws Exception {
        if (!Files.isDirectory(reports)) {
            return;
        }
        parseSurefireDir(reports, statuses, configFailures);
        Path junit = reports.resolve("junitreports");
        if (Files.isDirectory(junit)) {
            parseSurefireDir(junit, statuses, configFailures);
        }
    }

    private static void parseTestNgResults(
            Path file, Map<String, String> statuses, Map<String, String> configFailures) throws Exception {
        if (!Files.isRegularFile(file)) {
            return;
        }
        String xml = Files.readString(file, StandardCharsets.UTF_8);
        String currentClass = "";
        Matcher classOrMethod = Pattern.compile(
                "<class\\s+name=\"([^\"]+)\"|<test-method\\b([^>]*)>",
                Pattern.CASE_INSENSITIVE).matcher(xml);
        while (classOrMethod.find()) {
            if (classOrMethod.group(1) != null) {
                currentClass = classOrMethod.group(1);
                continue;
            }
            String attrs = classOrMethod.group(2);
            String name = attr(attrs, "name");
            String status = attr(attrs, "status");
            boolean config = "true".equalsIgnoreCase(attr(attrs, "is-config"));
            String tcId = simpleName(currentClass);
            if (tcId.isBlank() || status.isBlank()) {
                continue;
            }
            status = normalizeNgStatus(status);
            if (config) {
                if ("FAIL".equals(status) || "SKIP".equals(status)) {
                    configFailures.putIfAbsent(tcId, name + "=" + status);
                }
            } else {
                statuses.put(tcId, status);
            }
        }
    }

    private static String attr(String attrs, String key) {
        Matcher m = Pattern.compile(key + "\\s*=\\s*\"([^\"]*)\"", Pattern.CASE_INSENSITIVE).matcher(attrs);
        return m.find() ? m.group(1) : "";
    }

    private static String simpleName(String className) {
        int dot = className.lastIndexOf('.');
        return dot < 0 ? className : className.substring(dot + 1);
    }

    private static String normalizeNgStatus(String status) {
        String s = status.trim().toUpperCase();
        if (s.startsWith("PASS")) {
            return "PASS";
        }
        if (s.startsWith("FAIL")) {
            return "FAIL";
        }
        if (s.startsWith("SKIP")) {
            return "SKIP";
        }
        return s;
    }

    private static void parseSurefireDir(
            Path dir, Map<String, String> statuses, Map<String, String> configFailures) throws Exception {
        try (var stream = Files.list(dir)) {
            for (Path file : stream.toList()) {
                String name = file.getFileName().toString();
                if (!name.startsWith("TEST-") || !name.endsWith(".xml")) {
                    continue;
                }
                if (name.equals("TEST-TestSuite.xml") || name.contains("ValidationAssertAll")) {
                    continue;
                }
                String xml = Files.readString(file, StandardCharsets.UTF_8);
                String className = name.substring("TEST-".length(), name.length() - 4);
                String tcId = simpleName(className);
                Matcher cases = Pattern.compile(
                        "<testcase\\b([^>]*)>(.*?)</testcase>",
                        Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(xml);
                boolean sawTestCase = false;
                while (cases.find()) {
                    String attrs = cases.group(1);
                    String body = cases.group(2);
                    String caseName = attr(attrs, "name");
                    boolean setup = "setUp".equals(caseName) || "tearDown".equals(caseName);
                    String status;
                    if (body.contains("<skipped")) {
                        status = "SKIP";
                    } else if (body.contains("<failure") || body.contains("<error")) {
                        status = "FAIL";
                    } else {
                        status = "PASS";
                    }
                    if (setup) {
                        if (!"PASS".equals(status)) {
                            configFailures.putIfAbsent(tcId, caseName + "=" + status);
                        }
                    } else {
                        sawTestCase = true;
                        statuses.put(tcId, status);
                    }
                }
                if (!sawTestCase && !statuses.containsKey(tcId) && xml.contains("<testcase")) {
                    if (xml.contains("<skipped")) {
                        statuses.put(tcId, "SKIP");
                    } else if (xml.contains("<failure") || xml.contains("<error")) {
                        statuses.put(tcId, "FAIL");
                    } else {
                        statuses.putIfAbsent(tcId, "PASS");
                    }
                }
            }
        }
    }

    private static String statusOf(Map<String, String> statuses, String tcId) {
        if (statuses.containsKey(tcId)) {
            return statuses.get(tcId);
        }
        for (Map.Entry<String, String> e : statuses.entrySet()) {
            if (e.getKey().contains(tcId) || tcId.contains(e.getKey())) {
                return e.getValue();
            }
        }
        return "MISSING";
    }

    private static Path unzip(Path zip, Path dest) throws Exception {
        Files.createDirectories(dest);
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                Path out = dest.resolve(entry.getName()).normalize();
                if (!out.startsWith(dest)) {
                    throw new IllegalStateException("zip slip: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(out);
                } else {
                    Files.createDirectories(out.getParent());
                    Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
        return dest;
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
        return java.util.HexFormat.of().formatHex(digest.digest());
    }

    private static int countQuitMarkers(Path projectDir) throws Exception {
        try (var stream = Files.walk(projectDir)) {
            return (int) stream
                    .filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .filter(name -> name.startsWith("driver-quit-") && name.endsWith(".marker"))
                    .count();
        }
    }

    private static int indexOf(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i + needle.length <= haystack.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    private static String tail(String log) {
        if (log == null) {
            return "";
        }
        int keep = Math.min(log.length(), 8000);
        return log.substring(log.length() - keep);
    }

    private record MavenRun(
            int exitCode,
            Map<String, String> statuses,
            Map<String, String> configFailures,
            String log) {
    }

    private record AllureExpect(String kind, String status) {
        static AllureExpect passedType() {
            return new AllureExpect("type", "passed");
        }

        static AllureExpect passedSelect() {
            return new AllureExpect("select", "passed");
        }

        static AllureExpect passedAssert() {
            return new AllureExpect("assert", "passed");
        }

        static AllureExpect failedType() {
            return new AllureExpect("type", "failed");
        }

        static AllureExpect failedSelect() {
            return new AllureExpect("select", "failed");
        }

        static AllureExpect failedAssert() {
            return new AllureExpect("assert", "failed");
        }
    }

    private static void assertAllureReporting(Path replay, Path evidenceOut, AllureExpect... expects)
            throws Exception {
        Path results = replay.resolve("test-output/target/allure-results");
        if (!Files.isDirectory(results)) {
            results = replay.resolve("target/allure-results");
        }
        Assert.assertTrue(Files.isDirectory(results), "Allure results missing under " + replay);
        JSONArray found = new JSONArray();
        try (var stream = Files.walk(results)) {
            for (Path file : stream.filter(p -> p.getFileName().toString().endsWith("-result.json")).toList()) {
                String text = Files.readString(file, StandardCharsets.UTF_8);
                Assert.assertFalse(text.contains(CANARY),
                        "typed password canary must be absent from Allure JSON: " + file);
                JSONObject json = new JSONObject(text);
                collectSteps(json.optJSONArray("steps"), json.optString("name"), found);
            }
        }
        Files.writeString(evidenceOut, found.toString(2), StandardCharsets.UTF_8);
        for (AllureExpect expect : expects) {
            JSONObject match = findNamedStep(found, expect.kind(), expect.status());
            Assert.assertNotNull(match,
                    "missing Allure " + expect.status() + " " + expect.kind() + " step in " + found);
            Assert.assertTrue(match.getLong("stop") >= match.getLong("start"), match.toString());
            Assert.assertTrue(match.optJSONArray("parameters") == null
                            || match.getJSONArray("parameters").length() == 0,
                    "step must not expose value parameters: " + match);
            Assert.assertFalse(match.optString("parent").isBlank(),
                    "step must be nested under a test method: " + match);
        }
    }

    private static void collectSteps(JSONArray steps, String parent, JSONArray out) {
        if (steps == null) {
            return;
        }
        for (int i = 0; i < steps.length(); i++) {
            JSONObject step = new JSONObject(steps.getJSONObject(i).toString());
            step.put("parent", parent == null ? "" : parent);
            out.put(step);
            collectSteps(step.optJSONArray("steps"), step.optString("name"), out);
        }
    }

    private static JSONObject findNamedStep(JSONArray found, String kind, String status) {
        for (int i = 0; i < found.length(); i++) {
            JSONObject step = found.getJSONObject(i);
            String name = step.optString("name");
            if (!status.equals(step.optString("status"))) {
                continue;
            }
            String lower = name.toLowerCase();
            boolean match = switch (kind) {
                case "type" -> lower.startsWith("type_");
                case "select" -> lower.startsWith("select_");
                case "assert" -> lower.startsWith("assert_");
                default -> false;
            };
            if (match) {
                return step;
            }
        }
        return null;
    }
}
