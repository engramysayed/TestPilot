package delivery.vision;

import utils.PropertyReader;

import java.util.Locale;

/**
 * UI-TARS coordinate contract (aligned with Midscene ui-tars/parser.ts + @ui-tars/action-parser and
 * model-adapter/image-preprocess.ts).
 * <ol>
 *   <li>Model space: {@link Space#THOUSAND} (UI-TARS 1.0 / Doubao, and our prompt: 0..1000 of the image
 *   the model saw) or {@link Space#SMART_RESIZE_V15} (UI-TARS 1.5: absolute pixels in the
 *   smart_resize(factor 28) dimensions of the image the model saw).</li>
 *   <li>"Image the model saw" = the <b>prepared</b> image, including any padding. Normalised values are
 *   mapped against the prepared size, then clamped to the content (unpadded) area.</li>
 *   <li>Content (sent) pixels scale back to the original screenshot (we downscale to MAX_SIDE).</li>
 *   <li>Screenshot pixels to CSS: × innerWidth/screenshotWidth (covers DPR; see CoordinateMapper).</li>
 * </ol>
 */
public final class UiTarsCoords {
    public static final String SPACE_KEY = "delivery.vision.uitars.coord-space";
    public static final int FACTOR = 28;
    static final long MIN_PIXELS = 100L * FACTOR * FACTOR;
    static final long MAX_PIXELS_V15 = 16384L * FACTOR * FACTOR;

    public enum Space { THOUSAND, SMART_RESIZE_V15 }

    /** Geometry of one model call. model = prepared (maybe padded); content = real pixels inside it. */
    public record Grid(Space space, int modelW, int modelH, int contentW, int contentH) {
        /** Model coordinate to content (sent-image) pixel, clamped to the content area. */
        public double[] toContentPx(double mx, double my) {
            double fx, fy;
            if (space == Space.SMART_RESIZE_V15) {
                int[] s = smartResizeV15(modelW, modelH);
                fx = s[0]; fy = s[1];
            } else {
                fx = 1000; fy = 1000;
            }
            double px = mx / fx * modelW;
            double py = my / fy * modelH;
            return new double[]{clamp(px, contentW), clamp(py, contentH)};
        }

        /** Largest valid raw model coordinate (exclusive bound check for parsers). */
        public double maxX() { return space == Space.THOUSAND ? 1000 : smartResizeV15(modelW, modelH)[0]; }
        public double maxY() { return space == Space.THOUSAND ? 1000 : smartResizeV15(modelW, modelH)[1]; }
    }

    private UiTarsCoords() {
    }

    static double clamp(double v, int size) {
        return size <= 0 ? v : Math.max(0, Math.min(size - 1, v));
    }

    /** @ui-tars/action-parser smartResizeForV15: returns [w, h]. */
    public static int[] smartResizeV15(int width, int height) {
        int wBar = Math.max(FACTOR, roundBy(width));
        int hBar = Math.max(FACTOR, roundBy(height));
        if ((long) hBar * wBar > MAX_PIXELS_V15) {
            double beta = Math.sqrt((double) height * width / MAX_PIXELS_V15);
            hBar = (int) (Math.floor(height / beta / FACTOR) * FACTOR);
            wBar = (int) (Math.floor(width / beta / FACTOR) * FACTOR);
        } else if ((long) hBar * wBar < MIN_PIXELS) {
            double beta = Math.sqrt((double) MIN_PIXELS / ((double) height * width));
            hBar = (int) (Math.ceil(height * beta / FACTOR) * FACTOR);
            wBar = (int) (Math.ceil(width * beta / FACTOR) * FACTOR);
        }
        return new int[]{wBar, hBar};
    }

    private static int roundBy(int v) {
        return (int) (Math.round((double) v / FACTOR) * FACTOR);
    }

    /** Full chain for one model point: model space → content px → original screenshot px → CSS px. */
    public static double[] toCss(double mx, double my, Grid g, int origW, int origH, int innerW, int innerH) {
        double[] c = g.toContentPx(mx, my);
        double ox = g.contentW() > 0 ? c[0] * origW / g.contentW() : c[0];
        double oy = g.contentH() > 0 ? c[1] * origH / g.contentH() : c[1];
        CssPoint css = CoordinateMapper.toCss(new CssPoint(ox, oy), origW, origH, innerW, innerH);
        return new double[]{css.x(), css.y()};
    }

    public static Space configuredSpace() {
        String v = System.getProperty(SPACE_KEY);
        if (v == null || v.isBlank()) v = PropertyReader.getProperty(SPACE_KEY);
        if (v == null || v.isBlank()) return Space.THOUSAND;
        return switch (v.trim().toLowerCase(Locale.ROOT)) {
            case "thousand", "1000", "v1.0" -> Space.THOUSAND;
            case "smart-resize", "v1.5" -> Space.SMART_RESIZE_V15;
            default -> throw new IllegalArgumentException(SPACE_KEY + "='" + v + "'; use thousand or smart-resize");
        };
    }
}
