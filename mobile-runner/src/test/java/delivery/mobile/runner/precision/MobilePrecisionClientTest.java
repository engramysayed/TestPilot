package delivery.mobile.runner.precision;

import delivery.mobile.runner.snapshot.MobileBounds;
import delivery.mobile.runner.snapshot.MobileSnapshotNode;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Pure unit coverage for the client-side helpers that don't need an HTTP stack. The
 * production {@code pick(...)} path is covered end-to-end by the service test on the portal
 * side (which exercises the gate the client reads back).
 */
public class MobilePrecisionClientTest {

    @Test
    public void excerptContainsClassBoundsAndClickableFlag() {
        MobileSnapshotNode a = node(1, "android.widget.Button", "sign in now", "btn_a", true);
        MobileSnapshotNode b = node(2, "android.widget.TextView", "please sign in", "btn_b", false);
        String xml = MobilePrecisionClient.buildExcerpt(List.of(a, b));
        assertTrue(xml.contains("class=\"android.widget.Button\""), xml);
        assertTrue(xml.contains("bounds=\"[0,0][100,100]\""), xml);
        assertTrue(xml.contains("clickable=\"true\""), xml);
        assertTrue(xml.contains(">sign in now</node>"), xml);
        assertTrue(xml.contains(">please sign in</node>"), xml);
    }

    @Test
    public void excerptEscapesSpecialCharacters() {
        MobileSnapshotNode n = node(1, "Button", "Sign \"in\" & go <now>", "btn", true);
        String xml = MobilePrecisionClient.buildExcerpt(List.of(n));
        assertTrue(xml.contains("Sign &quot;in&quot; &amp; go &lt;now&gt;"), xml);
        // Raw < or > inside the text would break the sidecar parser; the opening tag has them.
        assertFalse(xml.contains("<now>"), xml);
    }

    private static MobileSnapshotNode node(int idx, String cls, String text, String resource, boolean clickable) {
        return new MobileSnapshotNode(idx, null, cls, "com.example", resource, "",
                text, "", new MobileBounds(0, 0, 100, 100),
                true, true, clickable, false, false, false, false, false, false, false, "0", List.of());
    }
}
