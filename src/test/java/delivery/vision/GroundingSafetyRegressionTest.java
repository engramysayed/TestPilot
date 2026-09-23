package delivery.vision;

import delivery.authoring.StepIntentBinder;
import org.testng.Assert;
import org.testng.annotations.Test;
import java.util.concurrent.atomic.AtomicInteger;

public class GroundingSafetyRegressionTest {
    private static class Browser implements GroundingBrowser {
        String version = "before";
        boolean restored;
        int scrolls;
        public GroundedNode elementFromPoint(double x, double y) {
            return new GroundedNode("button", "save", "", "", "Save", "button", true, true, "button#save");
        }
        public byte[] screenshotPng() { return new byte[] {1, 2, 3}; }
        public ViewportMetrics metrics() { return new ViewportMetrics(800, 600, 800, 600); }
        public void scrollViewport() { scrolls++; }
        public String observationVersion() { return version; }
        public Object saveScroll() { return "original"; }
        public void restoreScroll(Object position) { restored = "original".equals(position); }
    }
    private static StepIntentBinder.IntentLine intent() {
        return new StepIntentBinder.IntentLine(StepIntentBinder.IntentKind.CLICK, "Click Save");
    }
    @Test public void unavailableProviderStopsWithoutEightRetriesAndRestoresScroll() {
        var browser = new Browser();
        AtomicInteger calls = new AtomicInteger();
        VisionGroundingProvider provider = (png, intent) -> {
            calls.incrementAndGet(); return VisionAnalysisResult.unavailable("offline");
        };
        Assert.assertTrue(ViewportSweep.run(provider, intent(), browser).isEmpty());
        Assert.assertEquals(calls.get(), 1);
        Assert.assertEquals(browser.scrolls, 0);
        Assert.assertTrue(browser.restored);
    }
    @Test public void changedDocumentCannotUseEarlierScreenshot() {
        var browser = new Browser();
        VisionGroundingProvider provider = (png, intent) -> {
            browser.version = "after";
            return VisionAnalysisResult.of(java.util.List.of(new VisualCandidate("Save", new BoundingBox(10,10,40,20), .9)));
        };
        Assert.assertTrue(ViewportSweep.run(provider, intent(), browser).isEmpty());
        Assert.assertTrue(browser.restored);
    }
}
