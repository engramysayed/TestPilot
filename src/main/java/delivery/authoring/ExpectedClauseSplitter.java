package delivery.authoring;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Splits an Expected Result cell or column into outcome clauses.
 * Numbered lists ({@code 1. 2. 3.}), dash bullets, and outcome {@code and}
 * split; mid-sentence {@code expands and sub-menu} does not.
 */
public final class ExpectedClauseSplitter {
    private static final Pattern NUMBERED = Pattern.compile("(?m)(?:^|\\n)\\s*\\d+[.)]\\s+");
    private static final Pattern DASH_LINE = Pattern.compile("(?m)(?:^|\\n)\\s*[-–—•*]\\s+");
    private static final Pattern OUTCOME_AND = Pattern.compile(
            "(?i)(?:\\n\\s*and\\s+|(?<=\\S)\\s+and\\s+(?=the\\s+[\"'A-Z])|(?<=\\S)\\s+and\\s+(?=[\"']))");

    private ExpectedClauseSplitter() {
    }

    /** Top-level expected cells (numbered items and newlines), still unsplit on outcome-and. */
    public static List<String> rows(String expected) {
        List<String> cells = split(expected, NUMBERED);
        List<String> out = new ArrayList<>();
        for (String cell : cells) {
            for (String dash : split(cell, DASH_LINE)) {
                String t = tidy(dash);
                if (!t.isBlank()) {
                    out.add(t);
                }
            }
        }
        return out;
    }

    /** Clauses inside one expected cell, including outcome {@code and} / leftover numbered. */
    public static List<String> clauses(String cell) {
        List<String> out = new ArrayList<>();
        for (String row : rows(cell == null ? "" : cell)) {
            for (String part : split(row, OUTCOME_AND)) {
                String t = tidy(part);
                if (t.toLowerCase(Locale.ROOT).startsWith("and ")) {
                    t = t.substring(4).trim();
                }
                if (!t.isBlank()) {
                    out.add(t);
                }
            }
        }
        return out;
    }

    private static List<String> split(String text, Pattern marker) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return out;
        }
        String[] parts = marker.split(text);
        for (String part : parts) {
            String t = part.replace('\u00a0', ' ').trim();
            if (!t.isBlank()) {
                out.add(t);
            }
        }
        if (out.isEmpty()) {
            String t = text.replace('\u00a0', ' ').trim();
            if (!t.isBlank()) {
                out.add(t);
            }
        }
        return out;
    }

    private static String tidy(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace('\u00a0', ' ').replaceAll("[ \\t]*\\n[ \\t]*", "\n")
                .replaceAll("[ \\t]+", " ").trim()
                .replaceFirst("^(?i)and\\s+", "").trim();
    }
}
