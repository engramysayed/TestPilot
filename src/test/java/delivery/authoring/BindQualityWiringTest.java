package delivery.authoring;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * ProvePhase wires one BindQualityMetrics into AuthoringService + HealCascade.
 * These tests lock the recording contract without a live browser prove.
 */
public class BindQualityWiringTest {


    @Test
    public void jobTotals_accumulateOnOneMetrics_thenWriteJsonAllNonZero() throws Exception {
        BindQualityMetrics metrics = new BindQualityMetrics();
        AuthoringService authoring = newAuthoring();
        authoring.setBindQuality(metrics);
        String html = readFixture();

        authoring.authorIntent("TC_01",
                new StepIntentBinder.IntentLine(
                        StepIntentBinder.IntentKind.CLICK, "Clicks \"Support\" in the sidebar"),
                html, null, false);
        Assert.assertTrue(metrics.bindClear >= 1, "Support should record clear: " + metrics.summaryLine());

        authoring.authorIntent("TC_02",
                new StepIntentBinder.IntentLine(StepIntentBinder.IntentKind.CLICK, "Click Delete"),
                html, null, false);
        Assert.assertTrue(metrics.bindTie >= 1, "Delete should record tie: " + metrics.summaryLine());

        authoring.authorIntent("TC_03",
                new StepIntentBinder.IntentLine(StepIntentBinder.IntentKind.CLICK, "Click Warp Drive"),
                html, null, false);
        Assert.assertTrue(metrics.bindEmpty >= 1, "Warp Drive should record empty: " + metrics.summaryLine());

        Path evidence = Files.createTempDirectory("bind-quality-job");
        try {
            metrics.writeJson(evidence.resolve("bind-quality.json"));
            String json = Files.readString(evidence.resolve("bind-quality.json"));
            Assert.assertFalse(json.contains("\"clear\":0"), json);
            Assert.assertFalse(json.contains("\"tie\":0"), json);
            Assert.assertFalse(json.contains("\"empty\":0"), json);
            Assert.assertTrue(metrics.bindClear >= 1 && metrics.bindTie >= 1 && metrics.bindEmpty >= 1,
                    metrics.summaryLine());
        } finally {
            Files.deleteIfExists(evidence.resolve("bind-quality.json"));
            Files.deleteIfExists(evidence);
        }
    }

    @Test
    public void support_recordsBindClear() throws Exception {
        BindQualityMetrics metrics = new BindQualityMetrics();
        AuthoringService authoring = newAuthoring();
        authoring.setBindQuality(metrics);
        var intent = new StepIntentBinder.IntentLine(
                StepIntentBinder.IntentKind.CLICK, "Clicks \"Support\" in the sidebar");
        authoring.authorIntent("TC_01", intent, readFixture(), null, false);
        Assert.assertTrue(metrics.bindClear >= 1, metrics.summaryLine());
    }

    @Test
    public void deleteAndSettings_recordBindTie() throws Exception {
        BindQualityMetrics metrics = new BindQualityMetrics();
        AuthoringService authoring = newAuthoring();
        authoring.setBindQuality(metrics);
        authoring.authorIntent("TC_01",
                new StepIntentBinder.IntentLine(StepIntentBinder.IntentKind.CLICK, "Click Delete"),
                readFixture(), null, false);
        authoring.authorIntent("TC_01",
                new StepIntentBinder.IntentLine(StepIntentBinder.IntentKind.CLICK, "Click Settings"),
                readFixture(), null, false);
        Assert.assertTrue(metrics.bindTie >= 1, metrics.summaryLine());
    }

    @Test
    public void empty_recordsBindEmpty() throws Exception {
        BindQualityMetrics metrics = new BindQualityMetrics();
        AuthoringService authoring = newAuthoring();
        authoring.setBindQuality(metrics);
        authoring.authorIntent("TC_01",
                new StepIntentBinder.IntentLine(StepIntentBinder.IntentKind.CLICK, "Click Warp Drive"),
                readFixture(), null, false);
        Assert.assertTrue(metrics.bindEmpty >= 1, metrics.summaryLine());
    }

