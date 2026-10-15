package parsingLayer;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;

/**
 * Shrinks page HTML for binder/LLM context. When the body exceeds {@code maxChars},
 * interactive controls are preserved first so buttons / inputs / links are not lost
 * to head/tail truncation.
 */
public class HtmlSlimmer {

    public static String slim(String html, int maxChars) {
        return slim(html, maxChars, java.util.List.of());
    }

    /**
     * @param namedTexts words from the step. When the page is too long to keep whole, a label
     *                   that carries one of these words is kept even if it is not a button or link.
     */
    public static String slim(String html, int maxChars, java.util.List<String> namedTexts) {

        if (html == null || html.isBlank()) {
            return "";
        }

        Document doc = Jsoup.parse(html);
        doc.getAllElements().forEach(el -> el.removeAttr("data-keel-nav-item"));
        for (Element el : doc.select("nav li,aside li,[role=navigation] li,[class*=sidebar] li,[class*=side-menu] li"))
            el.attr("data-keel-nav-item", "true");
        java.util.Map<String, Integer> attributeCounts = new java.util.HashMap<>();
        for (Element el : doc.getAllElements()) {
            el.removeAttr("data-keel-ambiguous-attrs");
            for (var attr : el.attributes()) {
                attributeCounts.merge(attr.getKey() + "\u0000" + attr.getValue(), 1, Integer::sum);
            }
        }
        // Positions belong to the source DOM, never the filtered/reconstructed snapshot.
        java.util.Map<String, Integer> ordinals = new java.util.HashMap<>();
        java.util.Map<String, Long> textMatches = new java.util.HashMap<>();
        for (Element el : doc.getAllElements()) {
            el.removeAttr("data-keel-source-xpath");
            el.removeAttr("data-keel-source-text-ambiguous");
            el.removeAttr("data-keel-frame-path");
            el.removeAttr("data-keel-shadow-path");
            String tag = el.tagName();
            // Ordinals are per browsing context: an element inside an iframe is addressed by an
            // xpath evaluated in ITS frame document (after switchTo().frame), not in the merged page.
            Element context = el.parent() == null ? null : el.parent().closest("[data-testpilot-context]");
            String contextKey = context == null ? "" : "ctx" + System.identityHashCode(context) + "\u0000";
            int ordinal = ordinals.merge(contextKey + tag, 1, Integer::sum);
            if (el.is(delivery.authoring.DomCandidateExtractor.INTERACTIVE_QUERY)) {
                String label = el.text().trim().replaceAll("\\s+", " ");
                // Nav <li> candidates bind by exact normalize-space() text, so only an exact twin
                // (e.g. the hidden responsive copy) is ambiguous; "Hidden Support" or a parent li
                // that merely contains "Support" is not. Other tags bind with contains().
                boolean exact = "li".equals(tag);
                if (!label.isBlank() && textMatches.computeIfAbsent(tag + "\u0000" + exact + "\u0000" + label,
                        ignored -> doc.getElementsByTag(tag).stream()
                                .map(n -> n.text().trim().replaceAll("\\s+", " "))
                                .filter(t -> exact ? t.equals(label) : t.contains(label)).count()) > 1) {
                    el.attr("data-keel-source-text-ambiguous", "true");
                }
                el.attr("data-keel-source-xpath", "(//" + tag + ")[" + ordinal + "]");
                String role = el.attr("role");
                if (java.util.Set.of("combobox", "textbox", "listbox", "checkbox", "radio", "switch").contains(role)) {
                    int roleOrdinal = ordinals.merge(contextKey + tag + "@" + role, 1, Integer::sum);
                    el.attr("data-keel-source-xpath", "(//" + tag + "[@role='" + role + "'])[" + roleOrdinal + "]");
                }
                if (context != null) {
                    String framePath = context.attr("data-testpilot-frame-path");
                    if (!framePath.isBlank()) {
                        el.attr("data-keel-frame-path", framePath);
                    }
                    if ("shadow".equals(context.attr("data-testpilot-context"))) {
                        // XPath cannot be evaluated inside a shadow root; CSS hops are the only form.
                        el.attr("data-keel-source-xpath", "unsupported-context-ordinal");
                        String shadowPath = context.attr("data-testpilot-shadow-path");
                        if (!shadowPath.isBlank()) {
                            el.attr("data-keel-shadow-path", shadowPath);
                        }
                    } else if (framePath.isBlank()) {
                        // Embedded context without a frame path (older snapshot): no valid ordinal anywhere.
                        el.attr("data-keel-source-xpath", "unsupported-context-ordinal");
                    }
                }
            }
            java.util.List<String> ambiguous = new java.util.ArrayList<>();
            for (var attr : el.attributes()) {
                if (attributeCounts.getOrDefault(attr.getKey() + "\u0000" + attr.getValue(), 0) > 1)
                    ambiguous.add(attr.getKey());
            }
            if (!ambiguous.isEmpty()) el.attr("data-keel-ambiguous-attrs", String.join(" ", ambiguous));
        }
        delivery.privacy.SecretSanitizer.maskPasswordFields(doc);

        removeComments(doc);

        doc.select("script, style, noscript, svg, canvas").remove();

        doc.select("meta, link").remove();

        // Drop what the user cannot see before the style attributes that prove it are stripped.
        // Responsive layouts ship a second hidden copy of the whole nav; binding to it costs a
        // full element-wait and then a heal round.
        doc.select("template, [hidden], [aria-hidden=true], input[type=hidden],"
                + " [style*='display:none'], [style*='display: none'],"
                + " [style*='visibility:hidden'], [style*='visibility: hidden']").remove();

        doc.getAllElements().forEach(el -> el.removeAttr("style"));

        doc.outputSettings().prettyPrint(false);

        String out = (doc.body() != null)
                ? "<body>" + doc.body().html() + "</body>"
                : doc.outerHtml();

        out = out.replaceAll("\\s+", " ").trim();

        if (maxChars > 0 && out.length() > maxChars) {
            out = slimPreferringControls(doc, maxChars, namedTexts);
        }

        return delivery.privacy.SecretSanitizer.scrubText(out);
    }

