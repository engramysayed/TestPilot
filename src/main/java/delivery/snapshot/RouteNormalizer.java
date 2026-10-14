package delivery.snapshot;

import java.net.URI;
import java.util.Locale;

/** host + path pattern; ids (numbers, uuids, hex, mixed tokens) become ":id"; query dropped; "#/..." SPA routes kept. */
public final class RouteNormalizer {
    private RouteNormalizer() {
    }

    public static String normalize(String url) {
        if (url == null || url.isBlank()) return "";
        URI u;
        try {
            u = URI.create(url.trim());
        } catch (IllegalArgumentException e) {
            return "";
        }
        String host = u.getHost() == null ? "" : u.getHost().toLowerCase(Locale.ROOT);
        int port = u.getPort();
        String path = pattern(u.getPath());
        String frag = u.getFragment();
        if (frag != null && frag.startsWith("/")) {
            int q = frag.indexOf('?');
            path = path + "#" + pattern(q < 0 ? frag : frag.substring(0, q));
        }
        return host + (port > 0 ? ":" + port : "") + path;
    }

    static String pattern(String path) {
        if (path == null || path.isBlank()) return "/";
        StringBuilder sb = new StringBuilder();
        for (String seg : path.split("/")) {
            if (seg.isEmpty()) continue;
            sb.append('/').append(isId(seg) ? ":id" : seg.toLowerCase(Locale.ROOT));
        }
        return sb.length() == 0 ? "/" : sb.toString();
    }

    static boolean isId(String seg) {
        String s = seg.toLowerCase(Locale.ROOT);
        if (s.matches("\\d+")) return true;
        if (s.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) return true;
        if (s.matches("[0-9a-f]{12,}")) return true;
        if (s.matches("(?=.*\\d)(?=.*[a-z])[a-z0-9_-]{8,}")) return true; // e.g. prj_a81ea6ad1132
        return false;
    }
}
