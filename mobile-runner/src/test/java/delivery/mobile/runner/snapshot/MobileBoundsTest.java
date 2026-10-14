package delivery.mobile.runner.snapshot;

import org.testng.annotations.Test;

import static org.testng.Assert.*;

public class MobileBoundsTest {
    @Test
    public void parsesAndCenters() {
        MobileBounds b = MobileBounds.parse("[10,20][110,220]");
        assertEquals(b.width(), 100);
        assertEquals(b.height(), 200);
        assertEquals(b.centerX(), 60);
        assertEquals(b.centerY(), 120);
    }

    @Test
    public void badBoundsThrow() {
        try {
            MobileBounds.parse("nope");
            fail();
        } catch (MobileSnapshotException e) {
            assertEquals(e.code(), MobileSnapshotException.Code.BAD_BOUNDS);
        }
    }
}
