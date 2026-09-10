package delivery.excel;

import delivery.portal.model.KeelPath;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Routes workbook rows to Automate vs Execute by {@code KeelPath}.
 * <ul>
 *   <li>AUTOMATE / EXECUTE / VISION_ONLY → both Automate and Execute</li>
 *   <li>MANUAL → neither (human review only)</li>
 *   <li>blank / missing → both (legacy sheets without KeelPath)</li>
 * </ul>
 * KeelPath remains a hint for Generate / operators; it no longer blocks Automate for
 * cases that Execute can already run.
 */
public final class KeelPathCaseFilter {
    public enum Surface { AUTOMATE, EXECUTE }

    private KeelPathCaseFilter() {
    }

    public static List<ManualTestCase> forSurface(List<ManualTestCase> cases, Surface surface) {
        if (cases == null || cases.isEmpty()) {
            return List.of();
        }
        List<ManualTestCase> out = new ArrayList<>();
        for (ManualTestCase tc : cases) {
            if (eligible(tc, surface)) {
                out.add(tc);
            }
        }
        return out;
    }

    public static List<ManualTestCase> requireForSurface(
            List<ManualTestCase> cases,
            Surface surface,
            String emptyMessage
    ) {
        List<ManualTestCase> filtered = forSurface(cases, surface);
        if (filtered.isEmpty()) {
            throw new IllegalArgumentException(emptyMessage == null || emptyMessage.isBlank()
                    ? defaultEmptyMessage(surface)
                    : emptyMessage);
        }
        return filtered;
    }

    static boolean eligible(ManualTestCase tc, Surface surface) {
        KeelPath path = resolve(tc == null ? null : tc.keelPath());
        if (path == null) {
            return true; // blank = legacy → both surfaces
        }
        if (path == KeelPath.MANUAL) {
            return false;
        }
        // Automate and Execute share the same runnable set (everything except MANUAL).
        return path == KeelPath.AUTOMATE
                || path == KeelPath.EXECUTE
                || path == KeelPath.VISION_ONLY;
    }

    /** Null means blank / unspecified (legacy). Throws if value is present but unknown. */
    static KeelPath resolve(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return KeelPath.parse(raw.trim().toUpperCase(Locale.ROOT));
    }

    private static String defaultEmptyMessage(Surface surface) {
        return switch (surface) {
            case AUTOMATE -> "No runnable test cases in workbook (only MANUAL rows are skipped on Automate)";
            case EXECUTE -> "No runnable test cases in workbook (only MANUAL rows are skipped on Execute)";
        };
    }
}
