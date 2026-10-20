package delivery.authoring;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Click the first visible table/grid row that carries a shared cell (status badge, quoted name).
 * Twins are not AMBIGUOUS: first row in document order wins.
 */
public final class ListRowFirstBind {
    private static final Pattern WITH_STATUS = Pattern.compile(
            "(?i)\\bwith\\s+(.+?)\\s+status\\b");
    private static final String ROW_QUERY = "table tbody tr, table tr, [role=row], .ant-table-row";

    private ListRowFirstBind() {
    }

    public static Optional<DomCandidate> pick(
            StepIntentBinder.IntentLine intent,
            List<DomCandidate> candidates,
            String slimHtml) {
        if (intent == null
                || intent.kind() != StepIntentBinder.IntentKind.CLICK
                || slimHtml == null || slimHtml.isBlank()
                || candidates == null || candidates.isEmpty()) {
            return Optional.empty();
        }
        String needle = needle(intent.text());
        if (needle == null || needle.isBlank()) {
            return Optional.empty();
        }
        Document doc = Jsoup.parse(slimHtml);
        List<Element> rows = matchingRows(doc, needle);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        return pickInRow(rows.get(0), candidates, needle, doc);
    }

    static String needle(String stepText) {
        String quoted = StepIntentBinder.quotedVisibleName(stepText);
        if (quoted != null && !quoted.isBlank()) {
            return normalize(quoted);
        }
        if (stepText == null || stepText.isBlank()) {
            return null;
        }
        Matcher m = WITH_STATUS.matcher(stepText);
        if (m.find()) {
            String status = normalize(m.group(1));
            return status.isBlank() ? null : status;
        }
        return null;
    }

    private static List<Element> matchingRows(Document doc, String needle) {
        List<Element> out = new ArrayList<>();
        for (Element row : doc.select(ROW_QUERY)) {
            if (row.parents().stream().anyMatch(p -> "thead".equalsIgnoreCase(p.tagName()))) {
                continue;
            }
            if (hidden(row)) {
                continue;
            }
            if (containsNeedle(row.text(), needle)) {
                out.add(row);
            }
        }
        return out;
    }

    private static Optional<DomCandidate> pickInRow(
            Element row,
            List<DomCandidate> candidates,
            String needle,
            Document doc) {
        List<DomCandidate> inside = new ArrayList<>();
        for (DomCandidate c : candidates) {
            if (c == null || !DomCandidateExtractor.isBindableStrategy(c.strategy())) {
                continue;
            }
            List<Element> hits = HtmlLocatorPresence.matchingElements(c.strategy(), c.value(), doc);
            for (Element hit : hits) {
                if (hit.equals(row) || isUnder(hit, row)) {
                    inside.add(c);
                    break;
                }
            }
        }
        if (inside.isEmpty()) {
            return Optional.empty();
        }
        for (DomCandidate c : inside) {
            if (isActionControl(c) && !normalize(c.label()).equals(needle)) {
                return Optional.of(c);
            }
        }
        for (DomCandidate c : inside) {
            if (isActionControl(c)) {
                return Optional.of(c);
            }
        }
        return Optional.of(inside.get(0));
    }

    private static boolean isActionControl(DomCandidate c) {
        String tag = c.tag() == null ? "" : c.tag().toLowerCase(Locale.ROOT);
        return "a".equals(tag) || "button".equals(tag) || "menuitem".equals(tag)
                || tag.contains("button") || "link".equals(tag);
    }

    private static boolean isUnder(Element el, Element ancestor) {
        for (Element p = el; p != null; p = p.parent()) {
            if (p.equals(ancestor)) {
                return true;
            }
        }
        return false;
    }

    static boolean containsNeedle(String text, String needle) {
        return normalize(text).contains(normalize(needle));
    }

    private static String normalize(String s) {
        if (s == null) {
            return "";
        }
        return s.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private static boolean hidden(Element el) {
        if (el.hasAttr("hidden") || "true".equalsIgnoreCase(el.attr("aria-hidden"))) {
            return true;
        }
        String style = el.attr("style").toLowerCase(Locale.ROOT).replace(" ", "");
        if (style.contains("display:none") || style.contains("visibility:hidden")) {
            return true;
        }
        return el.parents().stream().anyMatch(p -> {
            if (p.hasAttr("hidden") || "true".equalsIgnoreCase(p.attr("aria-hidden"))) {
                return true;
            }
            String st = p.attr("style").toLowerCase(Locale.ROOT).replace(" ", "");
            return st.contains("display:none") || st.contains("visibility:hidden");
        });
    }
}
