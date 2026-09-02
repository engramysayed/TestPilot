package delivery.vision;



import delivery.authoring.StepIntentBinder;

import utils.LogsManager;



import java.util.ArrayList;

import java.util.Comparator;

import java.util.List;

import java.util.Locale;

import java.util.Optional;

import java.util.Set;



public final class ViewportSweep {



    private static final Set<String> INTERACTIVE_TAGS = Set.of(

            "button", "a", "input", "select", "textarea");

    private static final Set<String> INTERACTIVE_ROLES = Set.of(

            "button", "link", "textbox", "combobox", "checkbox", "radio");



    private ViewportSweep() {

    }



    public record SweepHit(VisualCandidate candidate, ViewportMetrics metrics) {

    }



    public static Optional<VisualCandidate> find(

            VisionGroundingProvider provider,

            StepIntentBinder.IntentLine intent,

            GroundingBrowser browser) {

        return run(provider, intent, browser).map(SweepHit::candidate);

    }



    public static Optional<SweepHit> run(

            VisionGroundingProvider provider,

            StepIntentBinder.IntentLine intent,

            GroundingBrowser browser) {

        if (provider == null || browser == null) {

            return Optional.empty();

        }

        for (int i = 0; i < 8; i++) {

            byte[] png = browser.screenshotPng();

            ViewportMetrics metrics = withPngScreenshotDims(browser.metrics(), png);

            VisionAnalysisResult analysis;

            try {

                analysis = provider.analyze(png, intent);

            } catch (Exception e) {

                LogsManager.error("VISION_GROUND: analyze failed on sweep " + i + ": " + e.getMessage());

                analysis = VisionAnalysisResult.unavailable(e.getMessage());

            }

            if (analysis != null && analysis.error() != null) {
                VisionAttemptLog.record(VisionAttempt.of(null, 0.5, "none", false, "unavailable"));
            }

            Optional<VisualCandidate> hit = firstHit(analysis, browser, metrics);

            if (hit.isPresent()) {

                return Optional.of(new SweepHit(hit.get(), metrics));

            }

            recordSweepMiss(analysis);

            if (allCandidatesImplausible(analysis, metrics)) {
                LogsManager.info("VISION: abort implausible bbox (region, not a widget)");
                return Optional.empty();
            }
            if (allCandidatesLowConfidence(analysis)) {
                LogsManager.info("VISION: abort low-confidence boxes");
                return Optional.empty();
            }

            if (i < 7) {

                browser.scrollViewport();

            }

        }

        return Optional.empty();

    }



    private static Optional<VisualCandidate> firstHit(

            VisionAnalysisResult analysis,

            GroundingBrowser browser,

            ViewportMetrics metrics) {

        if (analysis == null || !analysis.found() || analysis.candidates() == null) {

            return Optional.empty();

        }

        List<VisualCandidate> sorted = new ArrayList<>(analysis.candidates());

        sorted.sort(Comparator.comparingDouble(VisualCandidate::confidence).reversed());

        int imageW = metrics.screenshotWidth();

        int imageH = metrics.screenshotHeight();

        int viewW = metrics.innerWidth();

        int viewH = metrics.innerHeight();

        for (VisualCandidate visual : sorted) {

            if (visual.confidence() < 0.6) {

                continue;

            }

            BoundingBox bbox = visual.boundingBox();
            if (bbox == null) {
                continue;
            }
            LogsManager.info("VISION: candidates=" + sorted.size()
                    + " bbox=" + bbox.x() + "," + bbox.y() + "," + bbox.width() + "," + bbox.height()
                    + " conf=" + visual.confidence());

            if (!plausibleWidgetBox(bbox, metrics)) {
                LogsManager.info("VISION: skip implausible bbox");
                continue;
            }

            CssPoint imageCenter = CoordinateMapper.center(visual.boundingBox());

            CssPoint css = CoordinateMapper.toCss(imageCenter, imageW, imageH, viewW, viewH);

            GroundedNode node = browser.elementFromPoint(css.x(), css.y());

            if (node != null && node.displayed() && node.enabled() && isInteractive(node)) {

                return Optional.of(visual);

            }

        }

        return Optional.empty();

    }



