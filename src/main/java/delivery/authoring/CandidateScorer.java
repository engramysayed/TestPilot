package delivery.authoring;

import delivery.store.PreferredHooksStore;

import java.util.List;
import java.util.Locale;

public final class CandidateScorer {
    public static final int MARGIN = 12;
    public static final int MIN_TOTAL = 15;
    public static final int SHORTLIST_MAX = 5;

    private CandidateScorer() {}

    public static int stability(DomCandidate c) {
        if (c == null) return 0;
        String s = c.strategy() == null ? "" : c.strategy().toLowerCase(Locale.ROOT);
        String v = c.value() == null ? "" : c.value();
        if (PreferredHooksStore.matches(v, PreferredHooksStore.current())
                || PreferredHooksStore.matches(s, PreferredHooksStore.current())) {
            return 50;
        }
        if (("css".equals(s) || "cssselector".equals(s) || "xpath".equals(s))
                && (v.contains("data-axis-test-id") || containsPreferredHook(v))) {
            return 50;
        }
        if ("data-testid".equals(s) || "data-test".equals(s) || "data-qa".equals(s) || "testid".equals(s)) {
            return 45;
        }
        if (v.contains("data-testid") || v.contains("data-test=") || v.contains("data-qa=")) {
            return 45;
        }
        if (v.contains("[role=") && (v.contains("aria-label") || v.contains("name="))) {
            return 40;
        }
        if (v.contains("aria-label") || s.contains("aria")) {
            return 36;
        }
        if ("name".equals(s)) {
            return 30;
        }
        if ("id".equals(s)) {
            return GeneratedIdDetector.looksGenerated(v) ? 0 : 24;
        }
        if ("css".equals(s) || "cssselector".equals(s)) {
            if (v.contains(":nth-")) return 2;
            return 12;
        }
        if ("xpath".equals(s)) {
            if (v.matches(".*\\[\\d+\\].*")) return 2;
            return 8;
        }
        return 0;
    }

    private static boolean containsPreferredHook(String value) {
        if (value == null || value.isBlank()) return false;
        String hay = value.toLowerCase(Locale.ROOT);
        for (String hook : PreferredHooksStore.current()) {
            if (hook != null && !hook.isBlank() && hay.contains(hook.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}