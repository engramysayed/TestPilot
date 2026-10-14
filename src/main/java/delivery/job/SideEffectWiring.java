package delivery.job;

import delivery.codegen.ProvenStep;
import delivery.settle.SettlePolicy;
import delivery.settle.SideEffectClassifier;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.util.List;
import java.util.Map;

/**
 * r3: fills IR {@code sideEffects} on the steps ProvePhase records, from {@link SideEffectClassifier}
 * (A8 table default refined by the bound element). Only a KNOWN result (none | mutating) is written and only
 * over "unknown"/absent: a value the step already carries (IR, ranker, upstream) is never overwritten.
 * Assertion-only steps are left alone. Off switch: -Ddelivery.sideeffects.classify=false.
 */
public final class SideEffectWiring {
    private SideEffectWiring() {}

    static final String TARGET_JS = """
            var e=arguments[0]; if(!e) return null; var f=e.closest?e.closest('form'):null;
            var n=(e.getAttribute('aria-label')||e.innerText||e.value||e.getAttribute('title')||e.getAttribute('name')||'');
            return {tag:e.tagName.toLowerCase(), role:e.getAttribute('role'), type:e.getAttribute('type'),
              name:String(n).trim().slice(0,200), href:e.getAttribute('href'),
              popup:!!e.getAttribute('aria-haspopup') && e.getAttribute('aria-haspopup')!=='false',
              inForm:!!f, method:f?(f.getAttribute('method')||'get'):null};""";

    public static boolean enabled() {
        return !"false".equalsIgnoreCase(System.getProperty("delivery.sideeffects.classify", "true"));
    }

    static boolean unknown(String v) {
        return v == null || v.isBlank() || ProvenStep.SIDE_EFFECTS_UNKNOWN.equals(v);
    }

    /** Pure rule: the value to record. */
    static String resolve(String current, SettlePolicy.Action kind, SideEffectClassifier.Target t) {
        if (!unknown(current) || kind == null || kind == SettlePolicy.Action.ASSERT) return current;
        SettlePolicy.SideEffects se = SideEffectClassifier.classify(SettlePolicy.builtin(), kind, t);
        return se == SettlePolicy.SideEffects.UNKNOWN ? current : se.wire;
    }

    static SideEffectClassifier.Target targetOf(WebDriver d, By locator) {
        if (d == null || locator == null || !(d instanceof JavascriptExecutor js)) return null;
        try {
            List<WebElement> found = d.findElements(locator);
            if (found.isEmpty()) return null;
            Object o = js.executeScript(TARGET_JS, found.get(0));
            if (!(o instanceof Map<?, ?> m)) return null;
            return new SideEffectClassifier.Target(s(m.get("tag")), s(m.get("role")), s(m.get("type")), s(m.get("name")),
                    s(m.get("href")), Boolean.TRUE.equals(m.get("popup")), Boolean.TRUE.equals(m.get("inForm")), s(m.get("method")));
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Step with sideEffects filled when known (same instance when nothing changes). Read BEFORE the action. */
    public static ProvenStep apply(WebDriver d, ProvenStep step, By locator) {
        if (!enabled() || step == null || !unknown(step.sideEffects())) return step;
        SettlePolicy.Action kind = ProveWiring.kindOf(step);
        if (kind == null || kind == SettlePolicy.Action.ASSERT) return step;
        // navigate/wait/etc. are decided by the table alone; only element actions need the DOM
        SideEffectClassifier.Target t = locator == null ? null : targetOf(d, locator);
        String v = resolve(step.sideEffects(), kind, t);
        return v == null || v.equals(step.sideEffects()) ? step : step.withSideEffects(v);
    }

    private static String s(Object o) {
        return o == null ? null : o.toString();
    }
}
