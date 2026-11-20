package delivery.codegen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * W3: natural-language / intent step → validated {@code By} cached as a field on the page class.
 * Resolves a short NL phrase against a proven locator (already validated by prove) and emits a
 * stable field name such as {@code nl_click_login_button}.
 */
public final class NlLocatorCache {
    private static final Pattern SAFE = Pattern.compile("[^a-zA-Z0-9]+");
    private static final int MAX_TOKEN = 40;

    private NlLocatorCache() {
    }

    public record CachedBy(
            String fieldName,
            String nlPhrase,
            String strategy,
            String value,
            String pageName
    ) {
        public CachedBy {
            fieldName = fieldName == null ? "" : fieldName;
            nlPhrase = nlPhrase == null ? "" : nlPhrase.trim();
            strategy = strategy == null ? "" : strategy.trim();
            value = value == null ? "" : value;
            pageName = pageName == null ? "" : pageName.trim();
        }
    }

    public static java.util.Optional<CachedBy> resolve(
            String pageName,
            String nlPhrase,
            String strategy,
            String value
    ) {
        if (nlPhrase == null || nlPhrase.isBlank()) {
            return java.util.Optional.empty();
        }
        if (value == null || value.isBlank()) {
            return java.util.Optional.empty();
        }
        String rawStrategy = strategy == null ? "" : strategy.trim();
        String st;
        if ("xpath".equalsIgnoreCase(rawStrategy)) {
            st = "xpath";
        } else if ("id".equalsIgnoreCase(rawStrategy)) {
            st = "id";
        } else if ("name".equalsIgnoreCase(rawStrategy)) {
            st = "name";
        } else if ("css".equalsIgnoreCase(rawStrategy) || "cssSelector".equalsIgnoreCase(rawStrategy)) {
            st = "cssSelector";
        } else {
            st = PageAccumulator.normalizeByStrategyForCandidates(
                    rawStrategy.isBlank() ? "css" : rawStrategy);
            if (st == null || st.isBlank()) {
                st = "cssSelector";
            }
        }
        String field = fieldNameFor(nlPhrase);
        return java.util.Optional.of(new CachedBy(field, nlPhrase.trim(), st, value, pageName));
    }

    public static String fieldNameFor(String nlPhrase) {
        String raw = nlPhrase == null ? "" : nlPhrase.trim().toLowerCase(Locale.ROOT);
        String[] parts = SAFE.split(raw);
        StringBuilder sb = new StringBuilder("nl");
        int used = 0;
        for (String p : parts) {
            if (p == null || p.isBlank()) {
                continue;
            }
            if (p.length() > MAX_TOKEN) {
                p = p.substring(0, MAX_TOKEN);
            }
            sb.append('_').append(p);
            used++;
            if (used >= 6) {
                break;
            }
        }
        if (used == 0) {
            sb.append("_step");
        }
        String name = sb.toString();
        if (!Character.isJavaIdentifierStart(name.charAt(0))) {
            name = "nl_" + name;
        }
        return name;
    }

    public static void apply(PageAccumulator pages, List<CachedBy> cached) {
        if (pages == null || cached == null || cached.isEmpty()) {
            return;
        }
        Map<String, List<CachedBy>> byPage = new LinkedHashMap<>();
        for (CachedBy c : cached) {
            if (c == null || c.value().isBlank()) {
                continue;
            }
            String key = c.pageName() == null || c.pageName().isBlank() ? "Page" : c.pageName();
            byPage.computeIfAbsent(key, k -> new ArrayList<>()).add(c);
        }
        for (Map.Entry<String, List<CachedBy>> e : byPage.entrySet()) {
            for (CachedBy c : e.getValue()) {
                pages.addNlCachedField(e.getKey(), c);
            }
        }
    }
}
