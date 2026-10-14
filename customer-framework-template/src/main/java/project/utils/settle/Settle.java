package project.utils.settle;

import io.qameta.allure.Allure;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.support.ui.WebDriverWait;
import project.utils.Logs.LogsManager;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Post-action settle for generated actions, driven by the A8 policy ({@link SettlePolicy}) and the
 * shared A8 probe ({@code /keel/settle-probe.js}, byte-identical to the engine). Same conditions as
 * the engine's {@code SettleWaiter.await}: readyState + network idle (when network=idle), DOM quiet,
 * no running finite animations, no visible spinners, url any/same/changed; quiet windows never start
 * before the action. Differences: every wait is a bounded {@link WebDriverWait} poll (no
 * {@code Thread.sleep}), the probe runs on the top document, and the rule's effect/effectWaitMs is
 * checked first. Effect signals (any one is enough): DOM signature ({@code [data-keel-volatile]} subtrees
 * excluded), URL, network activity started after the action (A8 probe trackers), and the visual check
 * ({@link ScreenshotDiff}: 8x8 blocks, mean luma &gt; 8/255, one block; volatile rects + one block of
 * slack and the target's own box masked). NO_EFFECT on an effect=required rule throws
 * {@link NoEffectError} by default ({@code -Dkeel.noeffect=warn}: WARN + Allure step only).
 * A settle timeout is reported (WARN + Allure step), never thrown and never PASS evidence.
 */
public final class Settle {
    static final String PROBE_JS = resource("/keel/settle-probe.js");
    /** r3 page-scoped network tracker, byte-identical to the engine's keel/settle-network.js. */
    static final String NETWORK_JS = resource("/keel/settle-network.js");
    /** System property: {@code page} (default) counts only requests the page starts to its own/allowed origins; {@code all} = legacy. */
    public static final String NETWORK_SCOPE_PROPERTY = "keel.settle.network.scope";
    /** Comma list of URL substrings, {@code /regex/} or {@code re:<regex>} that never count (System property, then properties files). */
    public static final String NETWORK_IGNORE_PROPERTY = "keel.settle.network.ignore";
    /** Comma list of extra origins that count under scope=page (System property, then properties files). */
    public static final String NETWORK_ALLOW_PROPERTY = "keel.settle.network.allow";
    /**
     * System property: {@code corroborated} (default) = network activity alone is never an EFFECT (DOM, URL or
     * visual change is required); {@code alone} = legacy r1-07 behaviour (a page request after the action is one).
     */
    public static final String NETWORK_EFFECT_PROPERTY = "keel.settle.network.effect";

    public enum EffectOutcome { EFFECT, NO_EFFECT, NOT_CHECKED }

    /** {@code effectSignal}: dom | url | network | visual when EFFECT, else "". */
    public record Result(boolean settled, long elapsedMs, List<String> unmet, String url, EffectOutcome effect,
                         String effectSignal) {
        public Result(boolean settled, long elapsedMs, List<String> unmet, String url, EffectOutcome effect) {
            this(settled, elapsedMs, unmet, url, effect, "");
        }
    }

    /** System property: {@code fail} (default) or {@code warn}. */
    public static final String NOEFFECT_PROPERTY = "keel.noeffect";
    /** System property: visual effect signal on (default) / {@code false}. */
    public static final String VISUAL_PROPERTY = "keel.effect.visual";
    public static final String VOLATILE_SELECTOR = "[data-keel-volatile]";

    /** Absolute (top-viewport, CSS px) rects of volatile elements in this and same-origin child frames, plus dpr. */
    static final String VOLATILE_RECTS_JS = """
            const sel = arguments[0], out = [];
            const walk = (win, ox, oy, depth) => {
              if (!win || depth > 5) return;
              let doc; try { doc = win.document; } catch (e) { return; }
              try { doc.querySelectorAll(sel).forEach(el => {
                const r = el.getBoundingClientRect();
                if (r.width > 0 && r.height > 0) out.push([ox + r.left, oy + r.top, r.width, r.height]);
              }); } catch (e) {}
              doc.querySelectorAll('iframe,frame').forEach(fr => {
                const r = fr.getBoundingClientRect();
                walk(fr.contentWindow, ox + r.left + fr.clientLeft, oy + r.top + fr.clientTop, depth + 1);
              });
            };
            walk(window, 0, 0, 0);
            return {dpr: window.devicePixelRatio || 1, rects: out};
            """;

    /** Element rect in top-viewport CSS px (adds same-origin frameElement offsets) plus top dpr. */
    static final String ELEMENT_RECT_JS = """
            const el = arguments[0]; const r = el.getBoundingClientRect();
            let x = r.left, y = r.top, w = el.ownerDocument.defaultView, top = w;
            try {
              while (w.frameElement) {
                const f = w.frameElement, fr = f.getBoundingClientRect();
                x += fr.left + f.clientLeft; y += fr.top + f.clientTop; w = w.parent; top = w;
              }
            } catch (e) {}
            return [x, y, r.width, r.height, top.devicePixelRatio || 1];
            """;

    static final String SIGNATURE_JS = """
            const parts = [];
            const walk = (doc, depth) => {
              if (!doc || depth > 5) return;
              if (doc.documentElement) {
                // [data-keel-volatile] subtrees (clocks, carousels) are not an effect: digest a pruned clone
                let root = doc.documentElement;
                if (root.querySelector('[data-keel-volatile]')) {
                  root = root.cloneNode(true);
                  root.querySelectorAll('[data-keel-volatile]').forEach(v => v.remove());
                }
                parts.push(root.outerHTML);
              }
              const w = doc.defaultView;
              if (w) parts.push(w.location.href, w.scrollX + ',' + w.scrollY);
              for (const f of doc.querySelectorAll('input,textarea,select')) parts.push(String(f.value), String(f.checked));
              for (const fr of doc.querySelectorAll('iframe,frame')) {
                try { walk(fr.contentDocument, depth + 1); } catch (e) { parts.push('x-origin'); }
              }
            };
            walk(document, 0);
            const s = parts.join('\\u0001');
            let h = 0x811c9dc5;
            for (let i = 0; i < s.length; i++) { h ^= s.charCodeAt(i); h = Math.imul(h, 0x01000193) >>> 0; }
            return s.length + ':' + h.toString(16);
            """;

    private Settle() {
    }

    /** Runs {@code act} for the A8 action key (click/type/select/drag/...), then effect check + settle. */
    public static Result after(WebDriver driver, String action, Runnable act) {
        return after(driver, SettlePolicy.builtin(), action, null, act);
    }

    /** As {@link #after(WebDriver, String, Runnable)}; {@code target}'s own box is masked in the visual check. */
    public static Result after(WebDriver driver, String action, By target, Runnable act) {
        return after(driver, SettlePolicy.builtin(), action, target, act);
    }

    public static Result after(WebDriver driver, SettlePolicy policy, String action, Runnable act) {
        return after(driver, policy, action, null, act);
    }

    public static Result after(WebDriver driver, SettlePolicy policy, String action, By target, Runnable act) {
        SettlePolicy.Rule rule = policy.rule(action);
        boolean checkEffect = rule.effect() != SettlePolicy.Effect.NONE;
        String urlBefore = prime(driver, policy);
        String sigBefore = checkEffect ? signature(driver) : null;
        boolean visual = checkEffect && !"false".equalsIgnoreCase(System.getProperty(VISUAL_PROPERTY, "true"));
        List<Rectangle> masks = new ArrayList<>();
        BufferedImage shotBefore = null;
        if (visual) {
            awaitPaint(driver); // the before-shot must not catch a half-painted first frame
            masks.addAll(volatileRects(driver));
            shotBefore = screenshot(driver);
            masks.addAll(volatileRects(driver)); // volatile boxes can grow while the shot is taken
        }
        act.run();
        long t0 = System.currentTimeMillis();
        if (visual && target != null) {
            masks.addAll(targetRect(driver, target)); // focus ring / :active styling of the acted-on control
        }
        Duration poll = Duration.ofMillis(policy.pollMs());
        EffectOutcome effect = EffectOutcome.NOT_CHECKED;
        String signal = "";
        if (sigBefore != null) {
            BufferedImage before = shotBefore;
            try {
                signal = new WebDriverWait(driver, Duration.ofMillis(rule.effectWaitMs()), poll)
                        .ignoring(WebDriverException.class)
                        .until(d -> effectSignal(d, policy, sigBefore, urlBefore, t0, before, masks));
                effect = EffectOutcome.EFFECT;
            } catch (TimeoutException e) {
                effect = EffectOutcome.NO_EFFECT;
            }
        }
        List<String>[] unmet = new List[]{List.of("probe")};
        String[] url = {""};
        boolean settled;
        try {
            // FluentWait keeps polling on Boolean.FALSE, so the outcome is a String sentinel.
            settled = "settled".equals(new WebDriverWait(driver, Duration.ofMillis(Math.max(rule.timeoutMs(), 1)), poll)
                    .ignoring(WebDriverException.class)
                    .until(d -> {
                        unmet[0] = check(d, policy, rule, urlBefore, System.currentTimeMillis() - t0, url);
                        if (unmet[0].isEmpty()) {
                            return "settled";
                        }
                        // url=same violated is a fact, not a wait condition (engine r3: report it now, alone)
                        if (unmet[0].contains("url-same")) {
                            unmet[0] = List.of("url-same");
                            return "url-same";
                        }
                        return null;
                    }));
        } catch (TimeoutException e) {
            settled = false;
        }
        Result r = new Result(settled, System.currentTimeMillis() - t0, List.copyOf(unmet[0]), url[0], effect, signal);
        report(action, rule, r);
        if (r.effect() == EffectOutcome.NO_EFFECT && rule.effect() == SettlePolicy.Effect.REQUIRED && !noEffectWarnOnly()) {
            throw new NoEffectError(action, "keel: '" + action + "' had no observable effect (no DOM, URL or visual change within "
                    + rule.effectWaitMs() + " ms; A8 effect=required). Opt out with -D" + NOEFFECT_PROPERTY + "=warn");
        }
        return r;
    }

    static boolean noEffectWarnOnly() {
        return "warn".equalsIgnoreCase(System.getProperty(NOEFFECT_PROPERTY, "fail").trim());
    }

    /** One effect poll: the first signal seen (dom, url, network, visual), or null to keep polling. */
    static String effectSignal(WebDriver d, SettlePolicy policy, String sigBefore, String urlBefore, long actedAt,
                               BufferedImage shotBefore, List<Rectangle> masks) {
        Map<String, Object> p = probe(d, policy);
        if (urlBefore != null && !urlBefore.isEmpty() && !urlBefore.equals(String.valueOf(p.get("url")))) {
            return "url";
        }
        if (!signature(d).equals(sigBefore)) {
            return "dom";
        }
        // A8 network signal: a page request started/finished after the action. r3: only with -D
        // keel.settle.network.effect=alone; by default network activity alone is not an observable effect.
        long sinceAct = System.currentTimeMillis() - actedAt;
        if ("alone".equalsIgnoreCase(prop(NETWORK_EFFECT_PROPERTY, "corroborated"))
                && num(p.get("netIdleMs")) < sinceAct) {
            return "network";
        }
        if (shotBefore != null) {
            List<Rectangle> m = new ArrayList<>(masks);
            m.addAll(volatileRects(d));
            BufferedImage now = screenshot(d);
            m.addAll(volatileRects(d));
            if (ScreenshotDiff.compare(shotBefore, now, m).changed()) {
                return "visual";
            }
        }
        return null;
    }

    /** Waits two animation frames (pending style/layout/paint committed); best effort, bounded by the script timeout. */
    static void awaitPaint(WebDriver driver) {
        try {
            driver.switchTo().defaultContent();
            ((JavascriptExecutor) driver).executeAsyncScript(
                    "const done = arguments[arguments.length - 1];"
                            + "try { requestAnimationFrame(() => requestAnimationFrame(() => done(true))); } catch (e) { done(false); }");
        } catch (WebDriverException ignored) {
            // no rAF (navigating, hidden tab): the poll still compares against this baseline
        }
    }

    static BufferedImage screenshot(WebDriver driver) {
        driver.switchTo().defaultContent();
        return ScreenshotDiff.read(((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES));
    }

    /** {@code [data-keel-volatile]} rects in screenshot px, each grown by one block of slack. */
    @SuppressWarnings("unchecked")
    static List<Rectangle> volatileRects(WebDriver driver) {
        List<Rectangle> out = new ArrayList<>();
        try {
            driver.switchTo().defaultContent();
            Map<String, Object> m = (Map<String, Object>) ((JavascriptExecutor) driver).executeScript(VOLATILE_RECTS_JS, VOLATILE_SELECTOR);
            double dpr = num(m.get("dpr"));
            for (Object o : (List<Object>) m.get("rects")) {
                List<Object> r = (List<Object>) o;
                out.add(ScreenshotDiff.withSlack(px(num(r.get(0)), num(r.get(1)), num(r.get(2)), num(r.get(3)), dpr)));
            }
        } catch (WebDriverException | ClassCastException | NullPointerException ignored) {
            // no masks on a navigating page; the DOM/URL signals still apply
        }
        return out;
    }

    /** The target's own box (screenshot px, one block of slack), measured in the action's frame context. */
    @SuppressWarnings("unchecked")
    static List<Rectangle> targetRect(WebDriver driver, By target) {
        try {
            List<org.openqa.selenium.WebElement> els = driver.findElements(target);
            if (els.isEmpty()) {
                return List.of();
            }
            List<Object> r = (List<Object>) ((JavascriptExecutor) driver).executeScript(ELEMENT_RECT_JS, els.get(0));
            return List.of(ScreenshotDiff.withSlack(px(num(r.get(0)), num(r.get(1)), num(r.get(2)), num(r.get(3)), num(r.get(4)))));
        } catch (WebDriverException | ClassCastException | NullPointerException gone) {
            return List.of(); // target navigated away / detached: nothing to mask
        }
    }

    private static Rectangle px(double x, double y, double w, double h, double dpr) {
        double k = dpr <= 0 ? 1 : dpr;
        int x0 = (int) Math.floor(x * k), y0 = (int) Math.floor(y * k);
        return new Rectangle(x0, y0, (int) Math.ceil((x + w) * k) - x0, (int) Math.ceil((y + h) * k) - y0);
    }

    /** Installs the probe trackers on the top document and returns its URL. */
    public static String prime(WebDriver driver, SettlePolicy policy) {
        try {
            return String.valueOf(probe(driver, policy).get("url"));
        } catch (WebDriverException e) {
            return "";
        }
    }

    /** One evaluation of the engine's await conditions; empty list = settled. */
    static List<String> check(WebDriver driver, SettlePolicy policy, SettlePolicy.Rule r, String urlBefore,
                              long elapsed, String[] urlOut) {
        Map<String, Object> p;
        try {
            p = probe(driver, policy);
        } catch (WebDriverException navigating) {
            p = null;
        }
        List<String> unmet = new ArrayList<>();
        String url = p == null ? "" : String.valueOf(p.get("url"));
        urlOut[0] = url;
        if (p == null) {
            unmet.add("probe");
            return unmet;
        }
        boolean idle = r.network() == SettlePolicy.Network.IDLE;
        if (!"complete".equals(p.get("ready")) && idle) unmet.add("readyState");
        if (idle && (num(p.get("pending")) > 0 || Math.min(num(p.get("netIdleMs")), elapsed) < r.networkIdleMs())) unmet.add("network");
        if (r.domStableMs() > 0 && Math.min(num(p.get("domQuietMs")), elapsed) < r.domStableMs()) unmet.add("dom");
        if (r.animations() && num(p.get("animations")) > 0) unmet.add("animations");
        if (r.spinners() && num(p.get("spinners")) > 0) unmet.add("spinners");
        if (r.url() == SettlePolicy.Url.CHANGED && url.equals(urlBefore)) unmet.add("url-changed");
        if (r.url() == SettlePolicy.Url.SAME && urlBefore != null && !urlBefore.isEmpty() && !url.equals(urlBefore)) unmet.add("url-same");
        return unmet;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> probe(WebDriver driver, SettlePolicy policy) {
        driver.switchTo().defaultContent();
        Map<String, Object> p = (Map<String, Object>) ((JavascriptExecutor) driver).executeScript(PROBE_JS, policy.spinnerSelectors());
        if (!"all".equalsIgnoreCase(prop(NETWORK_SCOPE_PROPERTY, "page"))) {
            // r3: background traffic the page did not start (browser, extensions, proxies) must not count
            Map<String, Object> n = (Map<String, Object>) ((JavascriptExecutor) driver).executeScript(NETWORK_JS, Map.of(
                    "scope", "page", "ignore", csv(NETWORK_IGNORE_PROPERTY), "allow", csv(NETWORK_ALLOW_PROPERTY)));
            p = new java.util.HashMap<>(p);
            p.put("pending", n.get("pending"));
            p.put("netIdleMs", n.get("netIdleMs"));
        }
        return p;
    }

    /** r3 SPEC: System property first, then the framework's properties files (PropertyReader). */
    static String prop(String key, String def) {
        String v = System.getProperty(key);
        if (v == null || v.isBlank()) {
            try {
                v = project.utils.dataReader.PropertyReader.getProperty(key);
            } catch (RuntimeException | LinkageError unavailable) {
                v = null;
            }
        }
        return v == null || v.isBlank() ? def : v.trim();
    }

    /** Comma list; a {@code /regex/} entry (r3 SPEC spelling) is passed to settle-network.js as {@code re:regex}. */
    static List<String> csv(String key) {
        String v = prop(key, "");
        return v.isBlank() ? List.of() : java.util.Arrays.stream(v.split(",")).map(String::trim).filter(x -> !x.isEmpty())
                .map(x -> x.length() > 2 && x.startsWith("/") && x.endsWith("/") ? "re:" + x.substring(1, x.length() - 1) : x)
                .toList();
    }

    static String signature(WebDriver driver) {
        driver.switchTo().defaultContent();
        return String.valueOf(((JavascriptExecutor) driver).executeScript(SIGNATURE_JS));
    }

    private static double num(Object o) {
        return o instanceof Number n ? n.doubleValue() : 0;
    }

    private static void report(String action, SettlePolicy.Rule rule, Result r) {
        StringBuilder msg = new StringBuilder("settle ").append(action).append(": ")
                .append(r.settled() ? "settled" : "NOT settled, unmet " + r.unmet())
                .append(" in ").append(r.elapsedMs()).append("ms");
        if (r.effect() != EffectOutcome.NOT_CHECKED) {
            msg.append(", effect ").append(r.effect());
            if (!r.effectSignal().isEmpty()) {
                msg.append(" (").append(r.effectSignal()).append(')');
            }
        }
        boolean noEffectRequired = r.effect() == EffectOutcome.NO_EFFECT && rule.effect() == SettlePolicy.Effect.REQUIRED;
        if (!r.settled() || noEffectRequired) {
            LogsManager.warn(msg + (noEffectRequired ? " - action had no observable effect (A8 effect=required"
                    + (noEffectWarnOnly() ? ", -D" + NOEFFECT_PROPERTY + "=warn)" : ", failing)") : ""));
        } else {
            LogsManager.info(msg.toString());
        }
        try {
            Allure.step(msg.toString());
        } catch (RuntimeException | LinkageError noAllureContext) {
            // reporting must never break replay
        }
    }

    private static String resource(String path) {
        try (InputStream in = Settle.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("missing " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
