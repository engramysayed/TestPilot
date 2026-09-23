package delivery.acceptance;

import delivery.authoring.ExplicitAssertionOps;
import delivery.codegen.CodeWriter;
import delivery.codegen.PageClusterer;
import delivery.codegen.ProvenStep;
import delivery.excel.ManualTestCase;
import delivery.ir.TcDraft;
import delivery.ir.TcDraftStore;
import delivery.job.CallBeforeSetup;
import delivery.job.EmitPhase;
import delivery.job.TcOutcome;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
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
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Re-emit explicit capture/compare/signed-out into the downloaded scoped replay project and run
 * {@code TC_ORD_01} / {@code TC_SES_01} only. Enable with {@code -Dkeel.checkoutAssertionReplay=true}.
 */
public class CheckoutChainAssertionReplayTest {
    private static final Path SHOP_DIR = Path.of("D:/priv/testpilot/keel-local-acceptance");
    private static final Path REPLAY = SHOP_DIR.resolve("replay-scoped-chain");
    private static final Path IR_WORK = SHOP_DIR.resolve("keel-store/127-0-0-1/kla_chain");
    private static final Path TEMPLATE = Path.of("customer-framework-template");
    private static final String SHOP_URL = "http://127.0.0.1:8896";
    private static final Pattern CAPTURED_ORDER = Pattern.compile(
            "captured phrase slot=orderId value=(Order KLA-[A-Za-z0-9]+)");
    private static final Pattern COMPARED_ORDER = Pattern.compile(
            "compared captured slot=orderId value=(Order KLA-[A-Za-z0-9]+)");

    private Process shopProcess;
    private boolean startedShop;

    @AfterClass(alwaysRun = true)
    public void stopShopIfStarted() throws Exception {
        if (!startedShop || shopProcess == null) {
            return;
        }
        shopProcess.destroy();
        shopProcess.waitFor(8, java.util.concurrent.TimeUnit.SECONDS);
        if (shopProcess.isAlive()) {
            shopProcess.destroyForcibly();
        }
    }

    @Test
    public void regenerateAndReplayExactOrderAndSignedOut() throws Exception {
        if (!Boolean.getBoolean("keel.checkoutAssertionReplay")) {
            throw new SkipException("set -Dkeel.checkoutAssertionReplay=true to re-emit and run downloaded ORD/SES");
        }
        Assert.assertTrue(Files.isDirectory(REPLAY), "downloaded replay project missing: " + REPLAY);
        Assert.assertTrue(Files.isDirectory(IR_WORK.resolve("ir")), "kla_chain IR missing: " + IR_WORK);

        List<TcDraft> clustered = new ArrayList<>();
        for (TcDraft draft : new TcDraftStore(IR_WORK).readAll()) {
            clustered.add(PageClusterer.reclusterDraft(draft));
        }
        List<ManualTestCase> pack = scopedPack();
        List<TcOutcome> bodies = new ArrayList<>();
        for (TcDraft draft : clustered) {
            bodies.add(applyExplicitOps(EmitPhase.toOutcome(draft)));
        }
        Map<String, TcOutcome> byId = new LinkedHashMap<>();
        for (TcOutcome body : bodies) {
            byId.put(body.tcId(), body);
        }
        List<TcOutcome> outcomes = new ArrayList<>();
        for (TcOutcome body : bodies) {
            outcomes.add(body.withSetup(setupFrom(body, byId, pack)));
        }
        new CodeWriter(TEMPLATE.resolve("templates")).write(REPLAY, outcomes);
        copyRuntime(REPLAY);

        String ord = Files.readString(REPLAY.resolve("src/test/java/project/tests/generated/TC_ORD_01.java"));
        String ses = Files.readString(REPLAY.resolve("src/test/java/project/tests/generated/TC_SES_01.java"));
        Assert.assertTrue(ord.contains("Capture()"), ord);
        Assert.assertTrue(ord.contains("Equals_Captured()"), ord);
        Assert.assertFalse(ord.contains("KLA-1001"), ord);
        Assert.assertTrue(ses.contains("Session expired"), ses);
        Assert.assertTrue(ses.contains("Signed_Out"), ses);
        Assert.assertFalse(ses.contains("authenticatedIdentityGone"), ses);

        ensureShop();
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        ProcessBuilder pb = new ProcessBuilder(
                windows ? "mvn.cmd" : "mvn", "-B", "-Dtest=TC_ORD_01,TC_SES_01", "test");
        pb.directory(REPLAY.toFile());
        Path logFile = SHOP_DIR.resolve("evidence/checkout-assertion-replay-mvn.log");
        LocalAcceptanceReplay.MavenRun run = LocalAcceptanceReplay.attachReports(
                LocalAcceptanceReplay.run(pb, Duration.ofMinutes(20), logFile), REPLAY);

        Assert.assertFalse(run.timedOut(), "downloaded replay timed out");
        Assert.assertEquals(status(run, "Order_on_account"), "PASS", run.log());
        Assert.assertEquals(status(run, "Session_expiry_overlay"), "PASS", run.log());

        String log = run.log() == null ? "" : run.log();
        if (Files.isRegularFile(logFile)) {
            log = log + "\n" + Files.readString(logFile, StandardCharsets.UTF_8);
        }
        Path replayLog = REPLAY.resolve("test-output/Logs/logs.log");
        if (Files.isRegularFile(replayLog)) {
            log = log + "\n" + Files.readString(replayLog, StandardCharsets.UTF_8);
        }
        Matcher captured = CAPTURED_ORDER.matcher(log);
        Matcher compared = COMPARED_ORDER.matcher(log);
        Assert.assertTrue(captured.find(), "exact-order capture did not execute:\n" + tail(log));
        Assert.assertTrue(compared.find(), "exact-order compare did not execute:\n" + tail(log));
        Assert.assertEquals(compared.group(1), captured.group(1), log);
        Assert.assertTrue(captured.group(1).length() > "Order KLA-".length(), captured.group(1));
        Assert.assertTrue(log.contains("expire-session"), "expire-session click missing:\n" + tail(log));
        Assert.assertTrue(
                log.contains("signed-out locator="),
                "signed-out assertion did not execute:\n" + tail(log));
    }

