package delivery.snapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Context-qualified locator, Keel contract v2 (superset of v1). Spec: patches/contract-v2/SPEC.md.
 * <pre>
 *   target   := context* locator modifier?
 *   context  := ("frame=" CSS | "shadow=" CSS | "shadow-closed=" CSS) " >> "
 *   locator  := "css=" CSS | "text=" JSON-STRING                        ; text= is v2
 *   modifier := " >> provider=" NAME ":" ID [" >> point=" PT]           ; v2, only after css=
 *             | " >> point=" PT                                          ; v2, after css= or text=
 *   PT       := NUM "," NUM "@" NUM "x" NUM                              ; CSS px from element top-left @ recorded size
 * </pre>
 * Hops are separated by " >> "; a literal ">>" inside a CSS/ID value is written "\>>". text= values
 * are JSON string literals (double quotes, \" \\ \n \r \t \\uXXXX escapes), so they may contain " >> ".
 */
public record KeelTarget(List<Hop> hops) {

    public static final int CONTRACT_VERSION = 2;

    public enum Kind { FRAME, SHADOW, SHADOW_CLOSED, CSS, TEXT, PROVIDER, POINT }

    /** {@code css} holds the hop value: a CSS selector, the decoded text literal, NAME:ID, or the point spec. */
    public record Hop(Kind kind, String css) {
        public String value() { return css; }

        public String prefix() {
            return switch (kind) {
                case FRAME -> "frame=";
                case SHADOW -> "shadow=";
                case SHADOW_CLOSED -> "shadow-closed=";
                case CSS -> "css=";
                case TEXT -> "text=";
                case PROVIDER -> "provider=";
                case POINT -> "point=";
            };
        }

        boolean isContext() { return kind == Kind.FRAME || kind == Kind.SHADOW || kind == Kind.SHADOW_CLOSED; }
        boolean isLocator() { return kind == Kind.CSS || kind == Kind.TEXT; }
    }

    /** Parsed point modifier: offset (x,y) in CSS px from the element's top-left at recorded size w x h. */
    public record Point(double x, double y, double w, double h) {
        /** Offset from the element CENTRE (Selenium Actions origin) for the element's current size. */
        public double[] offsetFromCentre(double currentW, double currentH) {
            double sx = currentW / w, sy = currentH / h;
            return new double[]{x * sx - currentW / 2.0, y * sy - currentH / 2.0};
        }

        public static Point parse(String spec) {
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("(\\d+(?:\\.\\d+)?),(\\d+(?:\\.\\d+)?)@(\\d+(?:\\.\\d+)?)x(\\d+(?:\\.\\d+)?)").matcher(spec);
            if (!m.matches()) throw new IllegalArgumentException("bad point spec: " + spec);
            Point p = new Point(Double.parseDouble(m.group(1)), Double.parseDouble(m.group(2)),
                    Double.parseDouble(m.group(3)), Double.parseDouble(m.group(4)));
            if (p.w <= 0 || p.h <= 0) throw new IllegalArgumentException("point size must be > 0: " + spec);
            if (p.x > p.w || p.y > p.h) throw new IllegalArgumentException("point outside recorded size: " + spec);
            return p;
        }

        public String format() {
            return num(x) + "," + num(y) + "@" + num(w) + "x" + num(h);
        }

        private static String num(double d) {
            if (d == Math.rint(d)) return String.valueOf((long) d);
            String s = String.format(Locale.ROOT, "%.2f", d);
            return s.replaceAll("0+$", "").replaceAll("\\.$", "");
        }
    }

    public static final String SEPARATOR = " >> ";

    public KeelTarget {
        hops = List.copyOf(hops);
        validate(hops);
    }

    private static void validate(List<Hop> hops) {
        int i = 0;
        while (i < hops.size() && hops.get(i).isContext()) i++;
        if (i >= hops.size() || !hops.get(i).isLocator())
            throw new IllegalArgumentException("KeelTarget needs exactly one css= or text= locator after context hops");
        Kind loc = hops.get(i).kind();
        i++;
        if (i < hops.size() && hops.get(i).kind() == Kind.PROVIDER) {
            if (loc != Kind.CSS) throw new IllegalArgumentException("provider= must follow css=");
            if (!hops.get(i).css().matches("[a-z0-9_-]+:\\S+")) throw new IllegalArgumentException("provider= needs NAME:ID");
            i++;
        }
        if (i < hops.size() && hops.get(i).kind() == Kind.POINT) {
            Point.parse(hops.get(i).css());
            i++;
        }
        if (i != hops.size())
            throw new IllegalArgumentException("unexpected hop after locator: " + hops.get(i).prefix());
        for (Hop h : hops) if (h.css() == null || h.css().isEmpty()) throw new IllegalArgumentException("blank hop value");
    }

    /** Lowest contract version able to express this target (1 for css-only, 2 when text/provider/point used). */
    public int requiredVersion() {
        return hops.stream().anyMatch(h -> h.kind() == Kind.TEXT || h.kind() == Kind.PROVIDER || h.kind() == Kind.POINT) ? 2 : 1;
    }

    public boolean isPlainCss() {
        return hops.size() == 1 && hops.get(0).kind() == Kind.CSS;
    }

    public List<Hop> frameHops() {
        return hops.stream().filter(h -> h.kind() == Kind.FRAME).toList();
    }

    public int locatorIndex() {
        for (int i = 0; i < hops.size(); i++) if (hops.get(i).isLocator()) return i;
        throw new IllegalStateException();
    }

    public Hop locator() {
        return hops.get(locatorIndex());
    }

    /** Terminal CSS (v1 API). Throws for text= targets. */
    public String terminalCss() {
        Hop l = locator();
        if (l.kind() != Kind.CSS) throw new IllegalStateException("target locator is " + l.prefix() + ", not css=");
        return l.css();
    }

    public java.util.Optional<Point> point() {
        return hops.stream().filter(h -> h.kind() == Kind.POINT).findFirst().map(h -> Point.parse(h.css()));
    }

    public java.util.Optional<String> provider() {
        return hops.stream().filter(h -> h.kind() == Kind.PROVIDER).findFirst().map(Hop::css);
    }

    /** Same context hops, new locator (modifiers dropped). */
    public KeelTarget withLocator(Hop locator) {
        List<Hop> out = new ArrayList<>(hops.subList(0, locatorIndex()));
        out.add(locator);
        return new KeelTarget(out);
    }

    public String format() {
        StringBuilder sb = new StringBuilder();
        for (Hop h : hops) {
            if (sb.length() > 0) sb.append(SEPARATOR);
            sb.append(h.prefix());
            sb.append(h.kind() == Kind.TEXT ? quote(h.css()) : h.css().replace(">>", "\\>>"));
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return format();
    }

    public static KeelTarget parse(String s) {
        if (s == null || s.isBlank()) throw new IllegalArgumentException("empty KeelTarget");
        List<Hop> hops = new ArrayList<>();
        int i = 0, n = s.length();
        while (i < n) {
            Kind kind = null; int start = i;
            for (Kind k : new Kind[]{Kind.SHADOW_CLOSED, Kind.FRAME, Kind.SHADOW, Kind.CSS, Kind.TEXT, Kind.PROVIDER, Kind.POINT}) {
                String p = new Hop(k, "x").prefix();
                if (s.startsWith(p, i)) { kind = k; i += p.length(); break; }
            }
            if (kind == null) {
                int end = s.indexOf(SEPARATOR, i);
                throw new IllegalArgumentException("unknown hop: " + s.substring(start, end < 0 ? n : end));
            }
            String value;
            if (kind == Kind.TEXT) {
                int[] endPos = new int[1];
                value = unquote(s, i, endPos);
                i = endPos[0];
            } else {
                int j = i;
                while (j < n && !(s.startsWith(SEPARATOR, j) && (j == 0 || s.charAt(j - 1) != '\\'))) j++;
                value = s.substring(i, j).trim().replace("\\>>", ">>");
                i = j;
            }
            if (value.isEmpty()) throw new IllegalArgumentException("blank selector in hop: " + new Hop(kind, "x").prefix());
            hops.add(new Hop(kind, value));
            if (i < n) {
                if (!s.startsWith(SEPARATOR, i)) throw new IllegalArgumentException("expected ' >> ' at " + i);
                i += SEPARATOR.length();
                if (i >= n) throw new IllegalArgumentException("trailing separator");
            }
        }
        return new KeelTarget(hops);
    }

    /** JSON string literal for text= values. */
    static String quote(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char ch : s.toCharArray()) {
            switch (ch) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> { if (ch < 0x20) sb.append(String.format(Locale.ROOT, "\\u%04x", (int) ch)); else sb.append(ch); }
            }
        }
        return sb.append('"').toString();
    }

    static String unquote(String s, int i, int[] endOut) {
        if (i >= s.length() || s.charAt(i) != '"') throw new IllegalArgumentException("text= value must be a JSON string literal");
        StringBuilder sb = new StringBuilder();
        int j = i + 1;
        while (j < s.length()) {
            char ch = s.charAt(j);
            if (ch == '"') { endOut[0] = j + 1; return sb.toString(); }
            if (ch == '\\') {
                if (j + 1 >= s.length()) break;
                char e = s.charAt(++j);
                switch (e) {
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'u' -> {
                        if (j + 4 >= s.length()) throw new IllegalArgumentException("bad \\u escape");
                        sb.append((char) Integer.parseInt(s.substring(j + 1, j + 5), 16));
                        j += 4;
                    }
                    default -> throw new IllegalArgumentException("bad escape \\" + e);
                }
            } else if (ch < 0x20) {
                throw new IllegalArgumentException("raw control character in text= literal");
            } else {
                sb.append(ch);
            }
            j++;
        }
        throw new IllegalArgumentException("unterminated text= literal");
    }

    /** Builds a target from serializer output (frame hops each carry the shadow chain that hosts the iframe). */
    static KeelTarget of(List<SnapshotCandidate.FrameHop> frames, List<SnapshotCandidate.ShadowHop> shadow, String css) {
        List<Hop> hops = new ArrayList<>();
        for (SnapshotCandidate.FrameHop f : frames) {
            for (SnapshotCandidate.ShadowHop sh : f.shadowPath()) hops.add(shadowHop(sh));
            hops.add(new Hop(Kind.FRAME, f.selector()));
        }
        for (SnapshotCandidate.ShadowHop sh : shadow) hops.add(shadowHop(sh));
        hops.add(new Hop(Kind.CSS, css));
        return new KeelTarget(hops);
    }

    private static Hop shadowHop(SnapshotCandidate.ShadowHop sh) {
        return new Hop("closed".equals(sh.mode().toLowerCase(Locale.ROOT)) ? Kind.SHADOW_CLOSED : Kind.SHADOW, sh.host());
    }
}
