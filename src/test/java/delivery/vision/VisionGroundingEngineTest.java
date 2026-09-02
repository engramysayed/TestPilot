package delivery.vision;

import delivery.authoring.AuthoringService;
import delivery.authoring.DomCandidate;
import delivery.authoring.LocalLlmClient;
import delivery.authoring.LocatorValidator;
import delivery.authoring.StepIntentBinder;
import delivery.codegen.ProvenStep;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

public class VisionGroundingEngineTest {

    static final class RecordingGroundingBrowser implements GroundingBrowser {
        private final GroundedNode node;

        RecordingGroundingBrowser(GroundedNode node) {
            this.node = node;
        }

        @Override
        public GroundedNode elementFromPoint(double cssX, double cssY) {
            return node;
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
    }

    @Test
    public void engineSkipsWhenDisabled() {
        System.clearProperty("delivery.vision.grounding.enabled");
        VisionGroundingEngine engine = new VisionGroundingEngine();
        List<ProvenStep> out = engine.tryGround("T",
                new StepIntentBinder.IntentLine(StepIntentBinder.IntentKind.CLICK, "Click Login"),
                List.of(), new FakeVisionGroundingProvider(), null, new byte[0], null);
        Assert.assertTrue(out.isEmpty());
    }

    @Test
    public void engineBindsWhenFakeHitsId() {
        System.setProperty("delivery.vision.grounding.enabled", "true");
        try {
            FakeVisionGroundingProvider fake = new FakeVisionGroundingProvider(
                    List.of(new VisualCandidate("Login", new BoundingBox(1, 1, 2, 2), 0.95)));
            List<DomCandidate> table = List.of(
                    new DomCandidate("c1", "id", "loginBtn", "button", "Login"));
            GroundingBrowser browser = new RecordingGroundingBrowser(new GroundedNode(
                    "button", "loginBtn", "", "", "Login", "button", true, true, "button#loginBtn"));
            AuthoringService authoring = new AuthoringService(
                    new LocalLlmClient("", ""), new LocatorValidator());
            VisionGroundingEngine engine = new VisionGroundingEngine();
            List<ProvenStep> out = engine.tryGround("T",
                    new StepIntentBinder.IntentLine(StepIntentBinder.IntentKind.CLICK, "Click Login"),
                    table, fake, browser, new byte[0], authoring);
            Assert.assertFalse(out.isEmpty());
            Assert.assertTrue(out.get(0).validated(), out.get(0).rationale());
            Assert.assertEquals(out.get(0).locatorValue(), "loginBtn");
        } finally {
            System.clearProperty("delivery.vision.grounding.enabled");
        }
    }

    @Test
    public void engineCatchesProviderThrow() {
        System.setProperty("delivery.vision.grounding.enabled", "true");
        try {
            VisionGroundingProvider throwing = new VisionGroundingProvider() {
                @Override
                public VisionAnalysisResult analyze(byte[] screenshotPng, StepIntentBinder.IntentLine intent) {
                    throw new RuntimeException("boom");
                }
            };
            GroundingBrowser browser = new RecordingGroundingBrowser(null);
            AuthoringService authoring = new AuthoringService(
                    new LocalLlmClient("", ""), new LocatorValidator());
            VisionGroundingEngine engine = new VisionGroundingEngine();
            List<ProvenStep> out = engine.tryGround("T",
                    new StepIntentBinder.IntentLine(StepIntentBinder.IntentKind.CLICK, "Click Login"),
                    List.of(), throwing, browser, new byte[0], authoring);
            Assert.assertTrue(out.isEmpty());
        } finally {
            System.clearProperty("delivery.vision.grounding.enabled");
        }
    }
}