    private static List<ProvenStep> setupFrom(
            TcOutcome leaf, Map<String, TcOutcome> byId, List<ManualTestCase> pack) {
        List<ProvenStep> setup = new ArrayList<>();
        boolean skipPrereqLogin = leaf != null && leaf.needsLoginBeforeMethod();
        String leafId = leaf == null ? "" : leaf.tcId();
        for (String preId : CallBeforeSetup.prerequisiteIds(pack, leafId)) {
            TcOutcome pre = byId.get(preId);
            if (pre == null) {
                continue;
            }
            if (!skipPrereqLogin) {
                setup.addAll(pre.loginSteps());
            }
            setup.addAll(pre.provenSteps());
        }
        return setup;
    }

    private static TcOutcome applyExplicitOps(TcOutcome outcome) {
        if (outcome == null || outcome.tcId() == null) {
            return outcome;
        }
        if ("TC_ORD_01".equals(outcome.tcId())) {
            ProvenStep capture = ExplicitAssertionOps.bind(
                    "Capture from id=order-id as orderId using exact text", "TC_ORD_01")
                    .withPageName("Order");
            ProvenStep click = firstClick(outcome);
            if (click == null) {
                click = new ProvenStep("TC_ORD_01", "Order", "elementAction", "click",
                        "css", "a[href='/account.html']", "", "", "", true, "intent:CLICK");
            } else {
                click = click.withPageName("Order");
            }
            ProvenStep compare = ExplicitAssertionOps.bind(
                    "Compare captured orderId on css=#order-list li using exact text", "TC_ORD_01")
                    .withPageName("Account");
            return replaceSteps(outcome, List.of(capture, click, compare));
        }
        if ("TC_SES_01".equals(outcome.tcId())) {
            List<ProvenStep> steps = new ArrayList<>();
            for (ProvenStep step : outcome.provenSteps()) {
                if (isSignedOutStep(step)) {
                    continue;
                }
                steps.add(step);
            }
            steps.add(ExplicitAssertionOps.bind(
                    "Assert signed-out on id=session-email expected empty", "TC_SES_01")
                    .withPageName("Account"));
            steps.add(ExplicitAssertionOps.bind(
                    "Assert signed-out on id=account-email expected text Not signed in", "TC_SES_01")
                    .withPageName("Account"));
            return replaceSteps(outcome, steps);
        }
        return outcome;
    }

