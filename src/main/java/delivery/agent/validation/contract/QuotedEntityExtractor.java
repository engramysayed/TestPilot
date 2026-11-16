package delivery.agent.validation.contract;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Extracts explicitly quoted UI entity labels from TC text (supports later locator/vision grounding). */
public final class QuotedEntityExtractor {
    private static final Pattern SINGLE = Pattern.compile("'([^']{2,120})'");
    private static final Pattern DOUBLE = Pattern.compile("\"([^\"]{2,120})\"");

    private QuotedEntityExtractor() {
    }

    public static List<String> extractFromText(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        Set<String> ordered = new LinkedHashSet<>();
        collect(SINGLE, text, ordered);
        collect(DOUBLE, text, ordered);
        return List.copyOf(ordered);
    }

    public static List<String> extractFromTestCase(String steps, String expectedResult) {
        List<String> out = new ArrayList<>(extractFromText(steps));
        for (String e : extractFromText(expectedResult)) {
            if (!out.contains(e)) {
                out.add(e);
            }
        }
        return List.copyOf(out);
    }

    private static void collect(Pattern pattern, String text, Set<String> sink) {
        Matcher m = pattern.matcher(text);
        while (m.find()) {
            String label = m.group(1).trim();
            if (!label.isEmpty()) {
                sink.add(label);
            }
        }
    }

    /** Heuristic: steps that reference UI surfaces should quote at least one entity. */
    public static boolean stepsLikelyNeedQuotedEntities(String steps) {
        if (steps == null || steps.isBlank()) {
            return false;
        }
        String lower = steps.toLowerCase(Locale.ROOT);
        return lower.contains(" page")
                || lower.contains(" field")
                || lower.contains(" button")
                || lower.contains("click ")
                || lower.contains("navigate")
                || lower.contains("confirm ")
                || lower.contains("assert ");
    }
}