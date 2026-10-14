package project.utils.locators;

import org.openqa.selenium.By;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.HasCapabilities;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.Rectangle;
import org.openqa.selenium.SearchContext;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Runtime for Keel targets, contract v2 (superset of v1). Mirrors the TestPilot reference
 * {@code delivery.snapshot.KeelTarget} + {@code KeelTargetResolver} (find/findAll/resolveClick/reset),
 * including error types and messages; verified against the shared golden vectors.
 * <pre>
 *   target   := context* locator provider? point?
 *   context  := ("frame=" | "shadow=" | "shadow-closed=") CSS
 *   locator  := "css=" CSS | "text=" JSON-STRING        (text= is v2)
 *   provider := "provider=" NAME ":" ID                  (v2, only after css=)
 *   point    := "point=" X "," Y "@" W "x" H             (v2)
 * </pre>
 * text= is evaluated only by the shared, byte-identical {@code /keel/keel-text-match.js}.
 * Leaves the driver inside the target's innermost frame; call {@link #reset(WebDriver)} after the action.
 */
public final class KeelLocate {
    /** Highest locator contract this runtime understands. */
    public static final int CONTRACT_VERSION = 2;
    public static final String SEPARATOR = " >> ";

    static final String CLOSED_ROOT_QUERY_JS =
            "const r=arguments[0][Symbol.for('keel.shadowRoot')];"
            + "if(!r) throw new Error('keel: closed shadow root not captured (hook missing?)');"
            + "const m=r.querySelectorAll(arguments[1]); return Array.from(m);";
    public static final String TEXT_MATCH_JS = resource("/keel/keel-text-match.js");
    static final String TEXT_CALL = "\nreturn keelTextMatch(arguments[0], arguments[1], arguments[2]);";
    static final String PROVIDER_JS =
            "const p=(window.__keelProviders||{})[arguments[1]];"
            + "if(typeof p!=='function') throw new Error('keel: canvas provider not registered: '+arguments[1]);"
            + "return p(arguments[0], arguments[2]);";

    public enum Kind { FRAME, SHADOW, SHADOW_CLOSED, CSS, TEXT, PROVIDER, POINT }

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

    /** Anchor element plus click offset from its centre (Selenium Actions origin), CSS px. */
    public record ClickPoint(WebElement element, double dxFromCentre, double dyFromCentre) {
    }

    /** point= modifier: offset (x,y) from the element's top-left at recorded size w x h. */
    public record Point(double x, double y, double w, double h) {
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
    }

    private KeelLocate() {
    }

    /** Fails with a clear error when generated code needs a newer contract than this runtime. */
    public static void requireContract(int version) {
        if (version > CONTRACT_VERSION) {
            throw new UnsupportedOperationException("keel: generated code needs locator contract " + version
                    + " but this framework runtime supports up to " + CONTRACT_VERSION
                    + "; update project.utils.locators (regenerate the framework ZIP)");
        }
    }

    public static void reset(WebDriver driver) {
        driver.switchTo().defaultContent();
    }

    // ---------------------------------------------------------------- resolution

    private static final class Ctx {
        SearchContext root;
        WebElement host;
        String mode; // null | open | closed
    }

    private static Ctx enter(WebDriver driver, List<Hop> hops) {
        failFastOnFirefox(driver, hops);
        driver.switchTo().defaultContent();
        Ctx c = new Ctx();
        c.root = driver;
        for (Hop hop : hops.subList(0, locatorIndex(hops))) {
            WebElement el = one(match(driver, c, new Hop(Kind.CSS, hop.css())), hop.css());
            switch (hop.kind()) {
                case FRAME -> { driver.switchTo().frame(el); c.root = driver; c.host = null; c.mode = null; }
                case SHADOW -> { c.root = el.getShadowRoot(); c.host = el; c.mode = "open"; }
                case SHADOW_CLOSED -> { c.host = el; c.mode = "closed"; }
                default -> throw new IllegalStateException();
            }
        }
        return c;
    }

    @SuppressWarnings("unchecked")
    private static List<WebElement> match(WebDriver driver, Ctx c, Hop locator) {
        JavascriptExecutor js = (JavascriptExecutor) driver;
        if (locator.kind() == Kind.TEXT)
            return (List<WebElement>) js.executeScript(TEXT_MATCH_JS + TEXT_CALL, c.host, c.mode, locator.css());
        if ("closed".equals(c.mode))
            return (List<WebElement>) js.executeScript(CLOSED_ROOT_QUERY_JS, c.host, locator.css());
        return c.root.findElements(By.cssSelector(locator.css()));
    }

    public static WebElement find(WebDriver driver, String target) {
        return find(driver, parse(target));
    }

    /** The anchor element (exactly one match per hop). Modifiers do not affect matching. */
    public static WebElement find(WebDriver driver, List<Hop> hops) {
        Ctx c = enter(driver, hops);
        Hop l = hops.get(locatorIndex(hops));
        return one(match(driver, c, l), l.kind() == Kind.CSS ? l.css() : "text=" + quote(l.css()));
    }

