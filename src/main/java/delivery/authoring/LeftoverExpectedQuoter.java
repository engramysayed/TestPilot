package delivery.authoring;

import delivery.excel.ManualTestCase;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Quotes distinctive leftover names in Expected Result after stripping filler words. */
public final class LeftoverExpectedQuoter {
    private static final Pattern QUOTED = Pattern.compile("'([^']*)'|\"([^\"]*)\"");
    private static final Pattern NUMBERED_LIST = Pattern.compile("(?m)(?:^|\\n)\\s*\\d+[.)]\\s+");
    private static final Pattern DASH_MARKER = Pattern.compile("(?m)^\\s*([-–—•*])\\s+");

    private LeftoverExpectedQuoter() {
    }

    public static ManualTestCase quoteCase(ManualTestCase tc) {
        String expected = tc.expectedResult();
        if (expected == null || expected.isBlank()) {
            return tc;
        }
        String rewritten = rewriteExpected(expected, tc.steps(), tc.title());
        if (rewritten.equals(expected)) {
            return tc;
        }
        return new ManualTestCase(
                tc.tcId(),
                tc.title(),
                tc.preconditions(),
                tc.steps(),
                rewritten,
                tc.priority(),
                tc.tags(),
                tc.visualAssertion(),
                tc.testData(),
                tc.keelPath(),
                tc.callBefore());
    }

    private static String rewriteExpected(String expected, String steps, String title) {
        List<String> rowBodies = ExpectedClauseSplitter.rows(expected);
        if (rowBodies.isEmpty()) {
            return expected;
        }
        boolean numbered = NUMBERED_LIST.matcher(expected).find();
        String dash = numbered ? null : leadingDash(expected);
        List<String> rewritten = new ArrayList<>();
        for (String body : rowBodies) {
            rewritten.add(rewriteRow(body, steps, title));
        }
        if (numbered) {
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < rewritten.size(); i++) {
                if (i > 0) {
                    out.append('\n');
                }
                out.append(i + 1).append(". ").append(rewritten.get(i));
            }
            return out.toString();
        }
        if (dash != null) {
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < rewritten.size(); i++) {
                if (i > 0) {
                    out.append('\n');
                }
                out.append(dash).append(' ').append(rewritten.get(i));
            }
            return out.toString();
        }
        return String.join("\n", rewritten);
    }

    private static String leadingDash(String expected) {
        Matcher marker = DASH_MARKER.matcher(expected);
        return marker.find() ? marker.group(1) : null;
    }

    private static String rewriteRow(String row, String steps, String title) {
        if (row == null || row.isBlank()) {
            return row == null ? "" : row;
        }
        if (hasQuotedPhrase(row)) {
            List<String> clauses = ExpectedClauseSplitter.clauses(row);
            if (clauses.size() <= 1) {
                return quoteClause(row, steps, title);
            }
            List<String> parts = new ArrayList<>();
            for (String clause : clauses) {
                parts.add(quoteClause(clause, steps, title));
            }
            return String.join(" and ", parts);
        }
        return quoteClause(row, steps, title);
    }

    public static String quoteClause(String clause, String steps, String title) {
        if (clause == null || clause.isBlank()) {
            return clause == null ? "" : clause;
        }
        if (hasQuotedPhrase(clause)) {
            List<String> sub = ExpectedClauseSplitter.clauses(clause);
            if (sub.size() > 1) {
                List<String> parts = new ArrayList<>();
                for (String piece : sub) {
                    parts.add(quoteClause(piece, steps, title));
                }
                return String.join(" and ", parts);
            }
            return filterQuotedFillers(clause, steps, title);
        }
        return quoteUnquotedPortion(clause, steps, title);
    }

    /**
     * A quoted span that still contains filler words is the model's invention.
     * Replace it with the filler-stripped display, or the Steps quote when that quote is clean.
     */
    private static String filterQuotedFillers(String clause, String steps, String title) {
        Matcher quoted = QUOTED.matcher(clause);
        StringBuilder out = new StringBuilder();
        int last = 0;
        while (quoted.find()) {
            out.append(clause, last, quoted.start());
            String inner = quoted.group(1) != null ? quoted.group(1) : quoted.group(2);
            out.append(replacementForQuotedSpan(quoted.group(0), inner, steps, title));
            last = quoted.end();
        }
        out.append(clause.substring(last));
        return out.toString();
    }

    private static String replacementForQuotedSpan(
            String original, String inner, String steps, String title) {
        if (inner == null || !spanHasFiller(inner)) {
            return original;
        }
        List<String> toks = ExpectedQuoteFilter.tokens(inner);
        String display = ExpectedQuoteFilter.remainderDisplay(inner);
        if (!ExpectedQuoteFilter.distinctive(toks, display)) {
            return original;
        }
        String reuse = findReusedQuote(toks, steps, title);
        if (reuse != null && !quotedSpanHasFiller(reuse)) {
            return reuse;
        }
        return "'" + display + "'";
    }

    private static boolean quotedSpanHasFiller(String quoted) {
        Matcher matcher = QUOTED.matcher(quoted);
        if (!matcher.find()) {
            return spanHasFiller(quoted);
        }
        String inner = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
        return inner != null && spanHasFiller(inner);
    }

    private static boolean spanHasFiller(String inner) {
        List<String> raw = new ArrayList<>();
        ExpectedQuoteFilter.scanWords(inner, raw::add);
        for (String word : raw) {
            if (ExpectedQuoteFilter.FILLERS.contains(word.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasQuotedPhrase(String text) {
        return text.indexOf('\'') >= 0 || text.indexOf('"') >= 0;
    }

    private static String quoteUnquotedPortion(String clause, String steps, String title) {
        List<String> toks = ExpectedQuoteFilter.tokens(clause);
        String display = ExpectedQuoteFilter.remainderDisplay(clause);
        if (!ExpectedQuoteFilter.distinctive(toks, display)) {
            return clause;
        }
        String reuse = findReusedQuote(toks, steps, title);
        if (reuse != null) {
            return replaceDistinctivePhrase(clause, display, reuse);
        }
        String wrapped = "'" + display + "'";
        return replaceDistinctivePhrase(clause, display, wrapped);
    }

    private static String findReusedQuote(List<String> leftoverTokens, String steps, String title) {
        Set<String> want = new HashSet<>(leftoverTokens);
        String corpus = (steps == null ? "" : steps) + "\n" + (title == null ? "" : title);
        Matcher m = QUOTED.matcher(corpus);
        while (m.find()) {
            String inner = m.group(1) != null ? m.group(1) : m.group(2);
            if (inner == null || inner.isBlank()) {
                continue;
            }
            Set<String> quoted = new HashSet<>(ExpectedQuoteFilter.tokens(inner));
            if (quoted.equals(want)) {
                return m.group(0);
            }
        }
        return null;
    }

    private static String replaceDistinctivePhrase(String clause, String display, String replacement) {
        if (display.isBlank()) {
            return clause;
        }
        String[] words = display.split("\\s+");
        StringBuilder pattern = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            if (i > 0) {
                pattern.append("[\\s\\u00a0]+");
            }
            pattern.append(Pattern.quote(words[i]));
        }
        Pattern p = Pattern.compile(pattern.toString(), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        Matcher m = p.matcher(clause);
        if (m.find()) {
            return m.replaceFirst(Matcher.quoteReplacement(replacement));
        }
        return clause;
    }

}