    @Test
    public void flush_writesJsonOnFailPath() throws Exception {
        BindQualityMetrics metrics = new BindQualityMetrics();
        AuthoringService authoring = newAuthoring();
        authoring.setBindQuality(metrics);
        Path evidence = Files.createTempDirectory("bind-quality-fail");
        try {
            try {
                authoring.authorIntent("TC_01",
                        new StepIntentBinder.IntentLine(
                                StepIntentBinder.IntentKind.CLICK, "Click Warp Drive"),
                        readFixture(), null, false);
                throw new RuntimeException("simulated prove failure after empty bind");
            } catch (RuntimeException simulated) {
                // fail path — still flush (ProvePhase finally contract)
                Assert.assertTrue(metrics.bindEmpty >= 1, metrics.summaryLine());
                metrics.writeJson(evidence.resolve("bind-quality.json"));
            }
            String json = Files.readString(evidence.resolve("bind-quality.json"));
            Assert.assertTrue(json.contains("\"empty\":"), json);
            Assert.assertFalse(json.contains("\"empty\":0"), json);
            Assert.assertFalse(json.contains("falseBind"), json);
        } finally {
            Files.deleteIfExists(evidence.resolve("bind-quality.json"));
            Files.deleteIfExists(evidence);
        }
    }

    @Test
    public void healIntentWithOllama_incrementsOllamaAsked_andPassesNeighborhood() throws Exception {
        BindQualityMetrics metrics = new BindQualityMetrics();
        AtomicReference<String> capturedSystem = new AtomicReference<>("");
        AtomicReference<String> capturedUser = new AtomicReference<>("");
        LocalLlmClient fake = new LocalLlmClient("http://127.0.0.1:9", "dummy") {
            @Override
            public String completeRankingJson(String system, String user)
                    throws IOException, InterruptedException {
                capturedSystem.set(system == null ? "" : system);
                capturedUser.set(user == null ? "" : user);
                return "{\"candidateId\":\"c1\"}";
            }
        };
        AuthoringService authoring = new AuthoringService(fake, new LocatorValidator());
        authoring.setBindQuality(metrics);
        String html = """
                <html><body>
                <nav><span data-axis-test-id="side-nav-support-submenu-button">Support</span>
                <a data-axis-test-id="side-nav-home-link">Home</a></nav>
                <main id="huge">""" + "x".repeat(4000) + """
                </main></body></html>
                """;
        List<DomCandidate> candidates = DomCandidateExtractor.extract(html);
        var intent = new StepIntentBinder.IntentLine(
                StepIntentBinder.IntentKind.CLICK, "Clicks \"Support\" in the sidebar");
        BindDecision decision = CandidateScorer.decide(CandidateScorer.score(intent, candidates));
        List<DomCandidate> shortlist = decision.shortlist();
        Assert.assertFalse(shortlist.isEmpty(), decision.toString());
        authoring.healIntentWithOllama(
                "TC_01", intent, candidates, shortlist, null, "tie", List.of(), false, html);
        Assert.assertTrue(metrics.ollamaAsked >= 1, metrics.summaryLine());
        Assert.assertFalse(capturedSystem.get().toLowerCase().contains("invent"));
        Assert.assertFalse(capturedSystem.get().toLowerCase().contains("thought"));
        Assert.assertTrue(capturedUser.get().contains("Support")
                        || capturedUser.get().contains("side-nav-support"),
                capturedUser.get());
        Assert.assertFalse(capturedUser.get().contains("x".repeat(200)),
                "neighborhood must not dump huge padding");
    }

    private static AuthoringService newAuthoring() {
        return new AuthoringService(
                new LocalLlmClient("http://127.0.0.1:9", "dummy"),
                new LocatorValidator());
    }

    private static String readFixture() {
        try (InputStream in = BindQualityWiringTest.class
                .getResourceAsStream("/locator-fixtures/scorer-sidebar.html")) {
            Assert.assertNotNull(in, "classpath /locator-fixtures/scorer-sidebar.html missing");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }
}
