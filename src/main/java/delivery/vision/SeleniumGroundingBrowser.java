package delivery.vision;

import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;

import java.util.Map;

public final class SeleniumGroundingBrowser implements GroundingBrowser {

    private static final String METRICS_JS = """
            return {w: window.innerWidth, h: window.innerHeight, dpr: window.devicePixelRatio || 1};
            """;

    private static final String SCROLL_JS = """
            const s = document.scrollingElement || document.documentElement;
            s.scrollBy(0, Math.floor((window.innerHeight||0)*0.8));
            """;

    private static final String ELEMENT_FROM_POINT_JS = """
            function isInteractive(el) {
              if (!el || el.nodeType !== 1) return false;
              const tag = el.tagName.toLowerCase();
              if (['button','a','input','select','textarea'].indexOf(tag) >= 0) return true;
              const role = (el.getAttribute('role') || '').toLowerCase();
              return ['button','link','textbox','combobox','checkbox','radio'].indexOf(role) >= 0;
            }
            function isDisplayed(el) {
              if (!el) return false;
              const style = window.getComputedStyle(el);
              if (style.display === 'none' || style.visibility === 'hidden') return false;
              const rect = el.getBoundingClientRect();
              return rect.width > 0 && rect.height > 0;
            }
            function isEnabled(el) {
              if (!el) return false;
              if (el.disabled) return false;
              if (el.getAttribute('aria-disabled') === 'true') return false;
              return true;
            }
            function fingerprint(el) {
              const tag = el.tagName.toLowerCase();
              const id = el.id || '';
              return tag + (id ? '#' + id : '');
            }
            var el = document.elementFromPoint(arguments[0], arguments[1]);
            while (el && !isInteractive(el)) {
              el = el.parentElement;
            }
            if (!el) return null;
            return {
              tag: el.tagName.toLowerCase(),
              id: el.id || '',
              name: el.getAttribute('name') || '',
              dataTest: el.getAttribute('data-testid') || el.getAttribute('data-test')
                  || el.getAttribute('data-qa') || '',
              ariaLabel: el.getAttribute('aria-label') || '',
              role: (el.getAttribute('role') || '').toLowerCase(),
              displayed: isDisplayed(el),
              enabled: isEnabled(el),
              outerFingerprint: fingerprint(el),
              visibleText: ((el.innerText || el.textContent || '') + '').trim().slice(0, 80)
            };
            """;

    private final WebDriver driver;

    public SeleniumGroundingBrowser(WebDriver driver) {
        this.driver = driver;
    }

    @Override
    public GroundedNode elementFromPoint(double cssX, double cssY) {
        if (!(driver instanceof JavascriptExecutor js)) {
            return null;
        }
        try {
            Object raw = js.executeScript(ELEMENT_FROM_POINT_JS, cssX, cssY);
            if (!(raw instanceof Map<?, ?> map)) {
                return null;
            }
            return new GroundedNode(
                    stringVal(map.get("tag")),
                    stringVal(map.get("id")),
                    stringVal(map.get("name")),
                    stringVal(map.get("dataTest")),
                    stringVal(map.get("ariaLabel")),
                    stringVal(map.get("role")),
                    boolVal(map.get("displayed")),
                    boolVal(map.get("enabled")),
                    stringVal(map.get("outerFingerprint")),
                    stringVal(map.get("visibleText")));
        } catch (WebDriverException e) {
            return null;
        }
    }

    @Override
    public byte[] screenshotPng() {
        if (!(driver instanceof TakesScreenshot ts)) {
            return new byte[0];
        }
        try {
            byte[] png = ts.getScreenshotAs(OutputType.BYTES);
            return png == null ? new byte[0] : png;
        } catch (WebDriverException e) {
            return new byte[0];
        }
    }

    @Override
    public ViewportMetrics metrics() {
        if (!(driver instanceof JavascriptExecutor js)) {
            return new ViewportMetrics(0, 0, 0, 0);
        }
        try {
            Object raw = js.executeScript(METRICS_JS);
            if (!(raw instanceof Map<?, ?> map)) {
                return new ViewportMetrics(0, 0, 0, 0);
            }
            int innerW = intVal(map.get("w"));
            int innerH = intVal(map.get("h"));
            double dpr = doubleVal(map.get("dpr"));
            if (dpr <= 0) {
                dpr = 1;
            }
            int shotW = (int) Math.round(innerW * dpr);
            int shotH = (int) Math.round(innerH * dpr);
            return new ViewportMetrics(innerW, innerH, shotW, shotH);
        } catch (WebDriverException e) {
            return new ViewportMetrics(0, 0, 0, 0);
        }
    }

    @Override
    public void scrollViewport() {
        if (!(driver instanceof JavascriptExecutor js)) {
            return;
        }
        try {
            js.executeScript(SCROLL_JS);
        } catch (WebDriverException ignored) {
            // viewport scroll is best-effort
        }
    }

    private static String stringVal(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }

    private static boolean boolVal(Object o) {
        if (o instanceof Boolean b) {
            return b;
        }
        return "true".equalsIgnoreCase(String.valueOf(o));
    }

    private static int intVal(Object o) {
        if (o instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static double doubleVal(Object o) {
        if (o instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(o));
        } catch (NumberFormatException e) {
            return 1;
        }
    }
}
