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
            const candidates=Array.from(document.querySelectorAll('*')).filter(e=>{
              const r=e.getBoundingClientRect(),s=getComputedStyle(e);
              return r.width>0&&r.height>40&&r.bottom>0&&r.top<innerHeight
                && /(auto|scroll)/.test(s.overflowY)&&e.scrollHeight>e.clientHeight+2;
            }).sort((a,b)=>{
              const nav=e=>e.closest('nav,aside,[role=navigation],[class*=sidebar],[class*=side-menu]')?1:0;
              return nav(b)-nav(a);
            });
            for(const s of candidates){const before=s.scrollTop;s.scrollBy(0,Math.floor(s.clientHeight*0.8));if(s.scrollTop!==before)return;}
            const s=document.scrollingElement||document.documentElement;
            s.scrollBy(0, Math.floor(innerHeight*0.8));
            """;

    private static final String ELEMENT_FROM_POINT_JS = """
            function isInteractive(el) {
              if (!el || el.nodeType !== 1) return false;
              const tag = el.tagName.toLowerCase();
              if (['button','a','input','select','textarea'].indexOf(tag) >= 0) return true;
              const role = (el.getAttribute('role') || '').toLowerCase();
              if (['button','link','textbox','combobox','listbox','searchbox','checkbox','radio','switch','spinbutton','slider','menuitem','menuitemcheckbox','menuitemradio','tab','treeitem'].includes(role)
                  || el.getAttribute('contenteditable') === 'true') return true;
              if (el.hasAttribute('onclick') || typeof el.onclick === 'function') return true;
              return tag === 'li' && !el.querySelector('li')
                  && (el.getAttribute('data-keel-nav-item') === 'true'
                      || !!el.closest('nav,aside,[role=navigation],[class*=sidebar],[class*=side-menu]'));
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
            function norm(s) {
              return ((s || '') + '').replace(/\s+/g, ' ').trim().toLowerCase();
            }
            const want = norm(arguments[3] || '');
            var el = document.elementFromPoint(arguments[0], arguments[1]);
            var named = null;
            if (want && el) {
              for (let cur = el; cur && cur.nodeType === 1 && cur !== document.body && cur !== document.documentElement; cur = cur.parentElement) {
                const t = norm((cur.innerText || cur.textContent || '').slice(0, 120));
                if (t === want) { named = cur; break; }
              }
            }
            if (named) {
              el = named;
            } else {
              while (el && !isInteractive(el)) {
                el = el.parentElement;
              }
            }
            if (!el) return null;
            const attrs = (arguments[2] || []).concat(['data-testid','data-test','data-qa','data-cy']);
            function uniqueHookOn(node) {
              for (const a of attrs) {
                const value = node.getAttribute(a);
                if (!value) continue;
                const sel = node.tagName.toLowerCase() + '[' + a + '="' + CSS.escape(value) + '"]';
                if (document.querySelectorAll(sel).length === 1) return { attr: a, value: value, node: node };
              }
              return null;
            }
            let hook = uniqueHookOn(el);
            if (!hook) {
              const descendantKeys = new Map();
              for (const desc of el.querySelectorAll('*')) {
                const h = uniqueHookOn(desc);
                if (!h) continue;
                const key = h.attr + '=' + h.value;
                if (!descendantKeys.has(key)) descendantKeys.set(key, h);
              }
              if (descendantKeys.size === 1) hook = descendantKeys.values().next().value;
            }
            if (hook) el = hook.node;
            const preferredAttribute = hook ? hook.attr : '';
            const preferredValue = hook ? hook.value : '';
            const dataTest = el.getAttribute('data-testid') || el.getAttribute('data-test')
                || el.getAttribute('data-qa') || el.getAttribute('data-axis-test-id') || '';
            let dataTestAttribute = 'data-qa';
            if (el.hasAttribute('data-testid')) dataTestAttribute = 'data-testid';
            else if (el.hasAttribute('data-test')) dataTestAttribute = 'data-test';
            else if (el.hasAttribute('data-qa')) dataTestAttribute = 'data-qa';
            else if (el.hasAttribute('data-axis-test-id')) dataTestAttribute = 'data-axis-test-id';
            return {
              preferredAttribute: preferredAttribute,
              preferredValue: preferredValue,
              tag: el.tagName.toLowerCase(),
              id: el.id || '',
              name: el.getAttribute('name') || '',
              dataTest: dataTest,
              ariaLabel: el.getAttribute('aria-label') || '',
              role: (el.getAttribute('role') || (el.tagName.toLowerCase() === 'input' && ['submit','button','reset','image'].includes(el.type) ? 'button' : '')).toLowerCase(),
              displayed: isDisplayed(el),
              enabled: isEnabled(el),
              outerFingerprint: fingerprint(el),
              visibleText: ((el.innerText || el.textContent || '') + '').trim().slice(0, 80)
              , element: el,
              dataTestAttribute: dataTestAttribute
            };
            """;

    private final WebDriver driver;
    private org.openqa.selenium.WebElement observed;

    public SeleniumGroundingBrowser(WebDriver driver) {
        this.driver = driver;
    }

    @Override
    public GroundedNode elementFromPoint(double cssX, double cssY) {
        return elementFromPoint(cssX, cssY, "");
    }

    @Override
    public GroundedNode elementFromPoint(double cssX, double cssY, String namedText) {
        if (!(driver instanceof JavascriptExecutor js)) {
            return null;
        }
        try {
            observed = null;
            Object raw = js.executeScript(ELEMENT_FROM_POINT_JS, cssX, cssY,
                    delivery.store.PreferredHooksStore.current(),
                    namedText == null ? "" : namedText);
            if (!(raw instanceof Map<?, ?> map)) {
                return null;
            }
            observed = map.get("element") instanceof org.openqa.selenium.WebElement el ? el : null;
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
                    stringVal(map.get("visibleText")), stringVal(map.get("dataTestAttribute")),
                    stringVal(map.get("preferredAttribute")), stringVal(map.get("preferredValue")));
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
            if (png == null) {
                return new byte[0];
            }
            return delivery.privacy.ScreenshotRedactor.redactCapture(driver, png);
        } catch (WebDriverException e) {
            return new byte[0];
        }
    }

    @Override
    public boolean matchesObservedNode(String strategy, String value) {
        try {
            org.openqa.selenium.By by = switch (strategy.toLowerCase()) {
                case "id" -> org.openqa.selenium.By.id(value);
                case "name" -> org.openqa.selenium.By.name(value);
                case "xpath" -> org.openqa.selenium.By.xpath(value);
                case "css", "cssselector" -> org.openqa.selenium.By.cssSelector(value);
                case "data-test", "data-testid", "data-qa" -> org.openqa.selenium.By.xpath("//*[@" + strategy + "=" + delivery.authoring.XpathLiterals.quote(value) + "]");
                default -> null;
            };
            if (by == null || observed == null) return false;
            var matches = driver.findElements(by);
            return matches.size() == 1 && matches.get(0).equals(observed) && observed.isDisplayed() && observed.isEnabled();
        } catch (RuntimeException unavailable) { return false; }
    }

    @Override public boolean hasObservationIdentity() { return true; }

    @Override
    public String observationVersion() {
        if (!(driver instanceof JavascriptExecutor js)) return "";
        try {
            return String.valueOf(js.executeScript("""
                if (!window.__keelGroundObserver) {
                  window.__keelGroundVersion = 0;
                  window.__keelGroundObserver = new MutationObserver(() => window.__keelGroundVersion++);
                  window.__keelGroundObserver.observe(document.documentElement, {subtree:true,childList:true,attributes:true,characterData:true});
                }
                const nested=Array.from(document.querySelectorAll('*')).filter(e=>e.scrollTop||e.scrollLeft)
                  .map(e=>e.scrollLeft+','+e.scrollTop).join(';');
                return location.href+'|'+innerWidth+'|'+innerHeight+'|'+scrollX+'|'+scrollY+'|'+nested+'|'+window.__keelGroundVersion;
                """));
        } catch (RuntimeException unavailable) { return "unavailable"; }
    }

    @Override public Object saveScroll() {
        return driver instanceof JavascriptExecutor js ? js.executeScript("return Array.from(document.querySelectorAll('*')).filter(e=>e.scrollHeight>e.clientHeight+2&&e.clientHeight>0).map(e=>[e,e.scrollLeft,e.scrollTop])") : null;
    }
    @Override public void restoreScroll(Object position) {
        if (driver instanceof JavascriptExecutor js && position instanceof java.util.List<?> p)
            js.executeScript("for(const row of arguments[0]){if(row[0].isConnected){row[0].scrollLeft=row[1];row[0].scrollTop=row[2]}}", p);
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
