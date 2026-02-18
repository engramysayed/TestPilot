package parsingLayer;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Node;

public class HtmlSlimmer {

    public static String slim(String html, int maxChars) {

        if (html == null || html.isBlank()) {
            return "";
        }

        Document doc = Jsoup.parse(html);

        // 1) Remove comments safely (no list removals)
        removeComments(doc);

        // 2) Remove heavy tags BUT KEEP iframe
        doc.select("script, style, noscript, svg, canvas").remove();

        // 3) Remove meta & link
        doc.select("meta, link").remove();

        // 4) Remove inline style attributes
        doc.getAllElements().forEach(el -> el.removeAttr("style"));

        // 5) No pretty print; reduce whitespace
        doc.outputSettings().prettyPrint(false);

        String out = (doc.body() != null)
                ? "<body>" + doc.body().html() + "</body>"
                : doc.outerHtml();

        out = out.replaceAll("\\s+", " ").trim();

        // 6) Hard size cap using HEAD + TAIL (preserves late DOM like iframes)
        if (out.length() > maxChars) {
            int half = Math.max(0, (maxChars / 2) - 40);
            String head = out.substring(0, Math.min(half, out.length()));
            String tail = out.substring(Math.max(0, out.length() - half));

            out = head + " ...[HTML_TRUNCATED_HEAD_TAIL]... " + tail;
        }

        return out;
    }

    private static void removeComments(Document doc) {
        // Traverse all nodes and remove comment nodes safely
        for (Node node : doc.getAllElements()) {
            // iterate a snapshot of childNodes to avoid concurrent modification issues
            for (Node child : node.childNodesCopy()) {
                if ("#comment".equals(child.nodeName())) {
                    child.remove();
                }
            }
        }
    }
}
