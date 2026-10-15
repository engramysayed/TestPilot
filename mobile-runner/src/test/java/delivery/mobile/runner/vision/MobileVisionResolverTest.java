package delivery.mobile.runner.vision;

import org.testng.annotations.Test;

import static org.testng.Assert.*;

public class MobileVisionResolverTest {

    private static VisionFrame frame() {
        return new VisionFrame(1080, 1920, 0, 0, 0, "NATIVE", false, false, true);
    }

    private static VisionProposal hit() {
        return new VisionProposal(120, 340, 90, frame(), "ui-tars");
    }

    @Test
    public void freshGeometryTapIsAllowed() {
        VisionOutcome out = MobileVisionResolver.decide(new VisionInput(frame(), hit(), false, false, "ui-tars"));
        assertTrue(out.tap());
        assertEquals(out.x(), 120);
        assertEquals(out.y(), 340);
        assertEquals(out.model(), "ui-tars");
    }

    @Test
    public void staleWidthBlocksWithoutTap() {
        VisionFrame now = new VisionFrame(1080, 1920, 0, 0, 0, "NATIVE", false, false, true);
        VisionProposal old = new VisionProposal(10, 10, 90,
                new VisionFrame(720, 1280, 0, 0, 0, "NATIVE", false, false, true), "ui-tars");
        VisionOutcome out = MobileVisionResolver.decide(new VisionInput(now, old, false, false, "ui-tars"));
        assertFalse(out.tap());
        assertEquals(out.code(), "STALE_GEOMETRY");
    }

    @Test
    public void rotationOrInsetsBlock() {
        VisionProposal rotated = new VisionProposal(10, 10, 90,
                new VisionFrame(1080, 1920, 90, 0, 0, "NATIVE", false, false, true), "ui-tars");
        assertEquals(MobileVisionResolver.decide(new VisionInput(frame(), rotated, false, false, "ui-tars")).code(),
                "STALE_GEOMETRY");
        VisionProposal insets = new VisionProposal(10, 10, 90,
                new VisionFrame(1080, 1920, 0, 40, 80, "NATIVE", false, false, true), "ui-tars");
        assertEquals(MobileVisionResolver.decide(new VisionInput(frame(), insets, false, false, "ui-tars")).code(),
                "STALE_GEOMETRY");
    }

    @Test
    public void lowConfidenceDoesNotTap() {
        VisionProposal low = new VisionProposal(10, 10, 40, frame(), "ui-tars");
        VisionOutcome out = MobileVisionResolver.decide(new VisionInput(frame(), low, false, false, "ui-tars"));
        assertFalse(out.tap());
        assertEquals(out.code(), "LOW_CONFIDENCE");
    }

    @Test
    public void blankOrSecureScreenBlocks() {
        VisionFrame blank = new VisionFrame(1080, 1920, 0, 0, 0, "NATIVE", true, false, true);
        assertEquals(MobileVisionResolver.decide(new VisionInput(blank, hit(), false, false, "ui-tars")).code(),
                "BLANK_OR_SECURE");
        VisionFrame secure = new VisionFrame(1080, 1920, 0, 0, 0, "NATIVE", false, true, true);
        assertEquals(MobileVisionResolver.decide(new VisionInput(secure, hit(), false, false, "ui-tars")).code(),
                "BLANK_OR_SECURE");
    }

    @Test
    public void missingRedactionBlocks() {
        VisionFrame open = new VisionFrame(1080, 1920, 0, 0, 0, "NATIVE", false, false, false);
        assertEquals(MobileVisionResolver.decide(new VisionInput(open, hit(), false, false, "ui-tars")).code(),
                "REDACTION_REQUIRED");
    }

    @Test
    public void missingModelIsNotAPass() {
        VisionOutcome out = MobileVisionResolver.decide(new VisionInput(frame(), hit(), false, false, ""));
        assertFalse(out.tap());
        assertFalse(out.passed());
        assertEquals(out.code(), "MISSING_PROVIDER");
    }

    @Test
    public void storedCoordinatesAreRejected() {
        VisionOutcome out = MobileVisionResolver.decide(new VisionInput(frame(), hit(), true, false, "ui-tars"));
        assertFalse(out.tap());
        assertEquals(out.code(), "STORED_COORDINATES");
    }

    @Test
    public void uncertainPostTapDoesNotRetry() {
        VisionOutcome out = MobileVisionResolver.decide(new VisionInput(frame(), hit(), false, true, "ui-tars"));
        assertFalse(out.tap());
        assertFalse(out.retry());
        assertEquals(out.code(), "UNCERTAIN_NO_RETRY");
    }
}
