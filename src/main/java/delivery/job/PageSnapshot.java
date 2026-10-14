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
            const esc = (v) => (window.CSS && CSS.escape) ? CSS.escape(v) : String(v).replace(/[^a-zA-Z0-9_-]/g, '\\\\$&');
            const attrSel = (tag, name, v) => tag + '[' + name + '="' + String(v).replace(/\\\\/g, '\\\\\\\\').replace(/"/g, '\\\\"') + '"]';
            const enc = (s) => s.replace(/&/g, '&amp;').replace(/'/g, '&#39;').replace(/</g, '&lt;');
            // Stable selector for an element inside its own document / shadow root.
            function selectorFor(el) {
              const tag = el.tagName.toLowerCase();
              const root = el.getRootNode();
              const unique = (sel) => { try { return root.querySelectorAll(sel).length === 1; } catch (e) { return false; } };
              if (el.id && unique('#' + esc(el.id))) return '#' + esc(el.id);
              for (const a of ['data-testid', 'data-test', 'data-qa', 'name', 'title']) {
                const v = el.getAttribute(a);
                if (v && unique(attrSel(tag, a, v))) return attrSel(tag, a, v);
              }
              const src = el.getAttribute('src');
              if (src && unique(attrSel(tag, 'src', src))) return attrSel(tag, 'src', src);
              if (unique(tag)) return tag;
              return tag + ':nth-of-type(' + (Array.from(el.parentNode.children).filter(c => c.tagName === el.tagName).indexOf(el) + 1) + ')';
            }
            function wrap(kind, framePath, shadowPath, html) {
              out += '<div data-testpilot-context="' + kind + '"'
                + " data-testpilot-frame-path='" + enc(JSON.stringify(framePath)) + "'"
                + (shadowPath ? " data-testpilot-shadow-path='" + enc(JSON.stringify(shadowPath)) + "'" : '')
                + '>' + html + '</div>';
            }
            function collectShadow(root, framePath, hostPath, depth) {
              if (!root || depth > 5 || out.length > LIMIT) return;
              const all = root.querySelectorAll ? root.querySelectorAll('*') : [];
              for (const el of all) {
                if (out.length > LIMIT) return;
                if (el.shadowRoot) {
                  const hops = hostPath.concat([selectorFor(el)]);
                  wrap('shadow', framePath, hops, el.shadowRoot.innerHTML);
                  collectShadow(el.shadowRoot, framePath, hops, depth + 1);
                }
              }
            }
            function collectFrames(doc, framePath, depth) {
              if (depth > 5) return;
              const frames = doc.querySelectorAll('iframe, frame');
              for (const frame of frames) {
                if (out.length > LIMIT) return;
                const path = framePath.concat([selectorFor(frame)]);
                try {
                  const inner = frame.contentDocument;
                  if (inner && inner.body) {
                    wrap('iframe', path, null, inner.body.innerHTML);
                    collectShadow(inner, path, [], 0);
                    collectFrames(inner, path, depth + 1);
                  }
                } catch (crossOrigin) {
                  // A cross-origin frame is unreadable by design; the runtime still switches into it.
                }
              }
            }
            collectShadow(document, [], [], 0);
            collectFrames(document, [], 0);
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
