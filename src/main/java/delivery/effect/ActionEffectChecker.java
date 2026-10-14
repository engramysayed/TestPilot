package delivery.effect;

import delivery.snapshot.KeelTarget;
import delivery.snapshot.KeelTargetResolver;
import delivery.snapshot.PageSerializer;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.awt.Rectangle;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * A7: did the action do anything? Before/after signature (URL, focus, PageModel hash, DOM digest, target
 * state); if identical, masked screenshot diff; if still identical after the settle window and retries,
 * {@link EffectOutcome#NO_EFFECT} (never PASS). Signatures are taken in the top-level document.
 */
public final class ActionEffectChecker {
    static final String SIGNATURE_JS = resource("/keel/effect-signature.js");
    static final String MASK_RECTS_JS =
            "var out=[],dpr=window.devicePixelRatio||1;(arguments[0]||[]).forEach(function(s){try{"
            + "document.querySelectorAll(s).forEach(function(e){var r=e.getBoundingClientRect();"
            + "if(r.width>0&&r.height>0)out.push([r.left*dpr,r.top*dpr,r.width*dpr,r.height*dpr]);});}catch(x){}});return out;";

    /**
     * @param settleMs        how long to keep looking for an effect after each attempt (delayed UIs)
     * @param pollMs          poll interval inside the settle window
     * @param retries         extra action attempts when no effect was seen (0 = none; only for idempotent actions)
     * @param visualFallback  compare screenshots when the DOM signature is unchanged
     * @param block           screenshot diff block size (px)
     * @param lumaThreshold   per-block mean luma change that counts (0..255)
     * @param minChangedBlocks changed blocks needed for EFFECT_VISUAL
     * @param maskSelectors   volatile regions (clocks, carousels, ads): ignored by DOM digest and screenshot diff
     * @param pageModel       include the serializer PageModel hash (costlier; DOM digest already covers most)
     */
    public record Config(long settleMs, long pollMs, int retries, boolean visualFallback, int block,
                         double lumaThreshold, int minChangedBlocks, List<String> maskSelectors, boolean pageModel) {
        public static Config defaults() {
            return new Config(1500, 150, 0, true, 8, 8.0, 1, List.of("[data-keel-volatile]"), true);
        }

        public Config withSettle(long ms) { return new Config(ms, pollMs, retries, visualFallback, block, lumaThreshold, minChangedBlocks, maskSelectors, pageModel); }
        public Config withRetries(int n) { return new Config(settleMs, pollMs, n, visualFallback, block, lumaThreshold, minChangedBlocks, maskSelectors, pageModel); }
        public Config withMasks(List<String> m) { return new Config(settleMs, pollMs, retries, visualFallback, block, lumaThreshold, minChangedBlocks, m, pageModel); }
        public Config withVisualFallback(boolean v) { return new Config(settleMs, pollMs, retries, v, block, lumaThreshold, minChangedBlocks, maskSelectors, pageModel); }
    }

    public record Result(EffectOutcome outcome, int attempts, long elapsedMs, List<String> changed, String detail) {
    }

    private ActionEffectChecker() {
    }

    /**
     * Runs {@code action} once and classifies its effect. Unknown side effects are treated as mutating, so
     * {@code cfg.retries()} is ignored here: use the sideEffects overload to allow retries of read-only steps.
     */
    public static Result run(WebDriver driver, KeelTarget target, Runnable action, Config cfg) {
        return run(driver, target, action, cfg, delivery.settle.SettlePolicy.SideEffects.UNKNOWN, null);
    }

    /**
     * Call site of the A10 retry gate: retries = {@link delivery.guard.LoopGuard#safeRetries} (0 unless the
     * step's IR sideEffects is "none"). With a guard, every attempt is recorded and a STOP verdict ends
     * the retries early.
     */
    public static Result run(WebDriver driver, KeelTarget target, Runnable action, Config cfg,
                             delivery.settle.SettlePolicy.SideEffects sideEffects, delivery.guard.LoopGuard guard) {
        int retries = delivery.guard.LoopGuard.safeRetries(sideEffects, cfg.retries());
        if (retries < cfg.retries())
            utils.LogsManager.info("EFFECT_RETRY_GATED: sideEffects=" + (sideEffects == null ? "unknown" : sideEffects.wire)
                    + " configured=" + cfg.retries() + " effective=0");
        return runAttempts(driver, target, action, cfg, retries, sideEffects, guard);
    }

    private static Result runAttempts(WebDriver driver, KeelTarget target, Runnable action, Config cfg, int retries,
                                      delivery.settle.SettlePolicy.SideEffects sideEffects, delivery.guard.LoopGuard guard) {
        long t0 = System.currentTimeMillis();
        try {
            EffectSignature before = signature(driver, target, cfg);
            byte[] beforeShot = cfg.visualFallback() ? shot(driver) : null;
            List<Rectangle> masks = cfg.visualFallback() ? maskRects(driver, cfg) : List.of();
            String lastDetail = "";
            int attempt = 0;
            while (attempt < 1 + retries) {
                attempt++;
                if (attempt > 1 && guard != null) {
                    delivery.guard.LoopGuard.Verdict v = guard.record(new delivery.guard.LoopGuard.Step(
                            before, "effect-retry " + (target == null ? "" : target), false, 0, 0, sideEffects));
                    if (v.stop() || guard.beforeIteration().stop()) { attempt--; break; }
                }
                action.run();
                long deadline = System.currentTimeMillis() + cfg.settleMs();
                do {
                    EffectSignature after = signature(driver, target, cfg);
                    List<String> d = before.diff(after);
                    if (!d.isEmpty()) {
                        return new Result(EffectOutcome.EFFECT_DOM, attempt, System.currentTimeMillis() - t0, d, "dom signature changed: " + d);
                    }
                    if (beforeShot != null) {
                        List<Rectangle> m = new ArrayList<>(masks);
                        m.addAll(maskRects(driver, cfg));
                        m.addAll(targetRect(driver, target)); // focus ring / :active styling of the clicked control
                        byte[] now = shot(driver);
                        m.addAll(maskRects(driver, cfg)); // volatile boxes can grow while the shot is taken
                        ScreenshotDiff.Result r = ScreenshotDiff.compare(beforeShot, now, m, cfg.block(), cfg.lumaThreshold());
                        lastDetail = "screenshot changed blocks " + r.changedBlocks() + "/" + r.totalBlocks();
                        if (r.changedBlocks() >= cfg.minChangedBlocks()) {
                            return new Result(EffectOutcome.EFFECT_VISUAL, attempt, System.currentTimeMillis() - t0, List.of("screenshot"), lastDetail);
                        }
                    }
                    if (System.currentTimeMillis() >= deadline) break;
                    sleep(cfg.pollMs());
                } while (true);
            }
            return new Result(EffectOutcome.NO_EFFECT, attempt, System.currentTimeMillis() - t0, List.of(),
                    "no observable effect within " + cfg.settleMs() + " ms" + (lastDetail.isEmpty() ? "" : "; " + lastDetail));
        } catch (RuntimeException e) {
            return new Result(EffectOutcome.UNKNOWN, 0, System.currentTimeMillis() - t0, List.of(), "effect check failed: " + e);
        }
    }

    @SuppressWarnings("unchecked")
    public static EffectSignature signature(WebDriver driver, KeelTarget target, Config cfg) {
        driver.switchTo().defaultContent();
        WebElement el = null;
        if (target != null) {
            try {
                el = KeelTargetResolver.find(driver, target);
            } catch (RuntimeException gone) {
                el = null; // target vanished: still a valid signature component
            } finally {
                driver.switchTo().defaultContent();
            }
        }
        String pm = "";
        if (cfg.pageModel()) {
            pm = sha(PageSerializer.serialize(driver, List.of(), List.of()).render(1 << 20));
            driver.switchTo().defaultContent();
        }
        Map<String, Object> m = (Map<String, Object>) ((JavascriptExecutor) driver)
                .executeScript(SIGNATURE_JS, el, cfg.maskSelectors());
        String targetState = target == null ? "" : el == null ? "missing" : String.valueOf(m.get("target"));
        return new EffectSignature(String.valueOf(m.get("url")), String.valueOf(m.get("focus")), pm,
                String.valueOf(m.get("digest")), ((Number) m.get("nodes")).intValue(), targetState);
    }

    @SuppressWarnings("unchecked")
    static List<Rectangle> maskRects(WebDriver driver, Config cfg) {
        List<Rectangle> out = new ArrayList<>();
        if (cfg.maskSelectors() == null || cfg.maskSelectors().isEmpty()) return out;
        List<List<Number>> rs = (List<List<Number>>) ((JavascriptExecutor) driver).executeScript(MASK_RECTS_JS, cfg.maskSelectors());
        for (List<Number> r : rs) {
            int pad = Math.max(2, cfg.block()); // one block of slack around volatile regions
            out.add(new Rectangle((int) Math.floor(r.get(0).doubleValue()) - pad, (int) Math.floor(r.get(1).doubleValue()) - pad,
                    (int) Math.ceil(r.get(2).doubleValue()) + 2 * pad, (int) Math.ceil(r.get(3).doubleValue()) + 2 * pad));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    static List<Rectangle> targetRect(WebDriver driver, KeelTarget target) {
        if (target == null || target.hops().size() != 1) return List.of(); // top-level document targets only
        try {
            WebElement el = KeelTargetResolver.find(driver, target);
            List<Number> r = (List<Number>) ((JavascriptExecutor) driver).executeScript(
                    "var d=window.devicePixelRatio||1,r=arguments[0].getBoundingClientRect();return [r.left*d,r.top*d,r.width*d,r.height*d];", el);
            int pad = 4;
            return List.of(new Rectangle((int) r.get(0).doubleValue() - pad, (int) r.get(1).doubleValue() - pad,
                    (int) r.get(2).doubleValue() + 2 * pad, (int) r.get(3).doubleValue() + 2 * pad));
        } catch (RuntimeException e) {
            return List.of();
        } finally {
            driver.switchTo().defaultContent();
        }
    }

    private static byte[] shot(WebDriver driver) {
        return ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(Math.max(1, ms));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted", e);
        }
    }

    static String sha(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String resource(String path) {
        try (InputStream in = ActionEffectChecker.class.getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("missing resource " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
