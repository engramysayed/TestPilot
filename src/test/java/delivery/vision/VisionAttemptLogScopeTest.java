package delivery.vision;

import org.testng.Assert;
import org.testng.annotations.Test;

/** r2: attempts recorded outside an intent scope must not leak into a later heal prompt. */
public class VisionAttemptLogScopeTest {
    @Test
    public void recordsOutsideAnIntentAreDropped() {
        VisionAttemptLog.endIntent();
        for (int i = 0; i < 5; i++) VisionAttemptLog.record(VisionAttempt.of(null, 0.5, "none", false, "miss"));
        Assert.assertFalse(VisionAttemptLog.inIntent());
        Assert.assertTrue(VisionAttemptLog.linesForHeal().isEmpty());
        VisionAttemptLog.beginIntent();
        try {
            Assert.assertTrue(VisionAttemptLog.inIntent());
            Assert.assertEquals(VisionAttemptLog.linesForHeal().size(), 0, "scope starts empty");
        } finally {
            VisionAttemptLog.endIntent();
        }
    }
}
