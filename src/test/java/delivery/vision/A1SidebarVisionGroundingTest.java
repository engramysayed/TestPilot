package delivery.vision;

import delivery.authoring.StepIntentBinder;
import org.testng.annotations.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.testng.Assert.*;

/** A1 regression: exec_a74997b2c66c, Axis sidebar "Support" (screenshot 1920x842). */
public class A1SidebarVisionGroundingTest {

    private static final StepIntentBinder.IntentLine SUPPORT = new StepIntentBinder.IntentLine(
            StepIntentBinder.IntentKind.CLICK, "Clicks \"Support\" in the sidebar.", "");

    static byte[] png(int w, int h) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    /** UI-TARS 0-1000 point -> 1280x561 send space -> 1920x842 original: reproduces the journaled 36x36 box. */
    @Test public void uiTarsPointMapsBackToOriginalScreenshotPixels() throws Exception {
        UiTarsImagePrep.Prepared p = UiTarsImagePrep.prepare(png(1920, 842));
        assertEquals(p.sendW(), 1280);
        assertEquals(p.sendH(), 561);
        VisualCandidate c = UiTarsResponseParser.parseActionText(
                "Thought: x\nAction: click(start_box='<|box_start|>(60,885)<|box_end|>')", p.sendW(), p.sendH());
        BoundingBox orig = p.toOriginal(c.boundingBox());
        assertEquals(orig.width(), 36, "24px point pad x 1.5 = the 36px boxes seen in grounding-2026-10-11.jsonl");
        CssPoint center = CoordinateMapper.center(orig);
        // "Support" row in heal-TC_02-i0-*.png (1920x842) spans roughly x 20-260, y 730-770.
        assertTrue(center.y() > 730 && center.y() < 770, "center y=" + center.y());
    }

    @Test public void cssMappingHonoursDevicePixelRatio() {
        CssPoint css = CoordinateMapper.toCss(new CssPoint(114, 744), 1920, 842, 1536, 674);
        assertEquals(css.x(), 91.2, 0.01);
        assertEquals(css.y(), 595.5, 0.6);
    }

    /** The JS walker returns a nav <li> (H07); ViewportSweep must not reject it as "not interactive". */
    @Test public void sweepAcceptsNavListItemReturnedByElementFromPoint() throws Exception {
        byte[] shot = png(1920, 842);
        List<double[]> probes = new ArrayList<>();
        GroundingBrowser browser = new GroundingBrowser() {
            @Override public GroundedNode elementFromPoint(double x, double y) {
                probes.add(new double[]{x, y});
                return new GroundedNode("li", "", "", "", "", "", true, true, "li", "Support", "data-test",
                        "data-axis-test-id", "side-nav-support-submenu-button");
            }
            @Override public byte[] screenshotPng() { return shot; }
            @Override public ViewportMetrics metrics() { return new ViewportMetrics(1536, 674, 1920, 842); }
            @Override public void scrollViewport() { }
        };
        VisionGroundingProvider provider = new FakeVisionGroundingProvider(List.of(
                new VisualCandidate("Support", new BoundingBox(96, 726, 36, 36), 0.85)));
        Optional<VisualCandidate> hit = ViewportSweep.find(provider, SUPPORT, browser);
        assertTrue(hit.isPresent(), "nav li hit was dropped as a sweep-miss");
        assertEquals(probes.get(0)[0], 91.2, 0.01);
        assertEquals(probes.get(0)[1], 744.0 * 674 / 842, 0.01);
    }

    @Test public void sweepAcceptsMenuitemRole() throws Exception {
        byte[] shot = png(1920, 842);
        GroundingBrowser browser = new GroundingBrowser() {
            @Override public GroundedNode elementFromPoint(double x, double y) {
                return new GroundedNode("div", "", "", "", "", "menuitem", true, true, "div", "Support");
            }
            @Override public byte[] screenshotPng() { return shot; }
            @Override public ViewportMetrics metrics() { return new ViewportMetrics(1920, 842, 1920, 842); }
            @Override public void scrollViewport() { }
        };
        VisionGroundingProvider provider = new FakeVisionGroundingProvider(List.of(
                new VisualCandidate("Support", new BoundingBox(96, 726, 36, 36), 0.85)));
        assertTrue(ViewportSweep.find(provider, SUPPORT, browser).isPresent());
    }

    @Test public void probeDiagnosticNamesTheNodeInsteadOfNone() {
        String d = ViewportSweep.describeProbe(new CssPoint(91, 595), new ViewportMetrics(1536, 674, 1920, 842),
                new GroundedNode("span", "", "", "", "", "", true, true, "span", "Support"), SUPPORT);
        assertTrue(d.contains("css=91,595") && d.contains("shot=1920x842") && d.contains("view=1536x674"), d);
        assertTrue(d.contains("verdict=not-interactive"), d);
        assertTrue(ViewportSweep.describeProbe(new CssPoint(1, 1), null, null, SUPPORT).endsWith("node=null"));
    }
}
