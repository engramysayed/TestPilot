package delivery.mobile.runner.agent;

import delivery.mobile.runner.action.MobileDriverPort;
import delivery.mobile.runner.action.NormalizedSwipe;
import delivery.mobile.runner.execute.CaseOutcome;
import delivery.mobile.runner.snapshot.MobileBounds;
import delivery.mobile.runner.snapshot.MobileSnapshot;
import delivery.mobile.runner.snapshot.MobileSnapshotInvalidation;
import delivery.mobile.runner.snapshot.MobileSnapshotMeta;
import delivery.mobile.runner.snapshot.MobileSnapshotNode;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class MobileCaseRunnerWalkTest {

    @Test
    public void afterAMissLaterStepsAreLocatedAndNotTapped() {
        CountingDriver driver = new CountingDriver(payScreen());
        MobileCaseRunner runner = new MobileCaseRunner(driver, "com.app", ".Main");
        MobileCaseRunner.Outcome outcome = runner.run(new MobileCaseRunner.CaseInput(
                "TC_UP",
                List.of("Tap the \"Missing\" button", "Tap the \"Pay\" button", "Swipe up"),
                List.of(),
                List.of()), true);
        assertEquals(outcome.outcome(), CaseOutcome.FAILED);
        assertEquals(driver.taps, 0);
        assertEquals(driver.swipes, 0);
        assertTrue(outcome.locators().size() >= 2, outcome.locators().toString());
        assertTrue(outcome.locators().get(1).value().contains("pay"), outcome.locators().get(1).toString());
        assertTrue(outcome.locators().get(1).screenKey().contains("CheckoutActivity"),
                outcome.locators().get(1).screenKey());
    }

    private static MobileSnapshot payScreen() {
        MobileSnapshotNode heading = new MobileSnapshotNode(0, null, "android.widget.TextView", "com.app",
                "", "", "Checkout", "", new MobileBounds(0, 0, 100, 40),
                true, true, false, false, false, false, false, false, false, false, "0", List.of());
        MobileSnapshotNode pay = new MobileSnapshotNode(1, 0, "android.widget.Button", "com.app",
                "com.app:id/pay", "", "Pay", "", new MobileBounds(0, 50, 100, 90),
                true, true, true, false, false, false, false, false, false, false, "0", List.of());
        return new MobileSnapshot(new MobileSnapshotMeta(1, 1L, "com.app", ".CheckoutActivity",
                "NATIVE", 0, 720, 1280, "en", MobileSnapshotInvalidation.NONE),
                List.of(heading, pay), 0, "", 0);
    }

    private static final class CountingDriver implements MobileDriverPort {
        private final MobileSnapshot snap;
        int taps;
        int swipes;

        CountingDriver(MobileSnapshot snap) {
            this.snap = snap;
        }

        @Override public MobileSnapshot pageSource() { return snap; }
        @Override public void tap(int nodeIndex) { taps++; }
        @Override public void clear(int nodeIndex) { }
        @Override public void typeUnicode(int nodeIndex, String value) { }
        @Override public void typeAscii(int nodeIndex, String value) { }
        @Override public String readText(int nodeIndex) { return ""; }
        @Override public byte[] screenshot() { return new byte[0]; }
        @Override public void swipeNormalized(NormalizedSwipe swipe) { swipes++; }
    }
}
