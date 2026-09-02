package delivery.vision;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

public class VisionBboxQualityGateTest {

    @Test
    public void rejectsTinyOneByOne() {
        var c = new VisualCandidate("Login", new BoundingBox(0, 0, 1, 1), 0.9);
        Assert.assertTrue(VisionBboxQualityGate.filter(List.of(c), 800, 600).isEmpty());
    }

    @Test
    public void rejectsLowConfidenceEvenIfBoxOk() {
        var c = new VisualCandidate("Login", new BoundingBox(10, 10, 80, 30), 0.5);
        Assert.assertTrue(VisionBboxQualityGate.filter(List.of(c), 800, 600).isEmpty());
    }

    @Test
    public void rejectsPageEssayDescription() {
        String essay = "Login page containing a username input field, password input field, and a login button, "
                + "with a message indicating the login page is for logging into the secure area";
        var c = new VisualCandidate(essay, new BoundingBox(10, 10, 80, 30), 0.9);
        Assert.assertTrue(VisionBboxQualityGate.filter(List.of(c), 800, 600).isEmpty());
    }

    @Test
    public void keepsTightHighConfWidget() {
        var c = new VisualCandidate("Login button", new BoundingBox(100, 200, 90, 36), 0.85);
        Assert.assertEquals(VisionBboxQualityGate.filter(List.of(c), 800, 600).size(), 1);
    }

    @Test
    public void rejectsHugeRegionBox() {
        var c = new VisualCandidate("Form", new BoundingBox(0, 0, 700, 500), 0.9);
        Assert.assertTrue(VisionBboxQualityGate.filter(List.of(c), 800, 600).isEmpty());
    }

    @Test
    public void shrinksOversizedLoginLabelToCenterPad() {
        var c = new VisualCandidate("Login button", new BoundingBox(535, 372, 416, 416), 0.9);
        Assert.assertTrue(VisionBboxQualityGate.filter(List.of(c), 1902, 984).isEmpty());
        VisualCandidate shrunk = VisionBboxQualityGate.shrinkOversizedToCenterPad(c, 1902, 984);
        Assert.assertNotNull(shrunk);
        Assert.assertEquals(VisionBboxQualityGate.filter(List.of(shrunk), 1902, 984).size(), 1);
        Assert.assertTrue(shrunk.boundingBox().width() <= 40);
    }
}
