package delivery.hunt;

import delivery.settle.SettleWaiter;
import org.openqa.selenium.WebDriver;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * P1 toast observation window: an alert/toast is only bug-worthy if it is still present
 * after settle (second sample). Vanishing toasts are hunter noise, not product defects.
 */
public final class HuntToastObservation {
    /** Quiet window before second alert sample (AE-D7 / AE-L toast settle). */
    public static final long QUIET_MS = 400L;
    /** Cap for DOM-quiet wait so hunt stays responsive when the page never settles. */
    public static final long QUIET_TIMEOUT_MS = 2_000L;

    private HuntToastObservation() {}

    /**
     * Shared toast observation settle: DOM quiet only via {@link SettleWaiter#awaitDomQuiet}
     * (no network idle — offline / company-machine safe). Best-effort; never throws.
     */
    public static void awaitQuietWindow(WebDriver driver) {
        if (driver == null) {
            return;
        }
        try {
            SettleWaiter.awaitDomQuiet(driver, QUIET_MS, QUIET_TIMEOUT_MS);
        } catch (RuntimeException ignored) {
            /* toast window is best-effort; second sample still runs */
        }
    }

    /** Intersection of pre-settle and post-settle alert texts (normalized). */
    public static List<String> stillPresent(List<String> beforeSettle, List<String> afterSettle) {
        Set<String> after = new LinkedHashSet<>();
        if (afterSettle != null) {
            for (String a : afterSettle) {
                String n = norm(a);
                if (!n.isBlank()) after.add(n);
            }
        }
        List<String> kept = new ArrayList<>();
        if (beforeSettle == null) return kept;
        for (String a : beforeSettle) {
            String n = norm(a);
            if (!n.isBlank() && after.contains(n)) kept.add(a.trim());
        }
        return kept;
    }

    static String norm(String s) {
        if (s == null) return "";
        return s.toLowerCase(Locale.ROOT).replaceAll("\s+", " ").trim();
    }
}
