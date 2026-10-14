package delivery.job;

import parsingLayer.HtmlSlimmer;
import delivery.codegen.ProvenStep;
import delivery.effect.ActionEffectChecker;
import delivery.effect.EffectOutcome;
import delivery.evidence.BindFailureEvidence;
import delivery.ir.SecretStep;
import delivery.settle.SettlePolicy;
import delivery.settle.SideEffectClassifier;
import delivery.snapshot.KeelTarget;
import delivery.snapshot.KeelTargetResolver;
import delivery.snapshot.LocatorRanker;
import delivery.snapshot.PageModel;
import delivery.snapshot.PageSerializer;
import delivery.snapshot.RankedLocators;
import delivery.snapshot.SnapshotCandidate;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import utils.LogsManager;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Wiring of A2 (serializer v2 + closed-shadow hook), A3 (locator ranker), A7 (effect check) and A9
 * (bind-failure evidence) into ProvePhase / TcExecutionService / WebDriverFactory. Every flag defaults to
 * off, and with all flags off every method here is a no-op that returns its input (behaviour unchanged).
 *
 * <pre>
 * delivery.snapshot.serializer      = v1 | v2        (v2: hook in the driver + page model in the authoring HTML)
 * delivery.locator.ranker           = off | on       (re-rank each proven locator; adopt a better plain-css one)
 * delivery.effect.check             = off | log | strict (A7 + A8 table; strict fails a required NO_EFFECT)
 * delivery.evidence.bind-failure    = off | on       (A9 bundle when an intent finally fails to bind)
 * </pre>
 * Flags are read per call (System property, then application.properties), so tests can flip them.
 */
public final class ProveWiring {
    public static final String SERIALIZER = "delivery.snapshot.serializer";
    public static final String RANKER = "delivery.locator.ranker";
    public static final String EFFECT = "delivery.effect.check";
    public static final String BIND_EVIDENCE = "delivery.evidence.bind-failure";

    public enum EffectMode { OFF, LOG, STRICT }

    private ProveWiring() {}

    public static boolean serializerV2() { return "v2".equalsIgnoreCase(prop(SERIALIZER, "v1")); }
    public static boolean rankerOn() { return on(prop(RANKER, "off")); }
    public static boolean bindEvidenceOn() { return on(prop(BIND_EVIDENCE, "off")); }
    public static EffectMode effectMode() {
        String v = prop(EFFECT, "off").toLowerCase(Locale.ROOT);
        return switch (v) { case "log" -> EffectMode.LOG; case "strict" -> EffectMode.STRICT; case "on", "true" -> EffectMode.LOG; default -> EffectMode.OFF; };
    }

    // ---------------------------------------------------------------- A2: driver hook + authoring html

    /** Called by WebDriverFactory on the raw driver before the first navigation. */
    public static void onDriverCreated(WebDriver raw) {
        if (!serializerV2() || raw == null) return;
        boolean ok = PageSerializer.installClosedShadowHook(raw);
        LogsManager.info("KEEL_WIRING serializer=v2 closedShadowHook=" + ok);
    }

    /** v1: slimmed DOM (unchanged). v2: same, plus the frame/shadow-aware page model as an HTML comment. */
    public static String authoringHtml(WebDriver driver, String intentText, int maxChars) {
        String v1 = HtmlSlimmer.slim(PageSnapshot.html(driver), maxChars);
        if (!serializerV2()) return v1;
        try {
            PageModel m = PageSerializer.serialize(driver, keywords(intentText), List.of());
            driver.switchTo().defaultContent();
            String model = m.renderBudgeted(Math.max(2000, maxChars / 5)).replace("--", "- -");
            return v1 + "\n<!-- keel-page-model v2 (frames=" + m.frames() + " closedShadowHook=" + m.closedShadowHook()
                    + "; [eN] target | role | name ...)\n" + model + "\n-->";
        } catch (RuntimeException e) {
            LogsManager.warn("KEEL_WIRING serializer=v2 fell back to v1: " + e.getClass().getSimpleName());
            try { driver.switchTo().defaultContent(); } catch (RuntimeException ignored) { }
            return v1;
        }
    }

    // ---------------------------------------------------------------- A3: ranker

