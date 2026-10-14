package delivery.effect;

import com.sun.net.httpserver.HttpServer;
import delivery.snapshot.KeelTarget;
import delivery.snapshot.PageSerializer;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.List;

import static org.testng.Assert.*;

/** A7 on fixtures/a7/effect.html (headless Chrome). A volatile clock ticks every 50 ms throughout. */
public class ActionEffectLiveTest {
    private HttpServer server;
    private WebDriver driver;
    private String base;

    @BeforeClass public void start() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", ex -> {
                try (InputStream in = getClass().getResourceAsStream("/fixtures/a7" + ex.getRequestURI().getPath())) {
                    if (in == null) { ex.sendResponseHeaders(404, -1); ex.close(); return; }
                    byte[] b = in.readAllBytes();
                    ex.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
                    ex.sendResponseHeaders(200, b.length);
                    ex.getResponseBody().write(b);
                }
                ex.close();
            });
            server.start();
            base = "http://127.0.0.1:" + server.getAddress().getPort() + "/";
            ChromeOptions o = new ChromeOptions();
            o.addArguments("--headless=new", "--no-sandbox", "--disable-gpu", "--window-size=1000,700");
            driver = new ChromeDriver(o);
        } catch (Exception e) {
            throw new SkipException("Chrome unavailable: " + e.getMessage());
        }
        PageSerializer.installClosedShadowHook(driver);
    }

    @AfterClass(alwaysRun = true) public void stop() {
        if (driver != null) driver.quit();
        if (server != null) server.stop(0);
    }

    private ActionEffectChecker.Result click(String id, ActionEffectChecker.Config cfg) {
        driver.get(base + "effect.html");
        return ActionEffectChecker.run(driver, KeelTarget.parse("css=#" + id),
                () -> driver.findElement(By.id(id)).click(), cfg);
    }

    private ActionEffectChecker.Result click(String id, ActionEffectChecker.Config cfg,
                                             delivery.settle.SettlePolicy.SideEffects se, delivery.guard.LoopGuard g) {
        driver.get(base + "effect.html");
        return ActionEffectChecker.run(driver, KeelTarget.parse("css=#" + id),
                () -> driver.findElement(By.id(id)).click(), cfg, se, g);
    }

    private static ActionEffectChecker.Config fast() {
        return ActionEffectChecker.Config.defaults().withSettle(400);
    }

    @Test public void changingButtonIsDomEffect() {
        var r = click("change", fast());
        assertEquals(r.outcome(), EffectOutcome.EFFECT_DOM, r.toString());
        assertTrue(r.changed().contains("dom"), r.toString());
    }

    @Test public void noopButtonIsNoEffectDespiteTickingClock() {
        var r = click("noop", fast());
        assertEquals(r.outcome(), EffectOutcome.NO_EFFECT, r.toString());
        assertFalse(r.outcome().isEvidence(), "NO_EFFECT is never PASS evidence");
        assertTrue(r.elapsedMs() >= 400, "waited the full settle window");
    }

    @Test public void canvasOnlyChangeIsVisualEffect() {
        var r = click("paint", fast());
        assertEquals(r.outcome(), EffectOutcome.EFFECT_VISUAL, r.toString());
        var noVisual = click("paint", fast().withVisualFallback(false));
        assertEquals(noVisual.outcome(), EffectOutcome.NO_EFFECT, "without the screenshot tier canvas is invisible");
    }

    @Test public void unmaskedClockWouldFakeAnEffect() {
        var r = click("noop", fast().withMasks(List.of()));
        assertNotEquals(r.outcome(), EffectOutcome.NO_EFFECT, "volatile region must be masked: " + r);
    }

    @Test public void delayedChangeNeedsSettleWindow() {
        var shortWait = click("delayed", ActionEffectChecker.Config.defaults().withSettle(200));
        assertEquals(shortWait.outcome(), EffectOutcome.NO_EFFECT, shortWait.toString());
        var r = click("delayed", ActionEffectChecker.Config.defaults().withSettle(1500));
        assertEquals(r.outcome(), EffectOutcome.EFFECT_DOM, r.toString());
        assertTrue(r.elapsedMs() >= 600, r.toString());
    }

    @Test public void targetStateAttributeChange() {
        var r = click("toggle", fast());
        assertEquals(r.outcome(), EffectOutcome.EFFECT_DOM);
        assertTrue(r.changed().contains("target"), r.toString());
    }

    @Test public void retryRunsTheActionAgainOnlyWhenReadOnly() {
        var N = delivery.settle.SettlePolicy.SideEffects.NONE;
        var once = click("flaky", fast());
        assertEquals(once.outcome(), EffectOutcome.NO_EFFECT);
        var retried = click("flaky", fast().withRetries(1), N, null);
        assertEquals(retried.outcome(), EffectOutcome.EFFECT_DOM, retried.toString());
        assertEquals(retried.attempts(), 2);
    }

    @Test public void retriesDropToZeroUnlessSideEffectsNone() {
        for (var se : new delivery.settle.SettlePolicy.SideEffects[]{
                delivery.settle.SettlePolicy.SideEffects.MUTATING, delivery.settle.SettlePolicy.SideEffects.UNKNOWN, null}) {
            var r = click("flaky", fast().withRetries(3), se, null);
            assertEquals(r.outcome(), EffectOutcome.NO_EFFECT, String.valueOf(se));
            assertEquals(r.attempts(), 1, String.valueOf(se));
        }
        var legacy = click("flaky", fast().withRetries(3));
        assertEquals(legacy.attempts(), 1, "legacy overload = unknown = no retries");
    }

    @Test public void guardStopsReadOnlyRetries() {
        var limits = delivery.guard.LoopGuard.builtin(delivery.guard.LoopGuard.Loop.PRECISION);
        var tight = new delivery.guard.LoopGuard.Limits(1, limits.maxWallMs(), limits.maxModelCalls(), limits.maxCostUnits(),
                limits.warnRepeatAction(), limits.stopRepeatAction(), limits.warnCycle(), limits.stopCycle(),
                limits.warnFailureStreak(), limits.stopFailureStreak(), limits.stopNoProgress());
        var g = new delivery.guard.LoopGuard(delivery.guard.LoopGuard.Loop.PRECISION, tight, System::currentTimeMillis);
        var r = click("flaky", fast().withRetries(3), delivery.settle.SettlePolicy.SideEffects.NONE, g);
        assertEquals(r.attempts(), 1, r.toString());
        assertEquals(r.outcome(), EffectOutcome.NO_EFFECT);
    }
}
