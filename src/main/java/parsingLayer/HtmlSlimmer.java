package utils;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

public class HtmlSlimmer {

    public static String slim(String html, int maxChars) {

        if (html == null || html.isEmpty()) {
            return "";
        }

        String out = html;

        //Remove comments
        out = out.replaceAll("<!--.*?-->", "");

        //Remove script, style, noscript, svg, canvas blocks
        out = out.replaceAll("(?is)<script.*?>.*?</script>", "");
        out = out.replaceAll("(?is)<style.*?>.*?</style>", "");
        out = out.replaceAll("(?is)<noscript.*?>.*?</noscript>", "");
        out = out.replaceAll("(?is)<svg.*?>.*?</svg>", "");
        out = out.replaceAll("(?is)<canvas.*?>.*?</canvas>", "");

        //Remove link tags
        out = out.replaceAll("(?i)<meta[^>]*>", "");
        out = out.replaceAll("(?i)<link[^>]*>", "");

        //Remove inline styles
        out = out.replaceAll("(?i)\\sstyle=\"[^\"]*\"", "");

        //Collapse whitespace
        out = out.replaceAll("\\s+", " ").trim();

        //Hard size cap (VERY IMPORTANT)
        if (out.length() > maxChars) {
            out = out.substring(0, maxChars) + " ...[HTML_TRUNCATED]";
        }

        return out;
    }


}
