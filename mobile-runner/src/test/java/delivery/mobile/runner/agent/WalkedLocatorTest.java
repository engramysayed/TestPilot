package delivery.mobile.runner.agent;

import delivery.mobile.runner.snapshot.MobileBounds;
import delivery.mobile.runner.snapshot.MobileSnapshotNode;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class WalkedLocatorTest {

    @Test
    public void resourceIdWinsOverText() {
        WalkedLocator loc = WalkedLocator.fromNode(node("com.app:id/login", "Login", "Sign in"), "Sign in");
        assertEquals(loc.strategy(), "id");
        assertEquals(loc.value(), "com.app:id/login");
        assertFalse(loc.shell());
    }

    @Test
    public void contentDescWhenNoId() {
        WalkedLocator loc = WalkedLocator.fromNode(node("", "login_submit", "Sign in"), "Sign in");
        assertEquals(loc.strategy(), "accessibilityId");
        assertEquals(loc.value(), "login_submit");
    }

    @Test
    public void visibleTextWhenNothingStable() {
        WalkedLocator loc = WalkedLocator.fromNode(node("", "", "Sign in"), "button");
        assertEquals(loc.strategy(), "text");
        assertEquals(loc.value(), "Sign in");
        assertFalse(loc.shell());
    }

    @Test
    public void emptyNodeIsAShellOfTheExcelLabel() {
        WalkedLocator loc = WalkedLocator.fromNode(node("", "", ""), "Login");
        assertTrue(loc.shell());
        assertEquals(loc.value(), "Login");
    }

    private static MobileSnapshotNode node(String id, String desc, String text) {
        return new MobileSnapshotNode(1, 0, "android.widget.Button", "com.app", id, desc, text, "",
                new MobileBounds(0, 0, 10, 10),
                true, true, true, false, false, false, false, false, false, false, "0", List.of());
    }
}
