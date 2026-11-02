package delivery.visual;

/**
 * Feature flags for Execute visual baseline compare (Phase 4).
 * Off by default — enable with {@code -Dkeel.visual.diff.enabled=true}.
 */
public final class VisualDiffConfig {
    public static final String PROP_ENABLED = "keel.visual.diff.enabled";
    public static final String PROP_VIEWPORT = "keel.visual.diff.viewport";
    public static final String PROP_FAIL_FRACTION = "keel.visual.diff.fail-fraction";
    public static final String PROP_BLOCK = "keel.visual.diff.block";
    public static final String PROP_LUMA = "keel.visual.diff.luma-threshold";

    /** Default viewport label shared by AE + Backend until HTTP contract lands. */
    public static final String DEFAULT_VIEWPORT = "desktop";
    /** Fail when ScreenshotDiff changedFraction exceeds this (≈2% of blocks). */
    public static final double DEFAULT_FAIL_FRACTION = 0.02;
    public static final int DEFAULT_BLOCK = 8;
    public static final double DEFAULT_LUMA = 8.0;

    private VisualDiffConfig() {
    }

    public static boolean enabled() {
        return Boolean.parseBoolean(System.getProperty(PROP_ENABLED, "false"));
    }

    public static String viewport() {
        String v = System.getProperty(PROP_VIEWPORT, DEFAULT_VIEWPORT);
        return (v == null || v.isBlank()) ? DEFAULT_VIEWPORT : v.trim();
    }

    public static double failFraction() {
        try {
            double d = Double.parseDouble(System.getProperty(PROP_FAIL_FRACTION,
                    Double.toString(DEFAULT_FAIL_FRACTION)));
            if (Double.isNaN(d) || d < 0) {
                return DEFAULT_FAIL_FRACTION;
            }
            return d;
        } catch (NumberFormatException e) {
            return DEFAULT_FAIL_FRACTION;
        }
    }

    public static int block() {
        try {
            int b = Integer.parseInt(System.getProperty(PROP_BLOCK, Integer.toString(DEFAULT_BLOCK)));
            return b < 1 ? DEFAULT_BLOCK : b;
        } catch (NumberFormatException e) {
            return DEFAULT_BLOCK;
        }
    }

    public static double lumaThreshold() {
        try {
            double d = Double.parseDouble(System.getProperty(PROP_LUMA, Double.toString(DEFAULT_LUMA)));
            if (Double.isNaN(d) || d < 0) {
                return DEFAULT_LUMA;
            }
            return d;
        } catch (NumberFormatException e) {
            return DEFAULT_LUMA;
        }
    }
}