    private static boolean isInteractive(GroundedNode node) {

        String tag = safe(node.tag()).toLowerCase(Locale.ROOT);

        if (INTERACTIVE_TAGS.contains(tag)) {

            return true;

        }

        String role = safe(node.role()).toLowerCase(Locale.ROOT);

        return INTERACTIVE_ROLES.contains(role);

    }

    /**
     * A widget is a compact control. A box covering a large fraction of the viewport is a region
     * (form card, hero, whole column) and must not be grounded or retried.
     */
    public static boolean plausibleWidgetBox(BoundingBox box, ViewportMetrics metrics) {
        if (box == null || box.width() <= 0 || box.height() <= 0) {
            return false;
        }
        if (metrics == null) {
            return true;
        }
        int vw = Math.max(metrics.innerWidth(), metrics.screenshotWidth());
        int vh = Math.max(metrics.innerHeight(), metrics.screenshotHeight());
        if (vw <= 0 || vh <= 0) {
            return true;
        }
        double heightRatio = (double) box.height() / (double) vh;
        double areaRatio = ((double) box.width() * (double) box.height()) / ((double) vw * (double) vh);
        if (heightRatio > 0.25) {
            return false;
        }
        if (areaRatio > 0.35) {
            return false;
        }
        return true;
    }

    private static boolean allCandidatesImplausible(VisionAnalysisResult analysis, ViewportMetrics metrics) {
        if (analysis == null || analysis.candidates() == null || analysis.candidates().isEmpty()) {
            return false;
        }
        for (VisualCandidate c : analysis.candidates()) {
            if (c != null && plausibleWidgetBox(c.boundingBox(), metrics)) {
                return false;
            }
        }
        return true;
    }

    private static boolean allCandidatesLowConfidence(VisionAnalysisResult analysis) {
        if (analysis == null || analysis.candidates() == null || analysis.candidates().isEmpty()) {
            return false;
        }
        for (VisualCandidate c : analysis.candidates()) {
            if (c != null && c.confidence() >= 0.6) {
                return false;
            }
        }
        return true;
    }

    private static void recordSweepMiss(VisionAnalysisResult analysis) {
        if (analysis != null && analysis.error() != null) {
            return;
        }
        if (analysis == null || analysis.candidates() == null || analysis.candidates().isEmpty()) {
            VisionAttemptLog.record(VisionAttempt.of(null, 0.5, "none", false, "miss"));
            VisionMissJournal.recordGrounding("", "sweep-miss-empty", null, 0.5, "", "none", null);
            return;
        }
        VisualCandidate best = analysis.candidates().get(0);
        VisionAttemptLog.record(VisionAttempt.of(
                best.boundingBox(), best.confidence(), "none", false, "miss"));
        VisionMissJournal.recordGrounding(
                "",
                "sweep-miss",
                best.boundingBox(),
                best.confidence(),
                best.description(),
                "none",
                null);
    }

    private static String safe(String value) {

        return value == null ? "" : value.trim();

    }

    private static ViewportMetrics withPngScreenshotDims(ViewportMetrics metrics, byte[] png) {
        int[] ihdr = pngIhdrSize(png);
        if (ihdr == null || metrics == null) {
            return metrics;
        }
        return new ViewportMetrics(
                metrics.innerWidth(), metrics.innerHeight(), ihdr[0], ihdr[1]);
    }

    private static int[] pngIhdrSize(byte[] png) {
        if (png == null || png.length < 24) {
            return null;
        }
        if (png[0] != (byte) 0x89 || png[1] != 0x50 || png[2] != 0x4E || png[3] != 0x47) {
            return null;
        }
        int w = ((png[16] & 0xFF) << 24) | ((png[17] & 0xFF) << 16)
                | ((png[18] & 0xFF) << 8) | (png[19] & 0xFF);
        int h = ((png[20] & 0xFF) << 24) | ((png[21] & 0xFF) << 16)
                | ((png[22] & 0xFF) << 8) | (png[23] & 0xFF);
        if (w <= 0 || h <= 0) {
            return null;
        }
        return new int[]{w, h};
    }

}

