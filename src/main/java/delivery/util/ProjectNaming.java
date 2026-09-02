package delivery.util;

import java.net.URI;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Human-readable work/package folder names from website host + local timestamp.
 */
public final class ProjectNaming {
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
            .withZone(ZoneId.systemDefault());

    private ProjectNaming() {
    }

    public static String fromBaseUrl(String baseUrl, Instant now) {
        String host = hostSlug(baseUrl);
        String time = TS.format(now == null ? Instant.now() : now);
        return host + "-" + time;
    }

    public static String hostSlug(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "site";
        }
        try {
            String raw = baseUrl.trim();
            if (!raw.contains("://")) {
                raw = "https://" + raw;
            }
            URI uri = URI.create(raw);
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                host = raw;
            }
            host = host.toLowerCase(Locale.ROOT);
            if (host.startsWith("www.")) {
                host = host.substring(4);
            }
            String slug = host.replace('.', '-').replaceAll("[^a-z0-9\\-]", "-");
            slug = slug.replaceAll("-{2,}", "-");
            slug = slug.replaceAll("^-|-$", "");
            return slug.isBlank() ? "site" : slug;
        } catch (IllegalArgumentException e) {
            return "site";
        }
    }
}
