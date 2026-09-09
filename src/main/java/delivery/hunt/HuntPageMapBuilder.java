package delivery.hunt;

import delivery.authoring.DomCandidate;
import delivery.authoring.DomCandidateExtractor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class HuntPageMapBuilder {
    public static final int CONTROL_CAP = 100;
    public static final int THIN_CONTROL_THRESHOLD = 8;

    private static final int HEADING_MAX = 8;
    private static final int ALERT_MAX = 10;
    private static final int DIALOG_MAX = 5;
    private static final int SNIPPET_MAX = 200;

    private HuntPageMapBuilder() {
    }

    public static HuntPageMap build(String url, String title, String slimHtml) {
        List<DomCandidate> controls = DomCandidateExtractor.extract(slimHtml, CONTROL_CAP);
        List<String> headings = extractHeadings(slimHtml);
        List<String> alerts = extractAlerts(slimHtml);
        List<String> dialogs = extractDialogs(slimHtml);
        return new HuntPageMap(url, title, headings, alerts, controls, dialogs);
    }

    private static List<String> extractHeadings(String html) {
        if (html == null || html.isBlank()) {
            return List.of();
        }
        Document doc = Jsoup.parse(html);
        List<String> out = new ArrayList<>();
        for (Element el : doc.select("h1, h2, h3")) {
            String text = el.text().trim();
            if (!text.isEmpty()) {
                out.add(text);
                if (out.size() >= HEADING_MAX) {
                    break;
                }
            }
        }
        return List.copyOf(out);
    }

    private static List<String> extractAlerts(String html) {
        if (html == null || html.isBlank()) {
            return List.of();
        }
        Document doc = Jsoup.parse(html);
        Set<String> seen = new LinkedHashSet<>();
        List<String> out = new ArrayList<>();

        for (Element el : doc.select("[role=alert]")) {
            addSnippet(out, seen, snippet(el));
            if (out.size() >= ALERT_MAX) {
                return List.copyOf(out);
            }
        }
        for (Element el : doc.select("[aria-invalid=true]")) {
            addSnippet(out, seen, invalidSnippet(el));
            if (out.size() >= ALERT_MAX) {
                break;
            }
        }
        return List.copyOf(out);
    }

    private static List<String> extractDialogs(String html) {
        if (html == null || html.isBlank()) {
            return List.of();
        }
        Document doc = Jsoup.parse(html);
        Set<String> seen = new LinkedHashSet<>();
        List<String> out = new ArrayList<>();

        for (Element el : doc.select("[role=dialog]")) {
            String label = el.attr("aria-label").trim();
            if (label.isEmpty()) {
                Element titled = el.selectFirst("[title]");
                label = titled != null ? titled.attr("title").trim() : "";
            }
            if (label.isEmpty()) {
                label = el.text().trim();
            }
            addSnippet(out, seen, truncate(label));
            if (out.size() >= DIALOG_MAX) {
                break;
            }
        }
        return List.copyOf(out);
    }

    private static String snippet(Element el) {
        String text = el.text().trim();
        if (!text.isEmpty()) {
            return truncate(text);
        }
        String aria = el.attr("aria-label").trim();
        if (!aria.isEmpty()) {
            return truncate(aria);
        }
        return "";
    }

    private static String invalidSnippet(Element el) {
        String text = el.text().trim();
        if (!text.isEmpty()) {
            return truncate(text);
        }
        String name = el.attr("name").trim();
        if (!name.isEmpty()) {
            return truncate("invalid: " + name);
        }
        String id = el.attr("id").trim();
        if (!id.isEmpty()) {
            return truncate("invalid: " + id);
        }
        return truncate("invalid field");
    }

    private static void addSnippet(List<String> out, Set<String> seen, String snippet) {
        if (snippet == null || snippet.isBlank() || !seen.add(snippet)) {
            return;
        }
        out.add(snippet);
    }

    private static String truncate(String s) {
        if (s == null) {
            return "";
        }
        String trimmed = s.trim();
        if (trimmed.length() <= SNIPPET_MAX) {
            return trimmed;
        }
        return trimmed.substring(0, SNIPPET_MAX).trim();
    }
}
