package delivery.mobile.runner.agent;

import delivery.mobile.runner.snapshot.MobileBounds;
import delivery.mobile.runner.snapshot.MobileSnapshot;
import delivery.mobile.runner.snapshot.MobileSnapshotInvalidation;
import delivery.mobile.runner.snapshot.MobileSnapshotMeta;
import delivery.mobile.runner.snapshot.MobileSnapshotNode;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertTrue;

public class ScreenSignatureTest {

    @Test
    public void aDifferentControlSetIsADifferentScreen() {
        String login = ScreenSignature.of(snap("Sign in", "com.app:id/login"), "com.app");
        String pay = ScreenSignature.of(snap("Checkout", "com.app:id/pay"), "com.app");
        assertTrue(login.startsWith("SignIn"), login);
        assertTrue(pay.startsWith("Checkout"), pay);
        assertNotEquals(login, pay);
    }

    private static MobileSnapshot snap(String title, String id) {
        MobileSnapshotNode heading = new MobileSnapshotNode(0, null, "android.widget.TextView", "com.app",
                "", "", title, "", new MobileBounds(0, 0, 100, 40),
                true, true, false, false, false, false, false, false, false, false, "0", List.of());
        MobileSnapshotNode button = new MobileSnapshotNode(1, 0, "android.widget.Button", "com.app",
                id, "", "Go", "", new MobileBounds(0, 50, 100, 90),
                true, true, true, false, false, false, false, false, false, false, "0", List.of());
        return new MobileSnapshot(new MobileSnapshotMeta(1, 1L, "com.app", ".Main",
                "NATIVE", 0, 720, 1280, "en", MobileSnapshotInvalidation.NONE),
                List.of(heading, button), 0, "", 0);
    }
}
