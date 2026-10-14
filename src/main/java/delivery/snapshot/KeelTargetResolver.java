package delivery.snapshot;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.SearchContext;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Reference runtime for {@link KeelTarget} (contract v2). Keel Codegen's generated helper must behave
 * identically (see patches/contract-v2/SPEC.md and golden vectors). Leaves the driver switched into
 * the innermost frame of the target; callers switch back with {@link #reset(WebDriver)}.
 */
public final class KeelTargetResolver {

    static final String CLOSED_ROOT_QUERY_JS =
            "const r=arguments[0][Symbol.for('keel.shadowRoot')];"
            + "if(!r) throw new Error('keel: closed shadow root not captured (hook missing?)');"
            + "const m=r.querySelectorAll(arguments[1]); return Array.from(m);";

    /** Shared, byte-identical with Codegen. */
    public static final String TEXT_MATCH_JS = resource("/keel/keel-text-match.js");
    static final String TEXT_CALL = "\nreturn keelTextMatch(arguments[0], arguments[1], arguments[2]);";
    static final String PROVIDER_JS =
            "const p=(window.__keelProviders||{})[arguments[1]];"
            + "if(typeof p!=='function') throw new Error('keel: canvas provider not registered: '+arguments[1]);"
            + "return p(arguments[0], arguments[2]);";

    /** Anchor element plus the click offset from its centre (Selenium Actions origin), CSS px. */
    public record ClickPoint(WebElement element, double dxFromCentre, double dyFromCentre) {
    }

    private KeelTargetResolver() {
    }

    public static void reset(WebDriver driver) {
        driver.switchTo().defaultContent();
    }

    /** Same text as Keel Codegen's KeelLocate (template) so failures read identically on both sides. */
    static void failFastOnFirefox(WebDriver driver, KeelTarget target) {
        if (target.hops().stream().anyMatch(h -> h.kind() == KeelTarget.Kind.SHADOW_CLOSED) && isFirefox(driver)) {
            throw new UnsupportedOperationException(
                    "keel: shadow-closed= targets need Chrome/Edge (CDP closed-shadow hook); not supported on Firefox: "
                            + target.format());
        }
    }

    static boolean isFirefox(WebDriver driver) {
        if (driver instanceof org.openqa.selenium.HasCapabilities hc) {
            org.openqa.selenium.Capabilities caps = hc.getCapabilities();
            return caps != null && "firefox".equalsIgnoreCase(caps.getBrowserName());
        }
        return false;
    }

    /** Search state inside one frame: document, or the shadow root of {@code host}. */
    private static final class Ctx {
        SearchContext root;
        WebElement host;
        String mode; // null | open | closed
    }

    private static Ctx enter(WebDriver driver, KeelTarget target) {
        failFastOnFirefox(driver, target);
        driver.switchTo().defaultContent();
        Ctx c = new Ctx();
        c.root = driver;
        for (KeelTarget.Hop hop : target.hops().subList(0, target.locatorIndex())) {
            WebElement el = one(match(driver, c, new KeelTarget.Hop(KeelTarget.Kind.CSS, hop.css())), hop.css());
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
    private static List<WebElement> match(WebDriver driver, Ctx c, KeelTarget.Hop locator) {
        JavascriptExecutor js = (JavascriptExecutor) driver;
        if (locator.kind() == KeelTarget.Kind.TEXT)
            return (List<WebElement>) js.executeScript(TEXT_MATCH_JS + TEXT_CALL, c.host, c.mode, locator.css());
        if ("closed".equals(c.mode))
            return (List<WebElement>) js.executeScript(CLOSED_ROOT_QUERY_JS, c.host, locator.css());
        return c.root.findElements(By.cssSelector(locator.css()));
    }

    /** All matches of the locator hop (context hops must be unique). Modifiers are ignored. */
    public static List<WebElement> findAll(WebDriver driver, KeelTarget target) {
        Ctx c = enter(driver, target);
        return match(driver, c, target.locator());
    }

    /** The anchor element (exactly one match required). */
    public static WebElement find(WebDriver driver, KeelTarget target) {
        Ctx c = enter(driver, target);
        KeelTarget.Hop l = target.locator();
        return one(match(driver, c, l), l.kind() == KeelTarget.Kind.CSS ? l.css() : "text=" + KeelTarget.quote(l.css()));
    }

    /** Anchor + click offset for point=/provider= targets; plain targets click the centre (0,0). */
    @SuppressWarnings("unchecked")
    public static ClickPoint resolveClick(WebDriver driver, KeelTarget target) {
        WebElement el = find(driver, target);
        org.openqa.selenium.Rectangle r = el.getRect();
        double w = r.getWidth(), h = r.getHeight();
        if (target.provider().isPresent()) {
            String[] p = target.provider().get().split(":", 2);
            try {
                Object box = ((JavascriptExecutor) driver).executeScript(PROVIDER_JS, el, p[0], p[1]);
                if (box instanceof Map<?, ?> b) {
                    double bx = num(b.get("x")), by = num(b.get("y")), bw = num(b.get("w")), bh = num(b.get("h"));
                    return new ClickPoint(el, bx + bw / 2 - w / 2, by + bh / 2 - h / 2);
                }
            } catch (RuntimeException providerFailed) {
                if (target.point().isEmpty()) throw providerFailed;
            }
            if (target.point().isEmpty()) throw new NoSuchElementException("keel: provider returned no box");
        }
        if (target.point().isPresent()) {
            double[] d = target.point().get().offsetFromCentre(w, h);
            return new ClickPoint(el, d[0], d[1]);
        }
        return new ClickPoint(el, 0, 0);
    }

    private static double num(Object o) {
        return o instanceof Number n ? n.doubleValue() : Double.parseDouble(String.valueOf(o));
    }

    private static WebElement one(List<WebElement> found, String what) {
        if (found == null || found.size() != 1)
            throw new NoSuchElementException("keel: '" + what + "' matched " + (found == null ? 0 : found.size()) + " (need exactly 1)");
        return found.get(0);
    }

    private static String resource(String path) {
        try (InputStream in = KeelTargetResolver.class.getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("missing resource " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
