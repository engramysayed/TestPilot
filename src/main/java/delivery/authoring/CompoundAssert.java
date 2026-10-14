package delivery.authoring;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Multi-part Excel assert lines (joined with "and") are split into simple clauses
 * and checked with Keel binding — never Cursor groundRank.
 */
public final class CompoundAssert {
    private static final Pattern QUOTED = Pattern.compile("[\"']([^\"']{2,120})[\"']");

    private CompoundAssert() {
    }

    public static boolean isCompound(String intentText) {
        if (intentText == null || intentText.isBlank()) {
            return false;
        }
        if (ExplicitAssertionOps.isExplicit(intentText)) {
            return false;
        }
        List<String> clauses = splitRawClauses(intentText);
        if (clauses.size() < 2) {
            return false;
        }
        int assertLike = 0;
        for (String clause : clauses) {
            if (looksLikeAssertClause(clause)) {
                assertLike++;
            }
        }
        return assertLike >= 2;
    }

    /** Normalized clause texts suitable for {@link StepIntentBinder.IntentLine} ASSERT_VISIBLE. */
    public static List<String> splitClauses(String intentText) {
        List<String> out = new ArrayList<>();
        for (String raw : splitRawClauses(intentText)) {
            String normalized = normalizeClause(raw);
            if (normalized != null && !normalized.isBlank()) {
                out.add(normalized);
            }
        }
        return out;
    }

    static List<String> splitRawClauses(String intentText) {
        List<String> parts = new ArrayList<>();
        if (intentText == null || intentText.isBlank()) {
            return parts;
        }
        String trimmed = intentText.trim();
        StringBuilder current = new StringBuilder();
        boolean inDouble = false;
        boolean inSingle = false;
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c == '"' && !inSingle) {
                inDouble = !inDouble;
                current.append(c);
                continue;
            }
            if (c == '\'' && !inDouble) {
                inSingle = !inSingle;
                current.append(c);
                continue;
            }
            if (!inDouble && !inSingle && trimmed.regionMatches(true, i, " and ", 0, 5)) {
                flushClause(parts, current);
                i += 4;
                continue;
            }
            current.append(c);
        }
        flushClause(parts, current);
        return parts;
    }

    private static void flushClause(List<String> parts, StringBuilder current) {
        String s = current.toString().trim();
        current.setLength(0);
        if (s.isBlank()) {
            return;
        }
        if (s.toLowerCase(Locale.ROOT).startsWith("and ")) {
            s = s.substring(4).trim();
        }
        if (!s.isBlank()) {
            parts.add(s);
        }
    }

    static String normalizeClause(String clause) {
        if (clause == null || clause.isBlank()) {
            return null;
        }
        String c = clause.trim().replaceAll("\\s+", " ");
        if (c.matches("^[\"'].+[\"']\\.?$")) {
            String inner = c.replaceAll("^[\"']|[\"']\\.?$", "").trim();
            return "Confirm the text \"" + inner + "\" is visible";
        }
        Matcher quoted = QUOTED.matcher(c);
        if (quoted.find()) {
            String phrase = quoted.group(1).trim();
            String lower = c.toLowerCase(Locale.ROOT);
            if (lower.contains("shown") || lower.contains("visible") || lower.contains("displayed")
                    || lower.contains("dashboard") || lower.contains("welcome")) {
                return "Confirm the text \"" + phrase + "\" is visible";
            }
        }
        String lower = c.toLowerCase(Locale.ROOT);
        if (!lower.contains("confirm") && !lower.contains("verify") && !lower.contains("assert")) {
            if (StepIntentBinder.extractStateAssertion(c) != null) {
                c = "Confirm " + c;
            } else if (quoted.reset().find()) {
                return "Confirm the text \"" + quoted.group(1).trim() + "\" is visible";
            } else {
                c = "Confirm " + c;
            }
        }
        if (!c.endsWith(".") && !c.endsWith("!")) {
            c = c + ".";
        }
        return c;
    }

    private static boolean looksLikeAssertClause(String clause) {
        if (clause == null || clause.isBlank()) {
            return false;
        }
        String lower = clause.toLowerCase(Locale.ROOT);
        if (StepIntentBinder.extractStateAssertion(clause) != null) {
            return true;
        }
        if (StepIntentBinder.extractAssertTextPhrase(clause) != null) {
            return true;
        }
        if (QUOTED.matcher(clause).find()) {
            return true;
        }
        return lower.contains("visible") || lower.contains("shown") || lower.contains("displayed")
                || lower.contains("not visible") || lower.contains("no longer");
    }
}
