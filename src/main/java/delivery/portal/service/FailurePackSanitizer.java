package delivery.portal.service;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Pattern;

/** Redact secrets from failure-pack manifest fields (A1). */
public final class FailurePackSanitizer {
    private static final Pattern SENSITIVE = Pattern.compile(
            "(?i)(password|passwd|token|secret|api[_-]?key|authorization|cookie)\\s*[:=]",
            Pattern.CASE_INSENSITIVE);

    private FailurePackSanitizer() {
    }

    public static String pageUrl(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        try {
            URI u = URI.create(raw.trim());
            String host = u.getHost() == null ? "" : u.getHost();
            String path = u.getPath() == null ? "" : u.getPath();
            return u.getScheme() + "://" + host + path;
        } catch (Exception e) {
            return "[redacted-url]";
        }
    }

    public static String text(String raw) {
        if (raw == null) {
            return "";
        }
        String t = raw.trim();
        if (t.length() > 4000) {
            t = t.substring(0, 4000) + "…";
        }
        if (SENSITIVE.matcher(t).find()) {
            return "[redacted]";
        }
        return t;
    }
}
