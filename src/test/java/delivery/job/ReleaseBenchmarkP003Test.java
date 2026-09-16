package delivery.job;

import delivery.authoring.AuthoringEngine;
import delivery.codegen.CodeWriter;
import delivery.codegen.ProvenStep;
import delivery.excel.ManualTcExcelWriter;
import delivery.excel.ManualTestCase;
import delivery.ir.TcDraft;
import delivery.ir.TcDraftStatus;
import delivery.ir.TcDraftStore;
import delivery.packager.FrameworkPackager;
import delivery.store.ProjectStore;
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
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * P0-03 release benchmark: downloaded replay of the committed emit pipeline against
 * {@code docs/reviews/2026-09-15/release-benchmark/pages}.
 *
 * <p>Live authoring (LLM prove) is recorded separately; this gate uses fixture IR that
 * matches the static locators so Maven execution, Update reuse, setup failure, and
 * compile-fail retention can run without a model.
 */
public class ReleaseBenchmarkP003Test {

    private static final Path TEMPLATE = Path.of("customer-framework-template");
    private static final Path PAGES = Path.of("docs/reviews/2026-09-15/release-benchmark/pages");
    private static final String PROJECT_ID = "p003_bench";

    private com.sun.net.httpserver.HttpServer server;
    private String baseUrl;
    private Path workRoot;
    private Path storeRoot;
    private Path excel;
    private ConversionJobResult versionA;
    private ConversionJobResult versionB;
    private String versionASha256;
    private String versionBSha256;
    private Map<String, String> replay1;
    private Map<String, String> replay2;
    private String gitRevision;
    private String templateRevision;

