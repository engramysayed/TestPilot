package delivery.codegen;

import com.sun.net.httpserver.HttpServer;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.lang.reflect.InvocationTargetException;
import java.net.InetSocketAddress;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Codegen r1/07 NO_EFFECT handling, live Chrome: visual effect signal (8x8 blocks, luma &gt; 8/255, one
 * block), [data-keel-volatile] masking (+1 block slack, also pruned from the DOM signature), target-box
 * mask, default fail vs -Dkeel.noeffect=warn.
 */
public class SettleVisualEffectLiveTest {
    static final String PAGE = """
            <!doctype html><html><head><style>
              body { margin: 0; font: 14px sans-serif; background: #fff; }
              button { display: block; margin: 8px; width: 120px; height: 32px; background: #ddd; border: 0; }
              #focusy:focus { outline: 6px solid #f00; background: #ff0; }
              #focusy:active { background: #0f0; }
            </style></head><body>
            <button id=paint onclick="const c=document.getElementById('art').getContext('2d');c.fillStyle='#000';c.fillRect(0,0,40,40)">paint</button>
            <button id=focusy>focus-only</button>
            <button id=volatileOnly onclick="document.getElementById('ticker').textContent='tick '+Date.now()">volatile-only</button>
            <canvas id=art width=120 height=60 style="display:block;margin:8px;background:#fff"></canvas>
            <canvas id=clock data-keel-volatile width=60 height=30 style="display:block;margin:8px"></canvas>
            <span id=ticker data-keel-volatile>tick 0</span>
            <script>
              const k = document.getElementById('clock').getContext('2d'); let n = 0;
              setInterval(() => { n++; k.fillStyle = n % 2 ? '#000' : '#fff'; k.fillRect(0, 0, 60, 30); }, 50);
            </script>
            </body></html>
            """;
    private HttpServer server;
    private String base;
    private URLClassLoader loader;
    private Class<?> settle;
    private Object policy;
    protected WebDriver driver;

    @BeforeClass
    public void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            byte[] b = PAGE.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            ex.sendResponseHeaders(200, b.length);
            ex.getResponseBody().write(b);
            ex.close();
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort() + "/page";
        Path classes = Files.createTempDirectory("settle-visual");
        Path pkg = Path.of("customer-framework-template/src/main/java/project/utils/settle");
        loader = CustomerTemplateCompiler.compile(classes, pkg.resolve("SettlePolicy.java"), pkg.resolve("Settle.java"),
                pkg.resolve("ScreenshotDiff.java"), pkg.resolve("NoEffectError.java"));
        Files.createDirectories(classes.resolve("keel"));
        Files.copy(SettlePolicyParityTest.PROBE, classes.resolve("keel/settle-probe.js"));
        Files.copy(SettlePolicyParityTest.NETWORK, classes.resolve("keel/settle-network.js"));
        Files.copy(SettlePolicyParityTest.TABLE, classes.resolve("keel/settle-policy.json"));
        settle = loader.loadClass("project.utils.settle.Settle");
        policy = loader.loadClass("project.utils.settle.SettlePolicy").getMethod("builtin").invoke(null);
        try {
            ChromeOptions o = new ChromeOptions();
            o.addArguments("--headless=new", "--no-sandbox", "--disable-gpu", "--force-device-scale-factor=1");
            driver = new ChromeDriver(o);
            driver.manage().window().setSize(new Dimension(800, 600));
        } catch (RuntimeException e) {
            throw new SkipException("Chrome unavailable: " + e.getMessage());
        }
    }

    /** Hook: background traffic injected after load (none here; see SettleVisualEffectNoiseLiveTest). */
    protected void noise() {
    }

    @AfterMethod(alwaysRun = true)
    public void clearOptOut() {
        System.clearProperty("keel.noeffect");
        System.clearProperty("keel.settle.network.scope");
        System.clearProperty("keel.settle.network.effect");
    }

    @AfterClass(alwaysRun = true)
    public void stop() throws Exception {
        if (driver != null) driver.quit();
        if (server != null) server.stop(0);
        if (loader != null) loader.close();
    }

    /** Clicks {@code id}; masks its box only when {@code maskTarget}. Returns Result.toString(). */
    protected String click(String id, boolean maskTarget, String prep) {
        driver.get(base);
        noise();
        if (prep != null) {
            ((JavascriptExecutor) driver).executeScript(prep);
        }
        By target = By.id(id);
        try {
            return String.valueOf(settle.getMethod("after", WebDriver.class, policy.getClass(), String.class, By.class, Runnable.class)
                    .invoke(null, driver, policy, "click", maskTarget ? target : null, (Runnable) () -> driver.findElement(target).click()));
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof Error err) throw err;
            throw (RuntimeException) e.getCause();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    public void canvasOnlyChangePassesOnTheVisualSignal() {
        String r = click("paint", true, null);   // pixels only: no DOM, URL or network change; volatile clock ticking
        Assert.assertTrue(r.contains("effect=EFFECT, effectSignal=visual"), r);
    }

    @Test
    public void deadClickFailsByDefaultDespiteFocusStyleAndVolatileClock() {
        String r;
        try {
            r = click("focusy", true, null);
        } catch (AssertionError e) {
            Assert.assertEquals(e.getClass().getName(), "project.utils.settle.NoEffectError", String.valueOf(e));
            return;
        }
        Assert.fail("expected NoEffectError, got " + r);
    }

    @Test
    public void deadClickWarnsWithOptOut() {
        System.setProperty("keel.noeffect", "warn");
        String r = click("focusy", true, null);
        Assert.assertTrue(r.contains("effect=NO_EFFECT"), r);
        Assert.assertTrue(r.startsWith("Result[settled=true"), r);
    }

    @Test
    public void targetBoxMaskIsWhatHidesTheFocusRing() {
        System.setProperty("keel.noeffect", "warn");
        String r = click("focusy", false, null);   // same dead click, target not masked: its own :focus paint counts
        Assert.assertTrue(r.contains("effect=EFFECT, effectSignal=visual"), r);
    }

    @Test
    public void changeInsideVolatileRegionIsNoEffect() {
        System.setProperty("keel.noeffect", "warn");
        String r = click("volatileOnly", true, null);   // DOM change only inside [data-keel-volatile]
        Assert.assertTrue(r.contains("effect=NO_EFFECT"), r);
    }

    @Test
    public void volatileMaskIsWhatHidesTheTickingClock() {
        System.setProperty("keel.noeffect", "warn");
        String r = click("focusy", true, "document.getElementById('clock').removeAttribute('data-keel-volatile')");
        Assert.assertTrue(r.contains("effect=EFFECT, effectSignal=visual"), r);   // unmasked clock = false effect
    }
}
