package delivery.privacy;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Central sanitation for DOM, URLs, logs, JS results, prompts and exported packs.
 * Password field values are always stripped; configured secrets are masked everywhere.
 */
public final class SecretSanitizer {
    public static final String MASK = "***";
    private static final Pattern TOKEN_QUERY = Pattern.compile(
            "(?i)([?&](?:token|password|passwd|secret|api[_-]?key|access[_-]?token)=)[^&]*");
    private static final Pattern PASSWORD_ASSIGN = Pattern.compile(
            "(?i)((?:password|passwd|secret|api[_-]?key)\\s*[=:]\\s*)[^\\s&\"']+");
    private static final Pattern TYPE_PASSWORD = Pattern.compile(
            "(?i)((?:type|enter|fill)\\s+password\\s+)[^\\s&\"']+");

    private SecretSanitizer() {
    }

    public static String scrubHtml(String html) {
        return scrubHtml(html, List.of());
    }

    public static void maskPasswordFields(Document doc) {
        if (doc == null) {
            return;
        }
        for (Element el : doc.select(
                "input[type=password], input[autocomplete=current-password], input[autocomplete=new-password]")) {
            if (el.hasAttr("value")) {
                el.attr("value", MASK);
            }
        }
    }

    public static java.util.List<String> extractPasswordValues(String html) {
        if (html == null || html.isBlank()) {
            return List.of();
        }
        Document doc = Jsoup.parse(html);
        java.util.ArrayList<String> values = new java.util.ArrayList<>();
        for (Element el : doc.select(
                "input[type=password], input[autocomplete=current-password], input[autocomplete=new-password]")) {
            String v = el.attr("value");
            if (v != null && v.length() >= 3) {
                values.add(v);
            }
        }
        return values;
    }

    public static String scrubPrompt(String text) {
        return scrubPrompt(text, List.of());
    }

    public static String scrubPrompt(String text, Collection<String> secrets) {
        String out = scrubText(text, secrets);
        return TYPE_PASSWORD.matcher(out).replaceAll("$1" + MASK);
    }

    public static String scrubHtml(String html, Collection<String> secrets) {
        if (html == null || html.isBlank()) {
            return html == null ? "" : html;
        }
        Document doc = Jsoup.parse(html);
        doc.outputSettings().prettyPrint(false);
        maskPasswordFields(doc);
        String out = doc.body() != null ? doc.body().html() : doc.outerHtml();
        return scrubText(out, secrets);
    }

    public static String scrubText(String text) {
        return scrubText(text, List.of());
    }

    public static String scrubText(String text, Collection<String> secrets) {
        if (text == null || text.isEmpty()) {
            return text == null ? "" : text;
        }
        String out = text;
        if (secrets != null) {
            for (String secret : secrets) {
                if (secret == null || secret.isBlank() || secret.length() < 3) {
                    continue;
                }
                out = out.replace(secret, MASK);
            }
        }
        out = TOKEN_QUERY.matcher(out).replaceAll("$1" + MASK);
        out = PASSWORD_ASSIGN.matcher(out).replaceAll("$1" + MASK);
        return out;
    }

    public static String scrubUrl(String url) {
        if (url == null || url.isBlank()) {
            return url == null ? "" : url;
        }
        String stripped = TOKEN_QUERY.matcher(url).replaceAll("$1" + MASK);
        try {
            URI uri = URI.create(stripped);
            if (uri.getUserInfo() != null && !uri.getUserInfo().isBlank()) {
                String auth = uri.getRawAuthority();
                String hostPort = auth == null ? "" : auth.substring(auth.indexOf('@') + 1);
                String scheme = uri.getScheme() == null ? "" : uri.getScheme() + "://";
                String path = uri.getRawPath() == null ? "" : uri.getRawPath();
                String q = uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery();
                return scheme + hostPort + path + q;
            }
        } catch (IllegalArgumentException ignored) {
            return stripped;
        }
        return stripped;
    }

    public static String scrubJsResult(String result, Collection<String> secrets) {
        return scrubText(result, secrets);
    }
}