    public static List<WebElement> findAll(WebDriver driver, List<Hop> hops) {
        Ctx c = enter(driver, hops);
        return match(driver, c, hops.get(locatorIndex(hops)));
    }

    public static ClickPoint resolveClick(WebDriver driver, String target) {
        return resolveClick(driver, parse(target));
    }

    /** Anchor + click offset for point=/provider= targets; plain targets click the centre (0,0). */
    public static ClickPoint resolveClick(WebDriver driver, List<Hop> hops) {
        WebElement el = find(driver, hops);
        return clickPointFor(driver, el, hops);
    }

    /** Offset for an already-resolved anchor (driver must still be in its frame). */
    static ClickPoint clickPointFor(WebDriver driver, WebElement el, List<Hop> hops) {
        Rectangle r = el.getRect();
        double w = r.getWidth(), h = r.getHeight();
        Optional<String> provider = provider(hops);
        Optional<Point> point = point(hops);
        if (provider.isPresent()) {
            String[] p = provider.get().split(":", 2);
            try {
                Object box = ((JavascriptExecutor) driver).executeScript(PROVIDER_JS, el, p[0], p[1]);
                if (box instanceof Map<?, ?> b) {
                    double bx = num(b.get("x")), by = num(b.get("y")), bw = num(b.get("w")), bh = num(b.get("h"));
                    return new ClickPoint(el, bx + bw / 2 - w / 2, by + bh / 2 - h / 2);
                }
            } catch (RuntimeException providerFailed) {
                if (point.isEmpty()) throw providerFailed;
            }
            if (point.isEmpty()) throw new NoSuchElementException("keel: provider returned no box");
        }
        if (point.isPresent()) {
            double[] d = point.get().offsetFromCentre(w, h);
            return new ClickPoint(el, d[0], d[1]);
        }
        return new ClickPoint(el, 0, 0);
    }

    public static boolean hasPointer(List<Hop> hops) {
        return hops.stream().anyMatch(h -> h.kind() == Kind.PROVIDER || h.kind() == Kind.POINT);
    }

    private static double num(Object o) {
        return o instanceof Number n ? n.doubleValue() : Double.parseDouble(String.valueOf(o));
    }

    private static WebElement one(List<WebElement> found, String what) {
        if (found == null || found.size() != 1)
            throw new NoSuchElementException("keel: '" + what + "' matched " + (found == null ? 0 : found.size()) + " (need exactly 1)");
        return found.get(0);
    }

    static void failFastOnFirefox(WebDriver driver, List<Hop> hops) {
        if (hops.stream().anyMatch(h -> h.kind() == Kind.SHADOW_CLOSED) && isFirefox(driver)) {
            throw new UnsupportedOperationException(
                    "keel: shadow-closed= targets need Chrome/Edge (CDP closed-shadow hook); not supported on Firefox: "
                            + format(hops));
        }
    }

    static boolean isFirefox(WebDriver driver) {
        if (driver instanceof HasCapabilities hc) {
            Capabilities caps = hc.getCapabilities();
            return caps != null && "firefox".equalsIgnoreCase(caps.getBrowserName());
        }
        return false;
    }

    // ---------------------------------------------------------------- grammar

    static int locatorIndex(List<Hop> hops) {
        for (int i = 0; i < hops.size(); i++) if (hops.get(i).isLocator()) return i;
        throw new IllegalStateException();
    }

    static Optional<Point> point(List<Hop> hops) {
        return hops.stream().filter(h -> h.kind() == Kind.POINT).findFirst().map(h -> Point.parse(h.css()));
    }

    static Optional<String> provider(List<Hop> hops) {
        return hops.stream().filter(h -> h.kind() == Kind.PROVIDER).findFirst().map(Hop::css);
    }

    /** 1 for css-only targets, 2 when text=/provider=/point= are used. */
    public static int requiredVersion(List<Hop> hops) {
        return hops.stream().anyMatch(h -> h.kind() == Kind.TEXT || h.kind() == Kind.PROVIDER || h.kind() == Kind.POINT) ? 2 : 1;
    }

    public static boolean isPlainCss(String target) {
        List<Hop> h = parse(target);
        return h.size() == 1 && h.get(0).kind() == Kind.CSS;
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

    public static String format(List<Hop> hops) {
        StringBuilder sb = new StringBuilder();
        for (Hop h : hops) {
            if (sb.length() > 0) sb.append(SEPARATOR);
            sb.append(h.prefix());
            sb.append(h.kind() == Kind.TEXT ? quote(h.css()) : h.css().replace(">>", "\\>>"));
        }
        return sb.toString();
    }

    public static List<Hop> parse(String s) {
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
        validate(hops);
        return List.copyOf(hops);
    }

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

    private static String resource(String path) {
        try (InputStream in = KeelLocate.class.getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("missing resource " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
