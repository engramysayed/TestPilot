package delivery.job;

import java.net.URI;
import java.util.Locale;

/**
 * Last-layer navigation may only reopen the Excel {@code Open … at /path}.
 * Arbitrary URLs are rejected.
 */
public final class ExcelPathNavigator {
    private ExcelPathNavigator() {
    }

    public static boolean isAllowed(String requested, String excelPath) {
        if (requested == null || requested.isBlank() || excelPath == null || excelPath.isBlank()) {
            return false;
        }
        return pathMatches(pathOnly(requested), pathOnly(excelPath));
    }

    public static String resolve(String currentUrl, String excelPath) {
        if (excelPath == null || excelPath.isBlank()) {
            return "";
        }
        String p = excelPath.trim();
        if (p.startsWith("http://") || p.startsWith("https://")) {
            return p;
        }
        if (!p.startsWith("/")) {
            p = "/" + p;
        }
        if (!p.endsWith("/")) {
            p = p + "/";
        }
        if (currentUrl == null || currentUrl.isBlank()) {
            return p;
        }
        try {
            URI cur = URI.create(currentUrl.trim());
            String origin = cur.getScheme() + "://" + cur.getAuthority();
            return origin + p;
        } catch (IllegalArgumentException e) {
            return p;
        }
    }

    static boolean pathMatches(String have, String want) {
        String h = trimSlashes(have).toLowerCase(Locale.ROOT);
        String w = trimSlashes(want).toLowerCase(Locale.ROOT);
        return !w.isEmpty() && (h.equals(w) || h.startsWith(w + "/"));
    }

    static String pathOnly(String urlOrPath) {
        String s = urlOrPath.trim();
        if (s.startsWith("http://") || s.startsWith("https://")) {
            try {
                String p = URI.create(s).getPath();
                return p == null || p.isBlank() ? "/" : p;
            } catch (IllegalArgumentException e) {
                return s;
            }
        }
        return s.startsWith("/") ? s : "/" + s;
    }

    private static String trimSlashes(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        String p = path;
        while (p.startsWith("/")) {
            p = p.substring(1);
        }
        while (p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        return p;
    }
}
