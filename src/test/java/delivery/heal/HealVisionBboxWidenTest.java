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
import java.util.function.Supplier;

public class HealVisionBboxWidenTest {

    private static final String WEAK_HTML = "<body><button id=\"continue-control\">Proceed</button></body>";

    static final class BboxGroundingBrowser implements GroundingBrowser {
        private final GroundedNode node;

        BboxGroundingBrowser(GroundedNode node) {
            this.node = node;
        }

        @Override
        public delivery.vision.GroundedNode elementFromPoint(double cssX, double cssY) {
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
    public void bboxWidenResolvesWithoutOllama() {
        System.setProperty("delivery.vision.grounding.enabled", "true");
        try {
            List<DomCandidate> all = DomCandidateExtractor.extract(WEAK_HTML);
            DomCandidate c1 = all.get(0);
            Assert.assertEquals(c1.id(), "c1");

            FakeVisionGroundingProvider fake = new FakeVisionGroundingProvider(
                    List.of(new VisualCandidate("Proceed", new BoundingBox(1, 1, 2, 2), 0.9)));
            GroundingBrowser browser = new BboxGroundingBrowser(new GroundedNode(
                    "button", "continue-control", "", "", "Proceed", "button",
                    true, true, "button#continue-control"));

            AtomicInteger ollamaCalls = new AtomicInteger();
            LocalLlmClient llm = new LocalLlmClient("http://127.0.0.1:9", "dummy") {
                @Override
                public String completeJson(String system, String user, byte[] imagePng) {
                    ollamaCalls.incrementAndGet();
                    throw new RuntimeException("ollama must not run when bbox heals");
                }
            };
            AuthoringService authoring = new AuthoringService(llm, new LocatorValidator());
            CursorHealClient noCursor = new CursorHealClient(false, "unused", 1);
            HealCascade cascade = new HealCascade(authoring, noCursor, fake, browser);

            HealResult result = cascade.heal(
                    "TC_BBOX",
                    new StepIntentBinder.IntentLine(StepIntentBinder.IntentKind.CLICK, "Click Hyperdrive"),
                    WEAK_HTML, new byte[]{1}, "bind failed", null, true, List.of());

            Assert.assertTrue(result.ok(), result.reason());
            Assert.assertEquals(result.tierUsed(), "vision");
            Assert.assertEquals(result.steps().get(0).locatorValue(), "continue-control");
            Assert.assertTrue(result.steps().stream().allMatch(ProvenStep::validated));
            Assert.assertEquals(ollamaCalls.get(), 0);
        } finally {
            System.clearProperty("delivery.vision.grounding.enabled");
        }
    }

    @Test
    public void groundingBrowserSupplierInvokedOnHealNotAtConstruction() {
        System.setProperty("delivery.vision.grounding.enabled", "true");
        try {
            AtomicInteger supplierCalls = new AtomicInteger();
            GroundedNode node = new GroundedNode(
                    "button", "continue-control", "", "", "Proceed", "button",
                    true, true, "button#continue-control");
            Supplier<GroundingBrowser> supplier = () -> {
                supplierCalls.incrementAndGet();
                return new BboxGroundingBrowser(node);
            };

            FakeVisionGroundingProvider fake = new FakeVisionGroundingProvider(
                    List.of(new VisualCandidate("Proceed", new BoundingBox(1, 1, 2, 2), 0.9)));
            AuthoringService authoring = new AuthoringService(
                    new LocalLlmClient("http://127.0.0.1:9", "dummy"), new LocatorValidator());
            HealCascade cascade = new HealCascade(
                    authoring, new CursorHealClient(false, "unused", 1), fake, supplier);

            Assert.assertEquals(supplierCalls.get(), 0, "supplier must not run at construction");

            HealResult result = cascade.heal(
                    "TC_BBOX",
                    new StepIntentBinder.IntentLine(StepIntentBinder.IntentKind.CLICK, "Click Hyperdrive"),
                    WEAK_HTML, new byte[]{1}, "bind failed", null, true, List.of());

            Assert.assertTrue(result.ok(), result.reason());
            Assert.assertTrue(supplierCalls.get() >= 1, "supplier must run on bbox heal");
        } finally {
            System.clearProperty("delivery.vision.grounding.enabled");
        }
    }
}