    /**
     * Before a step runs (its element is on the page): rank the element's locators and, when the best
     * verified one is plain css and scores above the step's own, adopt it. Frame/shadow targets are left
     * alone (the v1 executor cannot express them). Returns the step unchanged when off or on any doubt.
     */
    public static ProvenStep rerank(WebDriver driver, ProvenStep step, By locator) {
        if (!rankerOn() || locator == null || step == null || isNavigate(step)) return step;
        try {
            List<WebElement> found = driver.findElements(locator);
            if (found.size() != 1) return step;
            WebElement el = found.get(0);
            PageModel m = PageSerializer.serialize(driver, List.of(), List.of());
            driver.switchTo().defaultContent();
            for (SnapshotCandidate c : m.candidates()) {
                if (!c.framePath().isEmpty() || !c.shadowPath().isEmpty()) continue;
                WebElement e;
                try { e = KeelTargetResolver.find(driver, c.target()); } catch (RuntimeException x) { continue; }
                finally { KeelTargetResolver.reset(driver); }
                if (!el.equals(e)) continue;
                Optional<RankedLocators> r = LocatorRanker.rank(driver, c, List.of(), "");
                if (r.isEmpty()) return step;
                var main = r.get().main();
                KeelTarget t = main.target();
                double current = currentScore(step);
                if (!t.isPlainCss() || main.score() <= current) return step;
                LogsManager.info("KEEL_RANKER " + step.locatorStrategy() + "=" + step.locatorValue() + " -> css=" + t.terminalCss()
                        + " tier=" + main.tier() + " score=" + main.score());
                return new ProvenStep(step.tcId(), step.pageName(), step.actionType(), step.action(), "css", t.terminalCss(),
                        step.value(), step.assertionType(), step.assertionExpected(), step.validated(),
                        (step.rationale() + " [ranker " + main.tier() + " " + main.score() + "; was " + step.locatorStrategy() + "="
                                + step.locatorValue() + "]").trim(), step.screenshotRelPath(),
                        // ranked main replaces the locator: old candidates are stale; keep drop target, sideEffects, secret
                        java.util.List.of(), step.dropCandidates(), step.sideEffects(), step.secret());
            }
            return step;
        } catch (RuntimeException e) {
            LogsManager.warn("KEEL_RANKER skipped: " + e.getClass().getSimpleName());
            return step;
        } finally {
            try { driver.switchTo().defaultContent(); } catch (RuntimeException ignored) { }
        }
    }

    /** Rough A3 score of a v1 locator so the ranker only replaces when it is clearly better. */
    static double currentScore(ProvenStep s) {
        String st = s.locatorStrategy() == null ? "" : s.locatorStrategy().toLowerCase(Locale.ROOT);
        String v = s.locatorValue() == null ? "" : s.locatorValue();
        if (v.matches("(?s).*\\[(data-testid|data-test|data-qa|data-cy)=.*")) return LocatorRanker.TEST_ID;
        if (st.equals("id")) return v.matches(".*\\d{3,}.*") ? LocatorRanker.CSS_ID_DYNAMIC : LocatorRanker.CSS_ID;
        if (st.equals("name")) return LocatorRanker.LABEL;
        if (st.equals("xpath") && v.matches(".*\\[\\d+\\].*")) return LocatorRanker.POSITION;
        if (st.equals("xpath")) return LocatorRanker.CSS_CLASS;
        if (st.equals("css") && v.contains(":nth-")) return LocatorRanker.POSITION;
        return LocatorRanker.CSS_CLASS;
    }

    // ---------------------------------------------------------------- A7 + A8: effect check

    /** Result of running one action through the effect check. failure != null => fail the step. */
    public record Effect(String result, String failure, EffectOutcome outcome) {}

