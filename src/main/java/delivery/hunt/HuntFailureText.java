package delivery.hunt;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Shortens Selenium / driver failure text so journals, repro slices, and bug rows stay readable.
 */
public final class HuntFailureText {
    public static final int MAX_CHARS = 220;

    private static final String[] CUT_MARKERS = {
            "(Session info:", "Session info:", "Build info:", "System info:", "Driver info:",
            "Capabilities {", "Capabilities", "Command:", "For documentation"
    };

    /** Failure reasons that mean the hunter aimed at something absent, not a product defect. */
    private static final Pattern HUNTER_MISS = Pattern.compile(
            "(?i)(no such element|unable to locate element|text not found"
                    + "|not interactable|stale element|element click intercepted)");

    private HuntFailureText() {
    }

    public static String shorten(String raw) {
        if (raw == null) {
            return "";
        }
        String m = raw.replaceAll("\\s+", " ").trim();
        for (String marker : CUT_MARKERS) {
            int i = m.indexOf(marker);
            if (i > 0) {
                m = m.substring(0, i).trim();
            }
        }
        m = m.replaceAll("[\\s(]+$", "").trim();
        if (m.length() > MAX_CHARS) {
            m = m.substring(0, MAX_CHARS) + "…";
        }
        return m;
    }

    /**
     * True when the failure text describes the hunter missing its target (absent element,
     * text already gone) rather than the application misbehaving.
     */
    public static boolean isHunterMiss(String reason) {
        if (reason == null || reason.isBlank()) {
            return false;
        }
        return HUNTER_MISS.matcher(reason.toLowerCase(Locale.ROOT)).find();
    }
}
