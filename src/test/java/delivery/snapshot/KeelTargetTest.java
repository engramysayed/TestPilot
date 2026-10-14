package delivery.snapshot;

import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.testng.Assert.*;

public class KeelTargetTest {
    @Test public void roundTripsAllHopKinds() {
        String s = "shadow=x-open >> frame=#sf >> shadow-closed=x-closed#c >> css=button.nested";
        KeelTarget t = KeelTarget.parse(s);
        assertEquals(t.hops().size(), 4);
        assertEquals(t.hops().get(2).kind(), KeelTarget.Kind.SHADOW_CLOSED);
        assertEquals(t.format(), s);
    }
    @Test public void escapesSeparatorInsideSelector() {
        KeelTarget t = new KeelTarget(List.of(new KeelTarget.Hop(KeelTarget.Kind.CSS, "a[title=\"x >> y\"]")));
        assertEquals(KeelTarget.parse(t.format()).terminalCss(), "a[title=\"x >> y\"]");
    }
    @Test public void plainCssIsBackwardCompatible() {
        assertTrue(KeelTarget.parse("css=#login").isPlainCss());
    }
    @Test(expectedExceptions = IllegalArgumentException.class) public void cssMustBeLast() {
        KeelTarget.parse("css=a >> frame=#f");
    }
    @Test(expectedExceptions = IllegalArgumentException.class) public void unknownHopRejected() {
        KeelTarget.parse("xpath=//a");
    }
    @Test public void iframeHostedInShadowEmitsShadowHopsBeforeFrameHop() {
        PageModel m = PageSerializer.parse(Map.of("hook", true, "frames", 1, "truncated", false, "candidates", List.of(
                Map.of("tag", "button", "role", "", "name", "Frame in shadow", "css", "button",
                        "framePath", List.of(Map.of("selector", "#sf", "index", 0,
                                "shadowPath", List.of(Map.of("host", "#o", "mode", "open")))),
                        "shadowPath", List.of()))));
        assertEquals(m.candidates().get(0).target().format(), "shadow=#o >> frame=#sf >> css=button");
    }
}
