package delivery.hunt;

import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.*;

public class HuntProgressStopTest {
    @Test public void detectsNoProgress() {
        var a = new HuntProgressStop.Snapshot(2, 1, 0);
        var b = new HuntProgressStop.Snapshot(2, 1, 0);
        assertTrue(HuntProgressStop.noProgress(a, b, true));
        assertFalse(HuntProgressStop.noProgress(a, new HuntProgressStop.Snapshot(3, 1, 0), true));
    }
}