    private static boolean isSignedOutStep(ProvenStep step) {
        if (step == null) {
            return false;
        }
        String type = step.assertionType() == null ? "" : step.assertionType();
        String rationale = step.rationale() == null ? "" : step.rationale();
        return "signedOut".equalsIgnoreCase(type)
                || "authenticatedIdentityGone".equalsIgnoreCase(type)
                || rationale.contains("SIGNED_OUT");
    }

    private static ProvenStep firstClick(TcOutcome outcome) {
        for (ProvenStep step : outcome.provenSteps()) {
            if ("click".equalsIgnoreCase(step.action())) {
                return step;
            }
        }
        return null;
    }

    private static TcOutcome replaceSteps(TcOutcome outcome, List<ProvenStep> steps) {
        return new TcOutcome(
                outcome.tcId(),
                outcome.title(),
                outcome.status(),
                steps,
                outcome.failureReason(),
                outcome.evidenceDir(),
                outcome.needsLoginBeforeMethod(),
                outcome.loginSteps(),
                outcome.setupSteps());
    }

    private void ensureShop() throws Exception {
        if (shopUp()) {
            return;
        }
        ProcessBuilder javac = new ProcessBuilder("javac", "ShopServer.java");
        javac.directory(SHOP_DIR.toFile());
        javac.redirectErrorStream(true);
        Process compile = javac.start();
        String compileOut = new String(compile.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        Assert.assertEquals(compile.waitFor(), 0, compileOut);
        ProcessBuilder run = new ProcessBuilder("java", "ShopServer", "web", "127.0.0.1", "8896");
        run.directory(SHOP_DIR.toFile());
        run.redirectErrorStream(true);
        shopProcess = run.start();
        startedShop = true;
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(shopProcess.getInputStream(), StandardCharsets.UTF_8));
        long deadline = System.currentTimeMillis() + 20_000;
        String started = null;
        while (System.currentTimeMillis() < deadline) {
            if (reader.ready()) {
                String line = reader.readLine();
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
        Assert.assertEquals(started, SHOP_URL, "shop URL");
        new Thread(() -> {
            try {
                while (reader.readLine() != null) {
                    // drain
                }
            } catch (Exception ignored) {
            }
        }, "shop-drain").start();
    }

    private static boolean shopUp() {
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
            HttpResponse<String> response = client.send(
                    HttpRequest.newBuilder(URI.create(SHOP_URL + "/")).timeout(Duration.ofSeconds(3)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200 && response.body() != null
                    && response.body().contains("Keel Local Shop");
        } catch (Exception e) {
            return false;
        }
    }

    private static void copyRuntime(Path replay) throws Exception {
        Path javaRoot = TEMPLATE.resolve("src/main/java");
        copyOne(javaRoot.resolve("project/utils/CaptureCompare.java"),
                replay.resolve("src/main/java/project/utils/CaptureCompare.java"));
        copyOne(javaRoot.resolve("project/utils/CapturedValues.java"),
                replay.resolve("src/main/java/project/utils/CapturedValues.java"));
        copyOne(javaRoot.resolve("project/validations/Assertion.java"),
                replay.resolve("src/main/java/project/validations/Assertion.java"));
        copyOne(javaRoot.resolve("project/validations/Validation.java"),
                replay.resolve("src/main/java/project/validations/Validation.java"));
        copyOne(javaRoot.resolve("project/utils/WaitHandler.java"),
                replay.resolve("src/main/java/project/utils/WaitHandler.java"));
    }

    private static void copyOne(Path from, Path to) throws Exception {
        Files.createDirectories(to.getParent());
        Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING);
    }

    private static String status(LocalAcceptanceReplay.MavenRun run, String method) {
        if (run.statuses() == null || !run.statuses().containsKey(method)) {
            return "missing";
        }
        return run.statuses().get(method);
    }

    private static String tail(String log) {
        if (log == null) {
            return "";
        }
        return log.length() <= 4000 ? log : log.substring(log.length() - 4000);
    }

    private static List<ManualTestCase> scopedPack() {
        return List.of(
                tc("TC_ACC_02", ""),
                tc("TC_CART_01", ""),
                tc("TC_CHK_02", "TC_CART_01"),
                tc("TC_ORD_01", "TC_CHK_02"),
                tc("TC_SES_01", "TC_ORD_01"));
    }

    private static ManualTestCase tc(String id, String callBefore) {
        return new ManualTestCase(id, id, "", "", "", "P1", "local-acceptance", "", "", "AUTOMATE", callBefore);
    }
}
