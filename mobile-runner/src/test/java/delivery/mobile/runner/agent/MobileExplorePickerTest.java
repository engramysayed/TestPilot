package delivery.mobile.runner.agent;

import delivery.mobile.runner.snapshot.MobileBounds;
import delivery.mobile.runner.snapshot.MobileSnapshotNode;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Set;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class MobileExplorePickerTest {

    @Test
    public void skipsAControlAlreadyTappedAndReportsACrashDialog() {
        MobileSnapshotNode login = node(1, "com.app:id/login", "Login", true);
        MobileSnapshotNode later = node(2, "com.app:id/next", "Next", true);
        assertEquals(MobileExplorePicker.nextTap(List.of(login, later), Set.of("id:com.app:id/login")), 2);
        MobileSnapshotNode crash = node(3, "", "Keel keeps stopping", true);
        assertTrue(MobileExplorePicker.crashText(List.of(crash)).contains("keeps stopping"));
    }

    @Test
    public void ignoresAPasswordField() {
        MobileSnapshotNode secret = new MobileSnapshotNode(1, 0, "android.widget.EditText", "com.app",
                "com.app:id/password", "", "secret", "",
                new MobileBounds(0, 0, 20, 20),
                true, true, true, true, false, true, false, false, false, false, "0", List.of());
        assertEquals(MobileExplorePicker.nextTap(List.of(secret), Set.of()), -1);
    }

    private static MobileSnapshotNode node(int index, String id, String text, boolean clickable) {
        return new MobileSnapshotNode(index, 0, "android.widget.Button", "com.app", id, "", text, "",
                new MobileBounds(0, 0, 40, 40),
                true, true, clickable, false, false, false, false, false, false, false, "0", List.of());
    }
}
