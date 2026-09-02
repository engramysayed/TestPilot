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

        if (html == null || html.isBlank()) {
            return "";
        }

        Document doc = Jsoup.parse(html);

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
            out = slimPreferringControls(doc, maxChars);
        }

        return out;
    }

    /**
     * Prefer keeping buttons/inputs/links (with stable attrs) over arbitrary head/tail slices.
     */
    private static String slimPreferringControls(Document doc, int maxChars) {
        StringBuilder controls = new StringBuilder(Math.min(maxChars, 32000));
        controls.append("<body>");
        if (doc.body() != null) {
            for (Element el : doc.body().select(
                    delivery.authoring.DomCandidateExtractor.INTERACTIVE_QUERY
                            + ", [data-test], [data-testid], [id]")) {
                String snip = el.outerHtml().replaceAll("\\s+", " ").trim();
                if (snip.isBlank()) {
                    continue;
                }
                if (controls.length() + snip.length() + 8 > maxChars) {
                    break;
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
