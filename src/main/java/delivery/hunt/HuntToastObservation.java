package delivery.hunt;

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
    private HuntToastObservation() {}

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
        return s.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }
}
