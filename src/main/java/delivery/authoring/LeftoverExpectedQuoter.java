package delivery.authoring;

import delivery.excel.ManualTestCase;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Quotes distinctive leftover names in Expected Result after stripping filler words. */
public final class LeftoverExpectedQuoter {
    private static final Pattern QUOTED = Pattern.compile("'([^']*)'|\"([^\"]*)\"");
    private static final Pattern NUMBERED_LIST = Pattern.compile("(?m)(?:^|\\n)\\s*\\d+[.)]\\s+");

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
        return String.join("\n", rewritten);
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
            return clause;
        }
        return quoteUnquotedPortion(clause, steps, title);
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