    /**
     * Runs {@code action} (which returns the executor's result string). Off: runs it and returns. On: the
     * A8 rule for the action kind decides whether an effect is expected and how long to look; retries go
     * through the A10 gate (0 unless sideEffects is none). Strict fails a required NO_EFFECT.
     */
    public static Effect runWithEffect(WebDriver driver, ProvenStep step, By locator, java.util.function.Supplier<String> action) {
        EffectMode mode = effectMode();
        if (mode == EffectMode.OFF) return new Effect(action.get(), null, null);
        SettlePolicy.Action kind = kindOf(step);
        SettlePolicy.Rule rule = kind == null ? null : SettlePolicy.builtin().rule(SecretStep.settleKind(kind, step.secret()));
        if (rule == null || rule.effect() == SettlePolicy.Effect.NONE) return new Effect(action.get(), null, null);
        KeelTarget target = plainTarget(step);
        // r3: the step's sideEffects (filled by SideEffectWiring from the bound element) when known
        SettlePolicy.SideEffects se = SideEffectWiring.unknown(step.sideEffects())
                ? SideEffectClassifier.classify(SettlePolicy.builtin(), kind, null) : SettlePolicy.SideEffects.of(step.sideEffects());
        String[] holder = new String[1];
        ActionEffectChecker.Config cfg = ActionEffectChecker.Config.defaults().withSettle(rule.effectWaitMs());
        ActionEffectChecker.Result r = ActionEffectChecker.run(driver, target, () -> holder[0] = action.get(), cfg, se, null);
        String result = holder[0];
        LogsManager.info("KEEL_EFFECT action=" + step.action() + " outcome=" + r.outcome() + " expect=" + rule.effect()
                + " " + r.detail());
        if (result != null && result.startsWith("false")) return new Effect(result, null, r.outcome());
        boolean fail = mode == EffectMode.STRICT && rule.effect() == SettlePolicy.Effect.REQUIRED && r.outcome() == EffectOutcome.NO_EFFECT;
        return new Effect(result, fail ? "EFFECT_NONE: no observable effect after " + step.action() + " (" + r.detail() + ")" : null, r.outcome());
    }

    static SettlePolicy.Action kindOf(ProvenStep s) {
        String a = s.action() == null ? "" : s.action().trim();
        for (SettlePolicy.Action k : SettlePolicy.Action.values()) if (k.key.equalsIgnoreCase(a)) return k;
        if (a.equalsIgnoreCase("doubleclick")) return SettlePolicy.Action.DOUBLE_CLICK;
        return null;
    }

    static KeelTarget plainTarget(ProvenStep s) {
        String st = s.locatorStrategy() == null ? "" : s.locatorStrategy().toLowerCase(Locale.ROOT);
        String v = s.locatorValue() == null ? "" : s.locatorValue();
        try {
            if (st.equals("css") && !v.isBlank()) return KeelTarget.parse("css=" + v);
            if (st.equals("id") && !v.isBlank()) return KeelTarget.parse("css=[id=\"" + v.replace("\"", "\\\"") + "\"]");
        } catch (RuntimeException ignored) { }
        return null;
    }

    // ---------------------------------------------------------------- A9: bind-failure evidence

    public static Optional<Path> bindFailure(WebDriver driver, Path evidenceRoot, String tcId, int intentIndex,
                                             String intentText, String kind, String testData, String reason) {
        if (!bindEvidenceOn() || driver == null || evidenceRoot == null) return Optional.empty();
        boolean secret = "TYPE_PASS".equals(kind) || SecretStep.isSecretValue(testData, List.of());
        String action = kind == null ? null : kind.startsWith("TYPE") ? "type" : kind.toLowerCase(Locale.ROOT);
        Optional<Path> p = BindFailureEvidence.capture(driver, new BindFailureEvidence.Request(evidenceRoot.resolve(safe(tcId)),
                tcId + "/" + (intentIndex + 1), intentText, keywords(intentText), null, reason, List.of(), action,
                null, secret, testData));
        p.ifPresent(d -> LogsManager.info("KEEL_BIND_EVIDENCE " + d));
        return p;
    }

    // ---------------------------------------------------------------- helpers

    static List<String> keywords(String text) {
        List<String> out = new ArrayList<>();
        if (text == null) return out;
        for (String w : text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+"))
            if (w.length() >= 3 && !List.of("the", "and", "click", "into", "type", "enter", "select", "button", "field", "with", "verify").contains(w))
                out.add(w);
        return out;
    }

    private static boolean isNavigate(ProvenStep s) { return "navigate".equalsIgnoreCase(s.action()); }

    private static String safe(String s) { return s == null ? "tc" : s.replaceAll("[^A-Za-z0-9._-]", "_"); }

    private static boolean on(String v) { return "on".equalsIgnoreCase(v) || "true".equalsIgnoreCase(v) || "1".equals(v); }

    static String prop(String key, String def) {
        String v = System.getProperty(key);
        if (v == null || v.isBlank()) {
            try { v = utils.PropertyReader.getProperty(key); } catch (RuntimeException ignored) { v = null; }
        }
        return v == null || v.isBlank() ? def : v.trim();
    }
}
