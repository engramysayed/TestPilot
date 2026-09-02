package delivery.job;

import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import utils.LogsManager;

/**
 * Page HTML for authoring, including the parts {@code getPageSource()} leaves out.
 *
 * <p>Page source serialises the top document only: a control inside a web component's shadow root
 * or inside an iframe is simply absent, so no candidate can ever be built for it and every step
 * touching one falls through to heal. The runtime already reaches into those contexts
 * ({@link ContextSearch}), so appending their markup is enough to make those controls bindable.
 */
public final class PageSnapshot {
    /** Embedded markup is supplementary; it must not crowd the main document out of the slimmer. */
    private static final int EMBEDDED_LIMIT = 40000;

    private static final String EMBEDDED_HTML_JS = """
            const LIMIT = arguments[0];
            let out = '';
            function collectShadow(root, depth) {
              if (!root || depth > 5 || out.length > LIMIT) return;
              const all = root.querySelectorAll ? root.querySelectorAll('*') : [];
              for (const el of all) {
                if (out.length > LIMIT) return;
                if (el.shadowRoot) {
                  out += '<div data-testpilot-context="shadow">' + el.shadowRoot.innerHTML + '</div>';
                  collectShadow(el.shadowRoot, depth + 1);
                }
              }
            }
            collectShadow(document, 0);
            const frames = document.querySelectorAll('iframe, frame');
            for (const frame of frames) {
              if (out.length > LIMIT) break;
              try {
                const doc = frame.contentDocument;
                if (doc && doc.body) {
                  out += '<div data-testpilot-context="iframe">' + doc.body.innerHTML + '</div>';
                }
              } catch (crossOrigin) {
                // A cross-origin frame is unreadable by design; the runtime still switches into it.
              }
            }
            return out;
            """;

    private PageSnapshot() {
    }

    public static String html(WebDriver driver) {
        if (driver == null) {
            return "";
        }
        String source;
        try {
            source = driver.getPageSource();
        } catch (Exception e) {
            if (DeadBrowserSession.isDead(e)) {
                LogsManager.warn("PAGE_SNAPSHOT: dead browser session: " + e.getMessage());
            } else {
                LogsManager.warn("Could not read page source: " + e.getMessage());
            }
            return "";
        }
        String base = source == null ? "" : source;
        if (!(driver instanceof JavascriptExecutor js)) {
            return base;
        }
        try {
            Object embedded = js.executeScript(EMBEDDED_HTML_JS, EMBEDDED_LIMIT);
            if (embedded instanceof String extra && !extra.isBlank()) {
                LogsManager.info("PAGE_SNAPSHOT: added " + extra.length()
                        + " chars from shadow roots / same-origin frames");
                return base + extra;
            }
        } catch (Exception e) {
            LogsManager.warn("Could not read embedded contexts: " + e.getMessage());
        }
        return base;
    }
}
