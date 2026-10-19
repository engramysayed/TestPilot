package delivery.authoring;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Rewrites leftover Expected assert clauses from a unique live page heading. */
public final class LiveHeadingQuote {
    private static final String HEADING_SELECTOR =
            "h1, h2, h3, [role=heading], .ant-card-head-title, [class*=card-head-title], [class*=card-title]";

    private LiveHeadingQuote() {
    }

    public static String maybeRewrite(String clause, String liveHtml) {
        if (clause == null || clause.isBlank() || liveHtml == null || liveHtml.isBlank()) {
            return clause;
        }
        List<String> tokens = clauseTokens(clause);
        if (tokens.isEmpty()) {
            return clause;
        }
        Document doc = Jsoup.parse(liveHtml);
        List<String> matches = new ArrayList<>();
        for (Element el : doc.select(HEADING_SELECTOR)) {
            if (isHidden(el)) {
                continue;
            }
            String text = el.text();
            if (text == null || text.isBlank()) {
                continue;
            }
            if (containsAllTokens(text, tokens)) {
                matches.add(collapseWhitespace(text.trim()));
            }
        }
        if (matches.size() != 1) {
            return clause;
        }
        return quoteTitle(matches.get(0));
    }

    private static List<String> clauseTokens(String clause) {
        return ExpectedQuoteFilter.tokens(stripQuoteChars(clause));
    }

    /** Unwrap quoted spans so leftover quoting still yields name tokens. */
    private static String stripQuoteChars(String clause) {
        StringBuilder out = new StringBuilder(clause.length());
        for (int i = 0; i < clause.length(); i++) {
            char c = clause.charAt(i);
            if (c != '\'' && c != '"') {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static boolean containsAllTokens(String titleText, List<String> tokens) {
        String lower = titleText.toLowerCase(Locale.ROOT);
        for (String token : tokens) {
            if (!lower.contains(token)) {
                return false;
            }
        }
        return true;
    }

    private static String collapseWhitespace(String text) {
        return text.trim().replaceAll("\\s+", " ");
    }

    private static String quoteTitle(String title) {
        if (title.indexOf('\'') >= 0) {
            return "\"" + title + "\"";
        }
        return "'" + title + "'";
    }

    private static boolean isHidden(Element el) {
        if (el == null) {
            return true;
        }
        if (hiddenMarker(el)) {
            return true;
        }
        for (Element ancestor : el.parents()) {
            if (hiddenMarker(ancestor)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hiddenMarker(Element el) {
        if (el.hasAttr("hidden") || "true".equalsIgnoreCase(el.attr("aria-hidden"))) {
            return true;
        }
        String style = el.attr("style").toLowerCase(Locale.ROOT).replace(" ", "");
        return style.contains("display:none");
    }
}