    /**
     * Prefer keeping buttons/inputs/links (with stable attrs) over arbitrary head/tail slices.
     */
    private static String slimPreferringControls(Document doc, int maxChars, java.util.List<String> namedTexts) {
        StringBuilder controls = new StringBuilder(Math.min(maxChars, 32000));
        controls.append("<body>");
        if (doc.body() != null) {
            for (Element el : doc.body().getAllElements()) {
                if (!innermostNamed(el, namedTexts)) {
                    continue;
                }
                appendSnippet(controls, el, maxChars);
            }
            java.util.List<Element> nodes = new java.util.ArrayList<>(doc.body().select(
                    delivery.authoring.DomCandidateExtractor.INTERACTIVE_QUERY
                            + ", [data-test], [data-testid], [id]"));
            nodes.sort(java.util.Comparator.comparingInt(el -> {
                if (el.closest("nav,aside,[role=navigation],[class*=sidebar],[class*=side-menu]") == null) return 2;
                return "true".equals(el.attr("data-keel-source-text-ambiguous")) ? 1 : 0;
            }));
            for (Element el : nodes) {
                // A node is represented once; parent fragments must not duplicate descendants.
                if (!el.select(delivery.authoring.DomCandidateExtractor.INTERACTIVE_QUERY).isEmpty()
                        && !el.is(delivery.authoring.DomCandidateExtractor.INTERACTIVE_QUERY)) continue;
                Element copy = el.clone();
                copy.select(delivery.authoring.DomCandidateExtractor.INTERACTIVE_QUERY).stream()
                        .filter(child -> child != copy).toList().forEach(child ->
                                child.replaceWith(new org.jsoup.nodes.TextNode(child.text())));
                String snip = copy.outerHtml().replaceAll("\\s+", " ").trim();
                if (snip.isBlank()) {
                    continue;
                }
                if (controls.length() + snip.length() + 8 > maxChars) {
                    continue;
                }
                controls.append(snip);
            }
        }
        controls.append("</body>");
        String preferred = controls.toString();
        if (preferred.length() > 64) {
            return preferred;
        }
        // Fallback: classic head/tail if almost no controls found
        String full = (doc.body() != null)
                ? "<body>" + doc.body().html().replaceAll("\\s+", " ").trim() + "</body>"
                : doc.outerHtml().replaceAll("\\s+", " ").trim();
        int half = Math.max(0, (maxChars / 2) - 40);
        String head = full.substring(0, Math.min(half, full.length()));
        String tail = full.substring(Math.max(0, full.length() - half));
        return head + " ...[HTML_TRUNCATED_HEAD_TAIL]... " + tail;
    }

    private static void appendSnippet(StringBuilder controls, Element el, int maxChars) {
        String snip = el.outerHtml().replaceAll("\\s+", " ").trim();
        if (snip.isBlank() || controls.length() + snip.length() + 8 > maxChars) {
            return;
        }
        controls.append(snip);
    }

    /** True when this element carries a step word and no descendant does. */
    private static boolean innermostNamed(Element el, java.util.List<String> names) {
        if (!textHits(el, names)) {
            return false;
        }
        for (Element child : el.getAllElements()) {
            if (child != el && textHits(child, names)) {
                return false;
            }
        }
        return true;
    }

    private static boolean textHits(Element el, java.util.List<String> names) {
        if (el == null || names == null || names.isEmpty()) {
            return false;
        }
        String tag = el.tagName().toLowerCase(java.util.Locale.ROOT);
        if ("html".equals(tag) || "body".equals(tag) || "script".equals(tag) || "style".equals(tag)) {
            return false;
        }
        String text = el.text() == null ? "" : el.text().trim().replaceAll("\\s+", " ");
        if (text.length() < 2 || text.length() > 80) {
            return false;
        }
        String lower = text.toLowerCase(java.util.Locale.ROOT);
        for (String name : names) {
            if (name == null) {
                continue;
            }
            String n = name.trim().toLowerCase(java.util.Locale.ROOT);
            if (n.length() < 4) {
                continue;
            }
            if (lower.equals(n) || lower.matches(".*\\b" + java.util.regex.Pattern.quote(n) + "\\b.*")) {
                return true;
            }
        }
        return false;
    }

    private static void removeComments(Document doc) {
        for (Node node : doc.getAllElements()) {
            for (Node child : node.childNodesCopy()) {
                if ("#comment".equals(child.nodeName())) {
                    child.remove();
                }
            }
        }
    }
}
