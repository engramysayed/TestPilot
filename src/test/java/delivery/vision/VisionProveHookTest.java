package delivery.vision;

import delivery.authoring.AuthoringService;
import delivery.authoring.DomCandidate;
import delivery.authoring.LocalLlmClient;
import delivery.authoring.LocatorValidator;
import delivery.authoring.StepIntentBinder;
import delivery.codegen.ProvenStep;
import delivery.heal.FailedLocator;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Optional;

public class VisionProveHookTest {

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
    public void hookSkipsWhenDisabled() {
        System.clearProperty("delivery.vision.grounding.enabled");
        AuthoringService authoring = new AuthoringService(
                new LocalLlmClient("", ""), new LocatorValidator());
        StepIntentBinder.IntentLine intent = new StepIntentBinder.IntentLine(
                StepIntentBinder.IntentKind.CLICK, "Click Login");
        Optional<List<ProvenStep>> out = VisionProveHook.tryLayer15(
                "T", intent, "<button id=\"loginBtn\">Login</button>", new byte[0],
                List.of(), true, List.of(), authoring,
                new RecordingGroundingBrowser(null), new FakeVisionGroundingProvider());
        Assert.assertTrue(out.isEmpty());
    }

    @Test
    public void hookSkipsWhenBindSucceeded() {
        System.setProperty("delivery.vision.grounding.enabled", "true");
        try {
            AuthoringService authoring = new AuthoringService(
                    new LocalLlmClient("", ""), new LocatorValidator());
            ProvenStep strong = new ProvenStep(
                    "T", "Page", "click", "click", "id", "loginBtn", "", "", "", true, "ok");
            Optional<List<ProvenStep>> out = VisionProveHook.tryLayer15(
                    "T",
                    new StepIntentBinder.IntentLine(StepIntentBinder.IntentKind.CLICK, "Click Login"),
                    "<button id=\"loginBtn\">Login</button>", new byte[0],
                    List.of(strong), false, List.of(), authoring,
                    new RecordingGroundingBrowser(null), new FakeVisionGroundingProvider());
            Assert.assertTrue(out.isEmpty());
        } finally {
            System.clearProperty("delivery.vision.grounding.enabled");
        }
    }

    @Test
    public void hookSkipsIneligibleIntent() {
        System.setProperty("delivery.vision.grounding.enabled", "true");
        try {
            AuthoringService authoring = new AuthoringService(
                    new LocalLlmClient("", ""), new LocatorValidator());
            Optional<List<ProvenStep>> out = VisionProveHook.tryLayer15(
                    "T", null, "<a href=\"/\">Home</a>", new byte[0],
                    List.of(), true, List.of(), authoring,
                    new RecordingGroundingBrowser(null), new FakeVisionGroundingProvider());
            Assert.assertTrue(out.isEmpty());
        } finally {
            System.clearProperty("delivery.vision.grounding.enabled");
        }
    }

    @Test
    public void hookGroundsWhenWeakBindAndFakeHits() {
        System.setProperty("delivery.vision.grounding.enabled", "true");
        try {
            FakeVisionGroundingProvider fake = new FakeVisionGroundingProvider(
                    List.of(new VisualCandidate("Login", new BoundingBox(1, 1, 2, 2), 0.95)));
            GroundingBrowser browser = new RecordingGroundingBrowser(new GroundedNode(
                    "button", "loginBtn", "", "", "Login", "button", true, true, "button#loginBtn"));
            AuthoringService authoring = new AuthoringService(
                    new LocalLlmClient("", ""), new LocatorValidator());
            String html = "<body><button id=\"loginBtn\">Login</button></body>";
            Optional<List<ProvenStep>> out = VisionProveHook.tryLayer15(
                    "T",
                    new StepIntentBinder.IntentLine(StepIntentBinder.IntentKind.CLICK, "Click Login"),
                    html, new byte[0], List.of(), true, List.of(),
                    authoring, browser, fake);
            Assert.assertTrue(out.isPresent());
            Assert.assertTrue(out.get().stream().allMatch(ProvenStep::validated));
            Assert.assertEquals(out.get().get(0).locatorValue(), "loginBtn");
        } finally {
            System.clearProperty("delivery.vision.grounding.enabled");
        }
    }

    @Test
    public void hookRejectsFailedLocatorReadd() {
        System.setProperty("delivery.vision.grounding.enabled", "true");
        try {
            FakeVisionGroundingProvider fake = new FakeVisionGroundingProvider(
                    List.of(new VisualCandidate("Login", new BoundingBox(1, 1, 2, 2), 0.95)));
            GroundingBrowser browser = new RecordingGroundingBrowser(new GroundedNode(
                    "button", "loginBtn", "", "", "Login", "button", true, true, "button#loginBtn"));
            AuthoringService authoring = new AuthoringService(
                    new LocalLlmClient("", ""), new LocatorValidator());
            String html = "<body><button id=\"loginBtn\">Login</button></body>";
            List<FailedLocator> failed = List.of(new FailedLocator("id", "loginBtn", "stale"));
            Optional<List<ProvenStep>> out = VisionProveHook.tryLayer15(
                    "T",
                    new StepIntentBinder.IntentLine(StepIntentBinder.IntentKind.CLICK, "Click Login"),
                    html, new byte[0], List.of(), true, failed,
                    authoring, browser, fake);
            Assert.assertTrue(out.isEmpty());
        } finally {
            System.clearProperty("delivery.vision.grounding.enabled");
        }
    }

    @Test
    public void hookSkipsStrongBind() {
        System.setProperty("delivery.vision.grounding.enabled", "true");
        try {
            AuthoringService authoring = new AuthoringService(
                    new LocalLlmClient("", ""), new LocatorValidator());
            ProvenStep strong = new ProvenStep(
                    "T", "Page", "click", "click", "id", "loginBtn", "", "", "", true, "Bound");
            Optional<List<ProvenStep>> out = VisionProveHook.tryLayer15(
                    "T",
                    new StepIntentBinder.IntentLine(StepIntentBinder.IntentKind.CLICK, "Click Login"),
                    "<button id=\"loginBtn\">Login</button>", new byte[0],
                    List.of(strong), true, List.of(), authoring,
                    new RecordingGroundingBrowser(null),
                    new FakeVisionGroundingProvider(
                            List.of(new VisualCandidate("Login", new BoundingBox(1, 1, 2, 2), 0.95))));
            Assert.assertTrue(out.isEmpty());
        } finally {
            System.clearProperty("delivery.vision.grounding.enabled");
        }
    }
}