    @BeforeClass
    public void startFixtureServer() throws Exception {
        Assert.assertTrue(Files.isDirectory(PAGES), "P0-03 pages missing: " + PAGES.toAbsolutePath());
        server = com.sun.net.httpserver.HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        Path pages = PAGES.toAbsolutePath().normalize();
        server.createContext("/", exchange -> {
            String rel = exchange.getRequestURI().getPath();
            if (rel == null || rel.equals("/")) {
                rel = "/login.html";
            }
            Path file = pages.resolve(rel.substring(1)).normalize();
            if (!file.startsWith(pages) || !Files.isRegularFile(file)) {
                exchange.sendResponseHeaders(HttpURLConnection.HTTP_NOT_FOUND, -1);
                exchange.close();
                return;
            }
            byte[] bytes = Files.readAllBytes(file);
            String type = rel.endsWith(".html") ? "text/html; charset=utf-8" : "text/plain; charset=utf-8";
            exchange.getResponseHeaders().set("Content-Type", type);
            exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        workRoot = Files.createTempDirectory("p003-work");
        storeRoot = Files.createTempDirectory("p003-store");
        excel = workRoot.resolve("cases.xlsx");
        gitRevision = git("rev-parse", "HEAD");
        templateRevision = git("log", "-1", "--format=%H", "--", "customer-framework-template");
    }

    @AfterClass(alwaysRun = true)
    public void stopFixtureServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    public void downloadedReplayFailsOnFalseAssertionAndPassesTheRest() throws Exception {
        List<ManualTestCase> cases = cases();
        List<TcDraft> drafts = draftsForNew(workRoot.resolve("new"));
        versionA = emit("NEW", workRoot.resolve("new"), cases, drafts);
        Assert.assertTrue(Files.isRegularFile(versionA.zipFile()), "version A zip missing");
        versionASha256 = sha256(versionA.zipFile());
        Assert.assertEquals(versionASha256.length(), 64, versionASha256);
        Assert.assertEquals(versionA.passed(), 10, versionA.message());
        String checkoutSrc = Files.readString(
                workRoot.resolve("new/project/src/test/java/project/tests/generated/TC_CHECKOUT.java"));
        Assert.assertTrue(checkoutSrc.contains("click_Add_Widget"),
                "checkout @BeforeMethod must inline cart add:\n" + checkoutSrc);

        Path replayDir = unzip(versionA.zipFile(), workRoot.resolve("replay-1"));
        assertCustomerZipHasNoCredentialMaterial(replayDir, versionASha256);
        Path generated = replayDir.resolve("src/test/java/project/tests/generated");
        Assert.assertTrue(Files.isRegularFile(generated.resolve("TC_ASSERT_FALSE.java")),
                "downloaded ZIP must contain TC_ASSERT_FALSE.java under " + generated);
        MavenRun run = mvnGeneratedTests(replayDir);
        replay1 = run.statuses;
        Assert.assertNotEquals(run.exitCode, 0, "F00: Maven must be nonzero when TC_ASSERT_FALSE fails:\n" + run.log);
        Assert.assertEquals(statusOf(replay1, "TC_ASSERT_FALSE"), "FAIL",
                "statuses=" + replay1 + "\n" + run.log);
        for (String id : List.of(
                "TC_LOGIN_OK", "TC_LOGIN_BAD", "TC_ASSERT_TRUE", "TC_URL_OK", "TC_SELECTED",
                "TC_CART", "TC_CHECKOUT", "TC_CHECKOUT_EMPTY", "TC_SECRET")) {
            Assert.assertEquals(statusOf(replay1, id), "PASS",
                    id + " expected PASS, got " + replay1 + "\n" + run.log);
        }
        String checkout = Files.readString(generated.resolve("TC_CHECKOUT.java"));
        Assert.assertTrue(checkout.contains("click_Add_Widget"), checkout);
        Assert.assertTrue(checkout.contains("Validation.assertAll()"), checkout);
        Assert.assertTrue(checkout.contains("@AfterMethod(alwaysRun = true)"), checkout);
    }

    @Test(dependsOnMethods = "downloadedReplayFailsOnFalseAssertionAndPassesTheRest")
    public void updateUnchangedPreservesMethodsDataSetupEvidenceAndReplay() throws Exception {
        List<ManualTestCase> cases = cases();
        Map<String, String> hashes = hashesOf(cases);
        Map<String, TcDraft> stored = loadStoredDrafts();
        ReuseEligibility.Context storedCtx = ReuseEligibility.read(
                new ProjectStore(storeRoot, baseUrl).projectRoot(PROJECT_ID));
        Set<String> author = ReuseEligibility.authorIds(
                cases, hashes, stored, storedCtx, ReuseEligibility.current(request("UPDATE")));
        Assert.assertTrue(author.isEmpty(), "unchanged PASS must be reused, not re-authored: " + author);

        List<TcDraft> reused = new ArrayList<>();
        Path projectRoot = new ProjectStore(storeRoot, baseUrl).projectRoot(PROJECT_ID);
        for (ManualTestCase tc : cases) {
            reused.add(ReuseEligibility.copyForReuse(stored.get(tc.tcId()), tc, projectRoot));
        }
        ManualTestCase todo = new ManualTestCase(
                "TC_UNPROVEN", "Unproven", "", "1. Missing control", "never",
                "P1", "", "", "", "AUTOMATE", "");
        Map<String, String> withTodo = new LinkedHashMap<>(hashes);
        withTodo.put("TC_UNPROVEN", todo.contentHash());
        Map<String, TcDraft> storedWithTodo = new LinkedHashMap<>(stored);
        storedWithTodo.put("TC_UNPROVEN", new TcDraft(
                "TC_UNPROVEN", "Unproven", "1. Missing control", "never", TcDraftStatus.TODO,
                List.of(), List.of(), false, 1, "CLICK", "blocked", "", 0, baseUrl + "/home.html"));
        Set<String> todoAuthor = ReuseEligibility.authorIds(
                append(cases, todo), withTodo, storedWithTodo, storedCtx,
                ReuseEligibility.current(request("UPDATE")));
        Assert.assertTrue(todoAuthor.contains("TC_UNPROVEN"), "unchanged TODO must be retried");
        Assert.assertFalse(todoAuthor.contains("TC_LOGIN_OK"), "eligible PASS must stay reusable");

        versionB = emit("UPDATE", workRoot.resolve("update"), cases, reused);
        versionBSha256 = sha256(versionB.zipFile());
        Assert.assertEquals(versionBSha256.length(), 64, versionBSha256);
        Path framework = projectRoot.resolve("framework");
        String shop = Files.readString(framework.resolve("src/main/java/project/pages/Shop_Actions.java"));
        Assert.assertTrue(shop.contains("click_Add_Widget"), shop);
        String data = Files.readString(
                framework.resolve("src/test/resources/test-data/delivery-testdata.properties"));
        Assert.assertTrue(data.contains("demo-pass") || data.contains("type_Password"), data);
        String checkout = Files.readString(
                framework.resolve("src/test/java/project/tests/generated/TC_CHECKOUT.java"));
        Assert.assertTrue(checkout.contains("click_Add_Widget"), checkout);
        Path evidence = ReuseEligibility.durableEvidenceDir(projectRoot, "TC_CART");
        Assert.assertTrue(Files.exists(evidence) || Files.exists(evidence.getParent()),
                "evidence must survive UPDATE: " + evidence);

        Path replayDir = unzip(versionB.zipFile(), workRoot.resolve("replay-2"));
        MavenRun run = mvnGeneratedTests(replayDir);
        replay2 = run.statuses;
        Assert.assertEquals(statusOf(replay2, "TC_ASSERT_FALSE"), "FAIL", replay2.toString());
        Assert.assertEquals(statusOf(replay2, "TC_CHECKOUT"), "PASS", replay2.toString());
        Assert.assertEquals(replay2, replay1, "replay #2 must match replay #1");
    }

    @Test(dependsOnMethods = "updateUnchangedPreservesMethodsDataSetupEvidenceAndReplay")
    public void changedPrerequisitePassToTodoRemovalAndRepeatedReuse() throws Exception {
        List<ManualTestCase> cases = cases();
        ManualTestCase changedCart = new ManualTestCase(
                "TC_CART", "Add widget to cart", "",
                "Open shop.html; click Add widget to cart; open cart.html",
                "Cart: 2 items", "P1", "", "", "", "AUTOMATE", "TC_LOGIN_OK");
        List<ManualTestCase> changed = replace(cases, changedCart);
        Map<String, TcDraft> stored = loadStoredDrafts();
        Map<String, String> originalHashes = hashesOf(cases);
        ReuseEligibility.Context storedCtx = ReuseEligibility.read(
                new ProjectStore(storeRoot, baseUrl).projectRoot(PROJECT_ID));
        Set<String> author = ReuseEligibility.authorIds(
                changed, originalHashes, stored, storedCtx, ReuseEligibility.current(request("UPDATE")));
        Assert.assertTrue(author.contains("TC_CART"), author.toString());
        Assert.assertTrue(author.contains("TC_CHECKOUT"),
                "changed prerequisite must invalidate checkout: " + author);

        Set<String> repeated = ReuseEligibility.authorIds(
                cases, hashesOf(cases), stored, storedCtx, ReuseEligibility.current(request("UPDATE")));
        Assert.assertTrue(repeated.isEmpty(), "repeated UPDATE of identical inputs must reuse: " + repeated);

        ConversionJobRequest otherHost = new ConversionJobRequest(
                PROJECT_ID, excel, "http://127.0.0.1:9", "demo", "demo-pass",
                workRoot, storeRoot, TEMPLATE, "UPDATE",
                "http://127.0.0.1:11434", "qwen2.5", false, false, AuthoringEngine.KEEL);
        Set<String> env = ReuseEligibility.authorIds(
                cases, hashesOf(cases), stored, storedCtx, ReuseEligibility.current(otherHost));
        Assert.assertTrue(env.contains("TC_LOGIN_OK"), "changed BASE_WEB must invalidate reuse: " + env);

        Path framework = Files.createTempDirectory("p003-layer");
        Path templates = TEMPLATE.resolve("templates");
        CodeWriter writer = new CodeWriter(templates);
        List<delivery.job.TcOutcome> full = new ArrayList<>();
        for (TcDraft d : stored.values()) {
            full.add(EmitPhase.toOutcome(d).withSetup(CallBeforeSetup.stepsFor(d, List.copyOf(stored.values()), cases)));
        }
        writer.write(framework, full);
        List<delivery.job.TcOutcome> withoutLogin = full.stream()
                .filter(o -> !"TC_LOGIN_OK".equals(o.tcId()))
                .toList();
        writer.write(framework, withoutLogin);
        Assert.assertFalse(Files.exists(framework.resolve("src/test/java/project/tests/generated/TC_LOGIN_OK.java")));

        List<delivery.job.TcOutcome> demotedUrl = new ArrayList<>();
        for (delivery.job.TcOutcome o : full) {
            if ("TC_URL_OK".equals(o.tcId())) {
                demotedUrl.add(new delivery.job.TcOutcome(
                        o.tcId(), o.title(), TcStatus.TODO, List.of(), "wrong URL", null,
                        o.needsLoginBeforeMethod(), o.loginSteps(), o.setupSteps()));
            } else {
                demotedUrl.add(o);
            }
        }
        writer.write(framework, demotedUrl);
        Assert.assertFalse(Files.exists(framework.resolve("src/test/java/project/tests/generated/TC_URL_OK.java")));
        Assert.assertTrue(Files.exists(framework.resolve("src/test/java/project/tests/todo/TC_URL_OKTodo.java")));
    }

    @Test(dependsOnMethods = "downloadedReplayFailsOnFalseAssertionAndPassesTheRest")
    public void failedSetupSkipsBodyAndStillQuits() throws Exception {
        Path work = workRoot.resolve("setup-fail");
        Files.createDirectories(work);
        List<ManualTestCase> cases = List.of(
                caseRow("TC_LOGIN_OK", "Valid login", "", "Open login.html", "Dashboard is visible"),
                caseRow("TC_CART", "Add widget to cart", "TC_LOGIN_OK", "Open shop.html", "Cart: 99 items"),
                caseRow("TC_CHECKOUT", "Checkout after cart", "TC_CART", "Open checkout.html", "Order confirmed"));
        List<ProvenStep> login = loginSteps("TC_LOGIN_OK", "demo", "demo-pass");
        TcDraft loginDraft = passed("TC_LOGIN_OK", cases.get(0),
                concat(login, List.of(bodyText("TC_LOGIN_OK", "Home", "Dashboard"))), List.of(), false,
                baseUrl + "/home.html", work);
        List<ProvenStep> cartBody = List.of(
                clickNav("TC_CART", "Home", "nav-shop"),
                click("TC_CART", "Shop", "add-widget"),
                textContains("TC_CART", "Cart", "cart-status", "Cart: 99 items"));
        TcDraft cartDraft = passed("TC_CART", cases.get(1), cartBody, List.of(), false,
                baseUrl + "/cart.html", work);
        List<ProvenStep> checkoutBody = List.of(
                clickNav("TC_CHECKOUT", "Cart", "nav-checkout"),
                click("TC_CHECKOUT", "Checkout", "place-order"),
                bodyText("TC_CHECKOUT", "Checkout", "Order confirmed"));
        TcDraft checkoutDraft = passed("TC_CHECKOUT", cases.get(2), checkoutBody, List.of(), false,
                baseUrl + "/checkout.html", work);
        ConversionJobResult emitted = emit("NEW", work, cases, List.of(loginDraft, cartDraft, checkoutDraft),
                "p003_setup_fail");
        Path replay = unzip(emitted.zipFile(), work.resolve("replay"));
        String source = Files.readString(
                replay.resolve("src/test/java/project/tests/generated/TC_CHECKOUT.java"));
        Assert.assertTrue(source.contains("@AfterMethod(alwaysRun = true)"), source);
        int assertAll = source.indexOf("Validation.assertAll();");
        int testMethod = source.indexOf("public void Checkout_after_cart()");
        Assert.assertTrue(assertAll >= 0 && assertAll < testMethod, source);

        MavenRun run = mvnOneClass(replay, "project.tests.generated.TC_CHECKOUT");
        Assert.assertNotEquals(run.exitCode, 0, "failed cart setup must fail Maven:\n" + run.log);
        Assert.assertNotEquals(statusOf(run.statuses, "TC_CHECKOUT"), "PASS",
                "checkout body must not pass when setup assertion fails: " + run.statuses + "\n" + run.log);
        Assert.assertTrue(countQuitMarkers(replay) > 0,
                "failed setup must quit the browser at runtime (quit marker missing under "
                        + replay + "):\n" + run.log);
    }

    @Test(dependsOnMethods = "downloadedReplayFailsOnFalseAssertionAndPassesTheRest")
    public void failedCompileLeavesPreviousPackageDownloadable() throws Exception {
        ProjectStore store = new ProjectStore(storeRoot, baseUrl);
        Path previous = store.latestVersionZip(PROJECT_ID).orElseThrow();
        Assert.assertTrue(Files.isRegularFile(previous));
        String previousSha = sha256(previous);
        Assert.assertEquals(previousSha.length(), 64, previousSha);
        Path broken = Files.createTempDirectory("p003-broken");
        copyTree(store.projectRoot(PROJECT_ID).resolve("framework"), broken);
        Path victim = broken.resolve("src/test/java/project/tests/generated/TC_LOGIN_OK.java");
        Files.writeString(victim, Files.readString(victim) + "\nTHIS IS NOT JAVA\n");
        try {
            EmitCompileCheck.runIfEnabled(broken);
            Assert.fail("broken package must fail test-compile");
        } catch (IllegalStateException expected) {
            Assert.assertTrue(expected.getMessage().contains("EMIT_COMPILE_CHECK"), expected.getMessage());
        }
        Path still = store.latestVersionZip(PROJECT_ID).orElseThrow();
        Assert.assertEquals(still.getFileName().toString(), previous.getFileName().toString());
        Assert.assertEquals(sha256(still), previousSha,
                "failed compile must not replace the previously downloadable ZIP bytes");
    }

    @Test(dependsOnMethods = {
            "updateUnchangedPreservesMethodsDataSetupEvidenceAndReplay",
            "changedPrerequisitePassToTodoRemovalAndRepeatedReuse",
            "failedSetupSkipsBodyAndStillQuits",
            "failedCompileLeavesPreviousPackageDownloadable"
    }, alwaysRun = true)
    public void artifactIdentitiesAreRecorded() {
        Assert.assertNotNull(gitRevision);
        Assert.assertFalse(gitRevision.isBlank());
        System.out.println("P0-03 gitRevision=" + gitRevision);
        System.out.println("P0-03 templateRevision=" + templateRevision);
        System.out.println("P0-03 baseUrl=" + baseUrl);
        if (versionA != null) {
            System.out.println("P0-03 versionA=" + versionA.zipFile());
            System.out.println("P0-03 versionASha256=" + versionASha256);
        }
        if (versionB != null) {
            System.out.println("P0-03 versionB=" + versionB.zipFile());
            System.out.println("P0-03 versionBSha256=" + versionBSha256);
        }
    }

    private ConversionJobResult emit(
            String mode, Path work, List<ManualTestCase> cases, List<TcDraft> drafts) throws Exception {
        return emit(mode, work, cases, drafts, PROJECT_ID);
    }

    private ConversionJobResult emit(
            String mode, Path work, List<ManualTestCase> cases, List<TcDraft> drafts, String projectId)
            throws Exception {
        Files.createDirectories(work);
        Path excelPath = work.resolve("cases.xlsx");
        ManualTcExcelWriter.write(excelPath, cases);
        TcDraftStore ir = new TcDraftStore(work);
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

    private ConversionJobRequest request(String mode) {
        return new ConversionJobRequest(
                PROJECT_ID, excel, baseUrl, "demo", "demo-pass",
                workRoot, storeRoot, TEMPLATE, mode,
                "http://127.0.0.1:11434", "qwen2.5", false, false, AuthoringEngine.KEEL);
    }

    private Map<String, TcDraft> loadStoredDrafts() {
        return ReuseEligibility.loadStoredDrafts(
                new ProjectStore(storeRoot, baseUrl).projectRoot(PROJECT_ID));
    }

    private List<TcDraft> draftsForNew(Path work) throws Exception {
        List<ProvenStep> loginOk = loginSteps("TC_LOGIN_OK", "demo", "demo-pass");
        List<ProvenStep> loginBad = loginSteps("TC_LOGIN_BAD", "demo", "wrong");
        List<ProvenStep> loginSecret = loginSteps("TC_SECRET", "demo", "CANARY-SECRET-001");
        List<ManualTestCase> rows = cases();
        return List.of(
                passed("TC_LOGIN_OK", rows.get(0),
                        concat(loginOk, List.of(bodyText("TC_LOGIN_OK", "Home", "Dashboard"))),
                        List.of(), false, baseUrl + "/home.html", work),
                passed("TC_LOGIN_BAD", rows.get(1),
                        concat(loginBad, List.of(bodyText("TC_LOGIN_BAD", "LoginPage", "Invalid credentials"))),
                        List.of(), false, baseUrl + "/login.html", work),
                passed("TC_ASSERT_FALSE", rows.get(2),
                        List.of(bodyText("TC_ASSERT_FALSE", "Home", "Admin Home")),
                        List.of(), false, baseUrl + "/home.html", work),
                passed("TC_ASSERT_TRUE", rows.get(3),
                        List.of(bodyText("TC_ASSERT_TRUE", "Home", "Dashboard")),
                        List.of(), false, baseUrl + "/home.html", work),
                passed("TC_URL_OK", rows.get(4),
                        List.of(urlContains("TC_URL_OK", "Home", "home.html")),
                        List.of(), false, baseUrl + "/home.html", work),
                passed("TC_SELECTED", rows.get(5),
                        List.of(clickNav("TC_SELECTED", "Home", "nav-shop"),
                                selected("TC_SELECTED", "Shop", "size", "M")),
                        List.of(), false, baseUrl + "/shop.html", work),
                passed("TC_CART", rows.get(6),
                        List.of(clickNav("TC_CART", "Home", "nav-shop"),
                                click("TC_CART", "Shop", "add-widget"),
                                textContains("TC_CART", "Cart", "cart-status", "Cart: 1 item")),
                        List.of(), false, baseUrl + "/cart.html", work),
                passed("TC_CHECKOUT", rows.get(7),
                        List.of(clickNav("TC_CHECKOUT", "Cart", "nav-checkout"),
                                click("TC_CHECKOUT", "Checkout", "place-order"),
                                bodyText("TC_CHECKOUT", "Checkout", "Order confirmed")),
                        List.of(), false, baseUrl + "/checkout.html", work),
                passed("TC_CHECKOUT_EMPTY", rows.get(8),
                        List.of(clickNav("TC_CHECKOUT_EMPTY", "Home", "nav-checkout"),
                                bodyText("TC_CHECKOUT_EMPTY", "Checkout", "Cannot checkout")),
                        List.of(), false, baseUrl + "/checkout.html", work),
                passed("TC_SECRET", rows.get(9),
                        concat(loginSecret, List.of(bodyText("TC_SECRET", "LoginPage", "Invalid credentials"))),
                        List.of(), false, baseUrl + "/login.html", work)
        );
    }

    private TcDraft passed(
            String id, ManualTestCase row, List<ProvenStep> proven, List<ProvenStep> login,
            boolean needsLogin, String lastUrl, Path work) throws Exception {
        Path ev = work.resolve("evidence").resolve(OccurrenceIdentity.folder(id, 1));
        Files.createDirectories(ev);
        Files.writeString(ev.resolve("step-001.png"), id, StandardCharsets.UTF_8);
        return new TcDraft(
                id, row.title(), row.steps(), row.expectedResult(), TcDraftStatus.PASSED,
                proven, login, needsLogin, -1, "", "", ev.toString(), 0, lastUrl)
                .withLoginFormUrl(baseUrl + "/login.html");
    }

    private static List<ManualTestCase> cases() {
        return List.of(
                caseRow("TC_LOGIN_OK", "Valid login", "", "Open login.html; type user demo; type password demo-pass; click Sign in", "Dashboard is visible"),
                caseRow("TC_LOGIN_BAD", "Invalid login", "", "Open login.html; type user demo; type password wrong; click Sign in", "Invalid credentials"),
                caseRow("TC_ASSERT_FALSE", "Deliberately false heading", "TC_LOGIN_OK", "Open home.html", "heading equals Admin Home"),
                caseRow("TC_ASSERT_TRUE", "True heading", "TC_LOGIN_OK", "Open home.html", "heading contains Dashboard"),
                caseRow("TC_URL_OK", "Home URL", "TC_LOGIN_OK", "Open home.html", "URL contains home.html"),
                caseRow("TC_SELECTED", "Size remains M", "TC_LOGIN_OK", "Open shop.html", "size select is M"),
                caseRow("TC_CART", "Add widget to cart", "TC_LOGIN_OK", "Open shop.html; click Add widget to cart; open cart.html", "Cart: 1 item"),
                caseRow("TC_CHECKOUT", "Checkout after cart", "TC_CART", "Open checkout.html; click Place order", "Order confirmed"),
                caseRow("TC_CHECKOUT_EMPTY", "Checkout empty cart", "TC_LOGIN_OK", "Open checkout.html", "Cannot checkout"),
                caseRow("TC_SECRET", "Canary password", "", "Open login.html; type user demo; type password CANARY-SECRET-001; click Sign in", "Invalid credentials")
        );
    }

    private static ManualTestCase caseRow(String id, String title, String callBefore, String steps, String expected) {
        return new ManualTestCase(id, title, "", steps, expected, "P1", "", "", "", "AUTOMATE", callBefore);
    }

    private static List<ProvenStep> loginSteps(String tcId, String user, String password) {
        return List.of(
                type(tcId, "LoginPage", "username", user),
                type(tcId, "LoginPage", "password", password),
                click(tcId, "LoginPage", "sign-in"));
    }

    private static ProvenStep type(String tcId, String page, String id, String value) {
        return new ProvenStep(tcId, page, "elementAction", "type",
                "id", id, value, "", "", true, "intent:TYPE");
    }

    private static ProvenStep click(String tcId, String page, String id) {
        return new ProvenStep(tcId, page, "elementAction", "click",
                "id", id, "", "", "", true, "intent:CLICK");
    }

    private static ProvenStep clickNav(String tcId, String page, String id) {
        return click(tcId, page, id);
    }

    private static ProvenStep textContains(String tcId, String page, String id, String expected) {
        return new ProvenStep(tcId, page, "elementAction", "assert",
                "id", id, "", "textContains", expected, true, "intent:ASSERT");
    }

    private static ProvenStep bodyText(String tcId, String page, String expected) {
        return new ProvenStep(tcId, page, "elementAction", "assert",
                "xpath", "//body", "", "textContains", expected, true, "intent:ASSERT");
    }

    private static ProvenStep urlContains(String tcId, String page, String expected) {
        return new ProvenStep(tcId, page, "elementAction", "assert",
                "", "", "", "urlContains", expected, true, "intent:ASSERT");
    }

    private static ProvenStep selected(String tcId, String page, String id, String expected) {
        return new ProvenStep(tcId, page, "elementAction", "assert",
                "id", id, "", "selected", expected, true, "intent:ASSERT");
    }

    private MavenRun mvnGeneratedTests(Path projectDir) throws Exception {
        return mvn(projectDir, List.of(
                "-Dtest=TC_LOGIN_OK,TC_LOGIN_BAD,TC_ASSERT_FALSE,TC_ASSERT_TRUE,TC_URL_OK,TC_SELECTED,TC_CART,TC_CHECKOUT,TC_CHECKOUT_EMPTY,TC_SECRET",
                "-DEXECUTION_TYPE=HEADLESS",
                "-DBROWSER_TYPE=CHROME",
                "-DBASE_WEB=" + baseUrl + "/login.html",
                "test"));
    }

    private MavenRun mvnOneClass(Path projectDir, String className) throws Exception {
        return mvn(projectDir, List.of(
                "-Dtest=" + className,
                "-DEXECUTION_TYPE=HEADLESS",
                "-DBROWSER_TYPE=CHROME",
                "-DBASE_WEB=" + baseUrl + "/login.html",
                "test"));
    }

    private MavenRun mvn(Path projectDir, List<String> args) throws Exception {
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        List<String> cmd = new ArrayList<>();
        cmd.add(windows ? "mvn.cmd" : "mvn");
        cmd.add("-B");
        cmd.addAll(args);
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
        boolean finished = proc.waitFor(15, TimeUnit.MINUTES);
        if (!finished) {
            proc.destroyForcibly();
            throw new IllegalStateException("mvn timed out after 15m");
        }
        Map<String, String> statuses = parseSurefire(projectDir.resolve("test-output/target/surefire-reports"));
        if (statuses.isEmpty()) {
            statuses = parseSurefire(projectDir.resolve("target/surefire-reports"));
        }
        return new MavenRun(proc.exitValue(), statuses, out.toString());
    }

    private static Map<String, String> parseSurefire(Path reports) throws Exception {
        Map<String, String> out = new LinkedHashMap<>();
        if (!Files.isDirectory(reports)) {
            return out;
        }
        parseSurefireDir(reports, out);
        Path junit = reports.resolve("junitreports");
        if (Files.isDirectory(junit)) {
            parseSurefireDir(junit, out);
        }
        return out;
    }

    private static void parseSurefireDir(Path dir, Map<String, String> out) throws Exception {
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
                String tcId = className.contains(".")
                        ? className.substring(className.lastIndexOf('.') + 1)
                        : className;
                if (tcId.endsWith("Todo")) {
                    tcId = tcId.substring(0, tcId.length() - 4);
                }
                if (xml.contains("<failure") || xml.contains("<error")) {
                    out.put(tcId, "FAIL");
                } else if (xml.contains("<skipped")) {
                    out.put(tcId, "SKIP");
                } else if (xml.contains("<testcase")) {
                    out.putIfAbsent(tcId, "PASS");
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

    private static void copyTree(Path src, Path dest) throws Exception {
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

    private static Map<String, String> hashesOf(List<ManualTestCase> cases) {
        Map<String, String> out = new LinkedHashMap<>();
        for (ManualTestCase tc : cases) {
            out.put(tc.tcId(), tc.contentHash());
        }
        return out;
    }

    private static List<ManualTestCase> replace(List<ManualTestCase> cases, ManualTestCase replacement) {
        List<ManualTestCase> out = new ArrayList<>();
        for (ManualTestCase tc : cases) {
            out.add(tc.tcId().equals(replacement.tcId()) ? replacement : tc);
        }
        return out;
    }

    private static List<ManualTestCase> append(List<ManualTestCase> cases, ManualTestCase extra) {
        List<ManualTestCase> out = new ArrayList<>(cases);
        out.add(extra);
        return out;
    }

    private static List<ProvenStep> concat(List<ProvenStep> a, List<ProvenStep> b) {
        List<ProvenStep> out = new ArrayList<>(a);
        out.addAll(b);
        return out;
    }

    private static String git(String... args) {
        try {
            List<String> cmd = new ArrayList<>();
            cmd.add("git");
            cmd.addAll(List.of(args));
            Process proc = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            String out = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            proc.waitFor(10, TimeUnit.SECONDS);
            return out;
        } catch (Exception e) {
            return "";
        }
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
        if (!Files.exists(projectDir)) {
            return 0;
        }
        try (var stream = Files.walk(projectDir)) {
            return (int) stream
                    .filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .filter(name -> name.startsWith("driver-quit-") && name.endsWith(".marker"))
                    .count();
        }
    }

    private static void assertCustomerZipHasNoCredentialMaterial(Path unzipped, String zipSha256)
            throws Exception {
        try (var stream = Files.walk(unzipped)) {
            for (Path file : stream.filter(Files::isRegularFile).toList()) {
                String name = file.getFileName().toString();
                Assert.assertFalse(name.equals(ReuseEligibility.CONTEXT_FILE),
                        "customer ZIP must not contain prove-context.json: " + file);
                Assert.assertFalse(name.equals(CredentialRevision.BINDING_FILE),
                        "customer ZIP must not contain credential-binding.json: " + file);
                Assert.assertFalse(name.equals(CredentialRevision.KEY_FILE),
                        "customer ZIP must not contain the MAC key: " + file);
                Assert.assertFalse(name.startsWith("driver-quit-"));
                String text;
                try {
                    text = Files.readString(file, StandardCharsets.UTF_8);
                } catch (Exception e) {
                    continue;
                }
                Assert.assertFalse(text.contains("credentialSecretFingerprint"), file + "\n" + text);
                Assert.assertFalse(text.contains("credential-mac.key"), file.toString());
            }
        }
        Assert.assertNotNull(zipSha256);
    }

    private record MavenRun(int exitCode, Map<String, String> statuses, String log) {
    }
}
