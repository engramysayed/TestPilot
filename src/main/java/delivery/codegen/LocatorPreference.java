package delivery.codegen;

import java.util.Locale;

/**
 * Site-agnostic heuristics for preferring click/control locators over bare container ids.
 */
public final class LocatorPreference {
    private LocatorPreference() {
    }

    public static int score(String strategy, String value) {
        String normalizedStrategy = strategy == null ? "" : strategy.trim().toLowerCase(Locale.ROOT);
        String normalizedValue = value == null ? "" : value.toLowerCase(Locale.ROOT);
        int score = 0;
        if (containsButtonHint(normalizedValue)) {
            score += 2;
        }
        if (isBareIdOrName(normalizedStrategy, normalizedValue)) {
            score -= 3;
        }
        return score;
    }

    public static boolean prefer(String s1, String v1, String s2, String v2) {
        return score(s1, v1) > score(s2, v2);
    }

    private static boolean containsButtonHint(String value) {
        if (value.isEmpty()) {
            return false;
        }
        return value.contains("button")
                || value.contains("type='submit'")
                || value.contains("type=\"submit\"")
                || value.contains("type=submit")
                || value.contains("role=button")
                || value.contains("input[type=submit]")
                || value.contains("/button")
                || value.contains("btn");
    }

    private static boolean isBareIdOrName(String strategy, String value) {
        if (!"id".equals(strategy) && !"name".equals(strategy)) {
            return false;
        }
        return !containsButtonHint(value);
    }
}
