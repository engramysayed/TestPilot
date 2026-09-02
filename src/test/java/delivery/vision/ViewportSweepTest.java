package delivery.vision;



import delivery.authoring.StepIntentBinder;

import org.testng.Assert;

import org.testng.annotations.Test;



import java.util.List;

import java.util.Optional;

import java.util.concurrent.atomic.AtomicInteger;



public class ViewportSweepTest {



    static final class CountingBrowser implements GroundingBrowser {

        private final int missCount;

        private final AtomicInteger scrolls = new AtomicInteger();

        private final AtomicInteger screenshots = new AtomicInteger();



        CountingBrowser(int missCount) {

            this.missCount = missCount;

        }



        int scrolls() {

            return scrolls.get();

        }



        int analyzes() {

            return screenshots.get();

        }



        @Override

        public GroundedNode elementFromPoint(double cssX, double cssY) {

            if (scrolls.get() < missCount) {

                return null;

            }

            return new GroundedNode(

                    "button", "xBtn", "", "", "X", "button", true, true, "button#xBtn");

        }



        @Override

        public byte[] screenshotPng() {

            screenshots.incrementAndGet();

            return new byte[0];

        }



        @Override

        public ViewportMetrics metrics() {

            return new ViewportMetrics(10, 10, 10, 10);

        }



        @Override

        public void scrollViewport() {

            scrolls.incrementAndGet();

        }

    }



    @Test

    public void hitsOnThirdScreen() {

        CountingBrowser browser = new CountingBrowser(2);

        FakeVisionGroundingProvider fake = new FakeVisionGroundingProvider(

                List.of(new VisualCandidate("X", new BoundingBox(1, 1, 2, 2), 0.9)));

        Optional<VisualCandidate> hit = ViewportSweep.find(fake,

                new StepIntentBinder.IntentLine(StepIntentBinder.IntentKind.CLICK, "Click X"),

                browser);

        Assert.assertTrue(hit.isPresent());

        Assert.assertEquals(browser.scrolls(), 2);

    }



    @Test

    public void stopsAtEight() {

        CountingBrowser browser = new CountingBrowser(99);

        FakeVisionGroundingProvider fake = new FakeVisionGroundingProvider(List.of());

        Assert.assertTrue(ViewportSweep.find(fake,

                new StepIntentBinder.IntentLine(StepIntentBinder.IntentKind.CLICK, "Click X"),

                browser).isEmpty());

        Assert.assertEquals(browser.analyzes(), 8);

    }



    @Test

    public void abortsOnAnImplausibleRegionBoxWithoutScrolling() {

        CountingBrowser browser = new CountingBrowser(99);

        FakeVisionGroundingProvider fake = new FakeVisionGroundingProvider(

                List.of(new VisualCandidate("region", new BoundingBox(0, 0, 9, 8), 0.99)));

        Assert.assertTrue(ViewportSweep.find(fake,

                new StepIntentBinder.IntentLine(StepIntentBinder.IntentKind.ASSERT_VISIBLE,

                        "Confirm the Password field is visible"),

                browser).isEmpty());

        Assert.assertEquals(browser.analyzes(), 1, "must not re-ask the VLM for the same giant box");

        Assert.assertEquals(browser.scrolls(), 0);

    }



    @Test

    public void rejectsABoxTallerThanAQuarterOfTheViewport() {

        ViewportMetrics metrics = new ViewportMetrics(1280, 720, 1280, 720);

        Assert.assertFalse(ViewportSweep.plausibleWidgetBox(new BoundingBox(628, 169, 1000, 200), metrics));

        Assert.assertTrue(ViewportSweep.plausibleWidgetBox(new BoundingBox(40, 400, 400, 40), metrics));

    }

    @Test
    public void skipsLowConfidenceBoxes() {
        CountingBrowser browser = new CountingBrowser(0);
        FakeVisionGroundingProvider fake = new FakeVisionGroundingProvider(
                List.of(new VisualCandidate("X", new BoundingBox(1, 1, 2, 2), 0.4)));
        Assert.assertTrue(ViewportSweep.find(fake,
                new StepIntentBinder.IntentLine(StepIntentBinder.IntentKind.CLICK, "Click X"),
                browser).isEmpty());
        Assert.assertEquals(browser.analyzes(), 1);
    }
}

