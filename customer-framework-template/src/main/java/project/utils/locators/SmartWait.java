package project.utils.locators;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptException;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.ScriptTimeoutException;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.Map;

/**
 * Actionability wait (no {@code Thread.sleep}): the element's bounding box is identical across two
 * {@code requestAnimationFrame} ticks AND {@code elementFromPoint} at its centre hits the element
 * or a descendant (piercing open shadow roots, and closed ones captured by the Keel hook). Polled with {@link WebDriverWait}.
 */
public final class SmartWait {
    static final String STABLE_AND_HIT_JS = """
            const el = arguments[0];
            const done = arguments[arguments.length - 1];
            if (!el || !el.isConnected) { done({ok: false, why: 'detached'}); return; }
            const box = () => { const r = el.getBoundingClientRect(); return [r.left, r.top, r.width, r.height]; };
            const a = box();
            requestAnimationFrame(() => requestAnimationFrame(() => {
              const b = box();
              const stable = a.every((v, i) => Math.abs(v - b[i]) < 0.5);
              if (!stable) { done({ok: false, why: 'moving'}); return; }
              if (b[2] <= 0 || b[3] <= 0) { done({ok: false, why: 'zero-size'}); return; }
              const cx = b[0] + b[2] / 2, cy = b[1] + b[3] / 2;
              if (cx < 0 || cy < 0 || cx >= innerWidth || cy >= innerHeight) {
                el.scrollIntoView({block: 'center', inline: 'center'});
                done({ok: false, why: 'offscreen'}); return;
              }
              let hit = document.elementFromPoint(cx, cy);
              const rootOf = (h) => h.shadowRoot || h[Symbol.for('keel.shadowRoot')];
              while (hit && rootOf(hit)) {
                const inner = rootOf(hit).elementFromPoint(cx, cy);
                if (!inner || inner === hit) break;
                hit = inner;
              }
              for (let n = hit; n; n = n.parentNode || n.host) {
                if (n === el) { done({ok: true, why: ''}); return; }
              }
              done({ok: false, why: 'covered by ' + (hit ? hit.tagName.toLowerCase() : 'nothing')});
            }));
            """;

    private SmartWait() {
    }

    /** Re-locates on every poll (survives re-renders), returns the actionable element. */
    public static WebElement untilActionable(WebDriver driver, By locator, Duration timeout) {
        String[] last = {""};
        return new WebDriverWait(driver, timeout)
                    .pollingEvery(Duration.ofMillis(100))
                    .ignoring(StaleElementReferenceException.class)
                    .ignoring(NoSuchElementException.class)
                    .ignoring(JavascriptException.class)
                    .ignoring(ScriptTimeoutException.class)
                    .withMessage(() -> "not actionable: " + locator + " (" + last[0] + ")")
                    .until(d -> {
                        WebElement el = d.findElement(locator);
                        String why = check(d, el);
                        last[0] = why;
                        return why.isEmpty() ? el : null;
                    });
    }

    /** Uses DEFAULT_WAIT (seconds) from the framework properties, 10s when absent. */
    public static WebElement untilActionable(WebDriver driver, By locator) {
        return untilActionable(driver, locator, defaultTimeout());
    }

    static Duration defaultTimeout() {
        try {
            String raw = project.utils.dataReader.PropertyReader.getProperty("DEFAULT_WAIT");
            return Duration.ofSeconds(raw == null || raw.isBlank() ? 10 : Long.parseLong(raw.trim()));
        } catch (RuntimeException e) {
            return Duration.ofSeconds(10);
        }
    }

    public static WebElement untilActionable(WebDriver driver, WebElement element, Duration timeout) {
        String[] last = {""};
        return new WebDriverWait(driver, timeout)
                .pollingEvery(Duration.ofMillis(100))
                .ignoring(JavascriptException.class)
                .ignoring(ScriptTimeoutException.class)
                .withMessage(() -> "not actionable: " + element + " (" + last[0] + ")")
                .until(d -> {
                    String why = check(d, element);
                    last[0] = why;
                    return why.isEmpty() ? element : null;
                });
    }

    /** Empty string when actionable, else the reason. */
    static String check(WebDriver driver, WebElement el) {
        Object r = ((JavascriptExecutor) driver).executeAsyncScript(STABLE_AND_HIT_JS, el);
        if (r instanceof Map<?, ?> m && Boolean.TRUE.equals(m.get("ok"))) {
            return "";
        }
        return r instanceof Map<?, ?> m ? String.valueOf(m.get("why")) : "unknown";
    }
}
