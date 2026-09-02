package delivery.heal;

import delivery.authoring.AuthoringService;
import delivery.authoring.DomCandidate;
import delivery.authoring.DomCandidateExtractor;
import delivery.authoring.LocalLlmClient;
import delivery.authoring.LocatorValidator;
import delivery.authoring.StepIntentBinder;
import delivery.codegen.ProvenStep;
import delivery.vision.BoundingBox;
import delivery.vision.FakeVisionGroundingProvider;
import delivery.vision.GroundedNode;
import delivery.vision.GroundingBrowser;
import delivery.vision.ViewportMetrics;
import delivery.vision.VisualCandidate;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Vision-heal stress fixture (ui-tars path): Excel names a control the DOM label does not carry
 * ("Hyperdrive"), so distinctive-token DOM bind fails and vision bbox must resolve.
 * Uses {@link FakeVisionGroundingProvider} as a stand-in for ui-tars bbox JSON (no live Ollama).
 * Mirrors {@link HealVisionBboxWidenTest} contract so the scorecard is deterministic in CI.
 */
public class UiTarsHealStressTest {

    private static final String WEAK_HTML = "<body><button id=\"continue-control\">Proceed</button></body>";

    @Test
    public void visionBboxTierFiresWhenDomDistinctiveTokensMiss() {
        System.setProperty("delivery.vision.grounding.enabled", "true");
        try {
            List<DomCandidate> all = DomCandidateExtractor.extract(WEAK_HTML);
            Assert.assertEquals(all.get(0).id(), "c1");

            FakeVisionGroundingProvider fake = new FakeVisionGroundingProvider(
                    List.of(new VisualCandidate("Proceed", new BoundingBox(1, 1, 2, 2), 0.9)));
            GroundingBrowser browser = new GroundingBrowser() {
                @Override
                public GroundedNode elementFromPoint(double cssX, double cssY) {
                    return new GroundedNode(
                            "button", "continue-control", "", "", "Proceed", "button",
                            true, true, "button#continue-control");
                }

                @Override
                public byte[] screenshotPng() {
                    return new byte[0];
                }

                @Override
                public ViewportMetrics metrics() {
                    return new ViewportMetrics(10, 10, 10, 10);
                }

                @Override
                public void scrollViewport() {
                }
            };

            AtomicInteger ollamaCalls = new AtomicInteger();
            LocalLlmClient llm = new LocalLlmClient("http://127.0.0.1:9", "ui-tars") {
                @Override
                public String completeJson(String system, String user, byte[] imagePng) {
                    ollamaCalls.incrementAndGet();
                    throw new RuntimeException("ollama must not run when bbox heals");
                }
            };
            AuthoringService authoring = new AuthoringService(llm, new LocatorValidator());
            HealCascade cascade = new HealCascade(
                    authoring, new CursorHealClient(false, "unused", 1), fake, browser);

            long started = System.nanoTime();
            HealResult result = cascade.heal(
                    "TC_UITARS_STRESS",
                    new StepIntentBinder.IntentLine(
                            StepIntentBinder.IntentKind.CLICK, "Click Hyperdrive"),
                    WEAK_HTML, new byte[]{1}, "bind failed", null, true, List.of());
            long ms = (System.nanoTime() - started) / 1_000_000L;

            Assert.assertTrue(result.ok(), result.reason());
            Assert.assertEquals(result.tierUsed(), "vision",
                    "expected vision heal tier, got " + result.tierUsed());
            Assert.assertEquals(result.steps().get(0).locatorValue(), "continue-control");
            Assert.assertTrue(result.steps().stream().allMatch(ProvenStep::validated));
            Assert.assertEquals(ollamaCalls.get(), 0);
            Assert.assertTrue(ms < 30_000L, "fixture heal should be fast, took " + ms + "ms");
        } finally {
            System.clearProperty("delivery.vision.grounding.enabled");
        }
    }
}
