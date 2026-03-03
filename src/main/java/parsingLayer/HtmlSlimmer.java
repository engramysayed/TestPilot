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

        removeComments(doc);

        doc.select("script, style, noscript, svg, canvas").remove();

        doc.select("meta, link").remove();

        doc.getAllElements().forEach(el -> el.removeAttr("style"));

        doc.outputSettings().prettyPrint(false);

        String out = (doc.body() != null)
                ? "<body>" + doc.body().html() + "</body>"
                : doc.outerHtml();

        out = out.replaceAll("\\s+", " ").trim();

        if (out.length() > maxChars) {
            int half = Math.max(0, (maxChars / 2) - 40);
            String head = out.substring(0, Math.min(half, out.length()));
            String tail = out.substring(Math.max(0, out.length() - half));

            out = head + " ...[HTML_TRUNCATED_HEAD_TAIL]... " + tail;
        }

        return out;
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
