package delivery.vision;

import delivery.authoring.DomCandidate;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Optional;

public class ElementGrounderTest {

    static final class RecordingGroundingBrowser implements GroundingBrowser {
        private final GroundedNode node;
        double lastX;
        double lastY;

        RecordingGroundingBrowser(GroundedNode node) {
            this.node = node;
        }

        @Override
        public GroundedNode elementFromPoint(double cssX, double cssY) {
            lastX = cssX;
            lastY = cssY;
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
    public void matchesExistingIdCandidate() {
        List<DomCandidate> table = List.of(
                new DomCandidate("c1", "id", "loginBtn", "button", "Login"));
        VisualCandidate v = new VisualCandidate("Login", new BoundingBox(0, 0, 10, 10), 0.9);
        GroundingBrowser browser = new RecordingGroundingBrowser(new GroundedNode(
                "button", "loginBtn", "", "", "Login", "button", true, true, "button#loginBtn"));
        Optional<GroundingHit> hit = ElementGrounder.groundToHit(
                table, v, browser, 10, 10, 10, 10);
        Assert.assertTrue(hit.isPresent());
        Assert.assertEquals(hit.get().candidateId(), "c1");
        Assert.assertFalse(hit.get().added());
    }

    @Test
    public void addsValidatedCandidateWhenMissing() {
        VisualCandidate v = new VisualCandidate("Go", new BoundingBox(0, 0, 10, 10), 0.9);
        GroundingBrowser browser = new RecordingGroundingBrowser(new GroundedNode(
                "button", "goBtn", "", "", "", "button", true, true, "button#goBtn"));
        Optional<GroundingHit> hit = ElementGrounder.groundToHit(
                List.of(), v, browser, 10, 10, 10, 10);
        Assert.assertTrue(hit.isPresent());
        Assert.assertTrue(hit.get().added());
        Assert.assertEquals(hit.get().table().get(0).strategy(), "id");
        Assert.assertEquals(hit.get().table().get(0).value(), "goBtn");
    }

    @Test
    public void rejectsDisabledNode() {
        VisualCandidate v = new VisualCandidate("Checkout", new BoundingBox(0, 0, 10, 10), 0.9);
        GroundingBrowser browser = new RecordingGroundingBrowser(new GroundedNode(
                "button", "co", "", "", "", "button", true, false, "button#co"));
        Assert.assertTrue(ElementGrounder.groundToHit(List.of(), v, browser, 10, 10, 10, 10).isEmpty());
    }

    @Test
    public void rejectsNullFromPoint() {
        VisualCandidate v = new VisualCandidate("Login", new BoundingBox(0, 0, 10, 10), 0.9);
        GroundingBrowser browser = new RecordingGroundingBrowser(null);
        Assert.assertTrue(ElementGrounder.groundToHit(List.of(), v, browser, 10, 10, 10, 10).isEmpty());
    }

    @Test
    public void groundsTextOnlyButtonFromVisibleLabel() {
        VisualCandidate v = new VisualCandidate("Sign Up", new BoundingBox(0, 0, 10, 10), 0.9);
        GroundingBrowser browser = new RecordingGroundingBrowser(new GroundedNode(
                "button", "", "", "", "", "button", true, true, "button", "Sign Up"));
        Optional<GroundingHit> hit = ElementGrounder.groundToHit(
                List.of(), v, browser, 10, 10, 10, 10);
        Assert.assertTrue(hit.isPresent(), "text-only Sign Up must be groundable");
        Assert.assertTrue(hit.get().added());
        Assert.assertEquals(hit.get().table().get(0).strategy(), "xpath");
        Assert.assertTrue(hit.get().table().get(0).value().toLowerCase().contains("sign up"),
                hit.get().table().get(0).value());
    }

    @Test
    public void rejectsNotDisplayedNode() {
        VisualCandidate v = new VisualCandidate("Login", new BoundingBox(0, 0, 10, 10), 0.9);
        GroundingBrowser browser = new RecordingGroundingBrowser(new GroundedNode(
                "button", "loginBtn", "", "", "Login", "button", false, true, "button#loginBtn"));
        Assert.assertTrue(ElementGrounder.groundToHit(List.of(), v, browser, 10, 10, 10, 10).isEmpty());
    }
}
