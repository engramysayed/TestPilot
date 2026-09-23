package delivery.vision;

import delivery.authoring.*;
import org.testng.Assert;
import org.testng.annotations.Test;
import java.util.List;

/** Safe-behavior regressions for the grounding audit. */
public class GroundingAuditCharacterizationTest {
    @Test public void invalidNativeBoxDoesNotBecomeACornerPoint() {
        Assert.assertTrue(UiTarsResponseParser.parse("Action: click(start_box='(0,0,2000,2000)')", 800, 600).candidates().isEmpty());
        Assert.assertTrue(UiTarsResponseParser.parse("Action: click(start_box='(20,20,20,20)')", 800, 600).candidates().isEmpty());
    }
    @Test public void jsonPointNeverMixesPixelsAndThousandGridAcrossAxes() {
        var parsed = UiTarsResponseParser.parse("{\"point\":[500,700],\"confidence\":0.9}", 800, 600);
        var center = CoordinateMapper.center(parsed.candidates().getFirst().boundingBox());
        Assert.assertEquals(center.x(), 400.0);
        Assert.assertEquals(center.y(), 420.0);
    }
    @Test
    public void loginIntentRejectsUsernameClick() {
        String old = System.getProperty("delivery.vision.grounding.enabled");
        System.setProperty("delivery.vision.grounding.enabled", "true");
        try {
            GroundingBrowser browser = new GroundingBrowser() {
                public GroundedNode elementFromPoint(double x, double y) {
                    return new GroundedNode("input", "username", "username", "", "", "textbox",
                            true, true, "input#username", "");
                }
                public byte[] screenshotPng() { return new byte[0]; }
                public ViewportMetrics metrics() { return new ViewportMetrics(1000, 800, 1000, 800); }
                public void scrollViewport() {}
            };
            var result = new VisionGroundingEngine().tryGround("audit",
                    new StepIntentBinder.IntentLine(StepIntentBinder.IntentKind.CLICK_LOGIN, "Click Login"),
                    List.of(new DomCandidate("c1", "id", "username", "input", "Username")),
                    new FakeVisionGroundingProvider(List.of(new VisualCandidate("Login",
                            new BoundingBox(100, 100, 24, 24), .85))), browser, new byte[0],
                    new AuthoringService(new LocalLlmClient("", ""), new LocatorValidator()));
            Assert.assertTrue(result.isEmpty());
        } finally {
            if (old == null) System.clearProperty("delivery.vision.grounding.enabled");
            else System.setProperty("delivery.vision.grounding.enabled", old);
        }
    }

    @Test
    public void duplicateVisibleTextCannotReplaceObservedIdentity() {
        var hit = ElementGrounder.addOrMatch(List.of(
                new DomCandidate("wrong", "id", "delete-first", "button", "Delete")),
                new GroundedNode("button", "delete-second", "", "", "", "button", true, true,
                        "button#delete-second", "Delete"),
                new VisualCandidate("Delete", new BoundingBox(10, 10, 24, 24), .85)).orElseThrow();
        Assert.assertNotEquals(hit.candidateId(), "wrong");
        Assert.assertEquals(hit.table().get(hit.table().size() - 1).value(), "delete-second");
    }

    @Test
    public void nativePointAtImageEdgeKeepsItsCenter() {
        var parsed = UiTarsResponseParser.parse("Action: click(start_box='(0,500)')", 1000, 800);
        Assert.assertEquals(CoordinateMapper.center(parsed.candidates().get(0).boundingBox()).x(), 0.0);
    }

    @Test
    public void nativePointAndBoxBothUseThousandGrid() {
        var point = UiTarsResponseParser.parse("Action: click(start_box='(0.5,0.5)')", 1000, 800);
        var box = UiTarsResponseParser.parse("Action: click(start_box='(0.49,0.49,0.51,0.51)')", 1000, 800);
        Assert.assertTrue(CoordinateMapper.center(point.candidates().get(0).boundingBox()).x() < 20);
        Assert.assertTrue(box.candidates().isEmpty() || CoordinateMapper.center(box.candidates().get(0).boundingBox()).x() < 20);
    }
}
