package delivery.vision;

import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

public class VisionAttemptTest {

    @AfterMethod
    public void reset() {
        VisionAttemptLog.endIntent();
        System.clearProperty("delivery.vision.heal-hints.enabled");
        System.clearProperty("delivery.vision.provider");
        System.clearProperty("delivery.vision.model");
    }

    @Test
    public void formatLine_matchesSpecShape() {
        String line = new VisionAttempt(
                "qwen", "qwen2.5vl:3b",
                new BoundingBox(412, 88, 96, 40),
                0.82, "cV2", false, "filtered").formatLine();
        Assert.assertEquals(line,
                "VISION qwen/qwen2.5vl:3b bbox=412,88,96,40 conf=0.82 grounded=cV2 used=no outcome=filtered");
        Assert.assertTrue(line.length() <= 200);
    }

    @Test
    public void linesForHeal_emptyWhenHintsDisabled() {
        VisionAttemptLog.beginIntent();
        VisionAttemptLog.record(VisionAttempt.of(
                new BoundingBox(1, 2, 3, 4), 0.9, "c1", true, "grounded"));
        System.setProperty("delivery.vision.heal-hints.enabled", "false");
        Assert.assertTrue(VisionAttemptLog.linesForHeal().isEmpty());
        Assert.assertEquals(VisionHealHints.ollamaSection(), "");
    }

    @Test
    public void linesForHeal_cappedAtEight() {
        VisionAttemptLog.beginIntent();
        System.setProperty("delivery.vision.heal-hints.enabled", "true");
        for (int i = 0; i < 12; i++) {
            VisionAttemptLog.record(VisionAttempt.of(null, 0.5, "none", false, "miss"));
        }
        Assert.assertEquals(VisionAttemptLog.linesForHeal().size(), 8);
        String section = VisionHealHints.ollamaSection();
        Assert.assertTrue(section.contains("## Vision attempts this intent"));
        Assert.assertFalse(section.contains("base64"));
    }
}
