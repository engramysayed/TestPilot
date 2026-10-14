package delivery.codegen;

import com.sun.net.httpserver.HttpServer;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.lang.reflect.InvocationTargetException;
import java.net.InetSocketAddress;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Template Settle (A8 policy + probe) in headless Chrome: network idle, spinner, DOM quiet, url rules, effect. */
public class SettleLiveSmokeTest {
    static final String PAGE = """
            <!doctype html><html><body>
            <button id=fetch onclick="fetch('/slow').then(r=>r.text()).then(t=>document.body.insertAdjacentHTML('beforeend','<p id=got>'+t+'</p>'))">fetch</button>
            <button id=spin onclick="const s=document.createElement('div');s.className='spinner';s.textContent='...';document.body.appendChild(s);setTimeout(()=>{s.remove();document.body.insertAdjacentHTML('beforeend','<p id=done>done</p>')},700)">spin</button>
            <button id=dead>dead</button>
            <button id=netonly onclick="fetch('/slow')">netonly</button>
            <a id=nav href="/page?next=1">next</a>
            <button id=hash onclick="location.hash='moved'">hash</button>
            </body></html>
            """;
    private HttpServer server;
    private String base;
    private URLClassLoader loader;
    private Class<?> settle;
    private Object policy;
    private WebDriver driver;

    @BeforeClass
    public void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/slow", ex -> {
            try {
                Thread.sleep(600); // test server latency only (not framework code)
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            byte[] b = "payload".getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, b.length);
            ex.getResponseBody().write(b);
            ex.close();
        });
        server.createContext("/", ex -> {
            byte[] b = PAGE.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            ex.sendResponseHeaders(200, b.length);
            ex.getResponseBody().write(b);
            ex.close();
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort() + "/page";
        Path classes = Files.createTempDirectory("settle-live");
        Path pkg = Path.of("customer-framework-template/src/main/java/project/utils/settle");
        loader = CustomerTemplateCompiler.compile(classes, pkg.resolve("SettlePolicy.java"), pkg.resolve("Settle.java"), pkg.resolve("NoEffectError.java"));
        Files.createDirectories(classes.resolve("keel"));
        Files.copy(SettlePolicyParityTest.PROBE, classes.resolve("keel/settle-probe.js"));
        Files.copy(SettlePolicyParityTest.NETWORK, classes.resolve("keel/settle-network.js"));
        Files.copy(SettlePolicyParityTest.TABLE, classes.resolve("keel/settle-policy.json"));
        settle = loader.loadClass("project.utils.settle.Settle");
        policy = loader.loadClass("project.utils.settle.SettlePolicy").getMethod("builtin").invoke(null);
        try {
            ChromeOptions o = new ChromeOptions();
            o.addArguments("--headless=new", "--no-sandbox", "--disable-gpu");
            driver = new ChromeDriver(o);
        } catch (RuntimeException e) {
            throw new SkipException("Chrome unavailable: " + e.getMessage());
        }
    }

    @AfterClass(alwaysRun = true)
    public void stop() throws Exception {
        if (driver != null) driver.quit();
        if (server != null) server.stop(0);
        if (loader != null) loader.close();
    }

    private String run(String action, String id) {
        driver.get(base);
        try {
            return String.valueOf(settle.getMethod("after", WebDriver.class, policy.getClass(), String.class, Runnable.class)
                    .invoke(null, driver, policy, action, (Runnable) () -> driver.findElement(By.id(id)).click()));
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof Error err) {
                throw err;
            }
            throw (RuntimeException) e.getCause();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static long elapsed(String r) {
        return Long.parseLong(r.replaceAll(".*elapsedMs=(\\d+).*", "$1"));
    }

    @Test
    public void clickWaitsForNetworkIdleAfterFetch() {
        String r = run("click", "fetch");
        Assert.assertTrue(r.startsWith("Result[settled=true"), r);
        Assert.assertTrue(r.contains("effect=EFFECT"), r);
        Assert.assertTrue(driver.findElements(By.id("got")).size() == 1, "response rendered before settle returned: " + r);
        Assert.assertTrue(elapsed(r) >= 600, r);
    }

    @Test
    public void clickWaitsForSpinnerToDisappear() {
        String r = run("click", "spin");
        Assert.assertTrue(r.startsWith("Result[settled=true"), r);
        Assert.assertEquals(driver.findElements(By.id("done")).size(), 1, r);
        Assert.assertTrue(elapsed(r) >= 700, r);
    }

    @Test
    public void deadClickFailsByDefault() {
        AssertionError e = Assert.expectThrows(AssertionError.class, () -> run("click", "dead"));
        Assert.assertEquals(e.getClass().getName(), "project.utils.settle.NoEffectError");
        Assert.assertTrue(e.getMessage().contains("-Dkeel.noeffect=warn"), e.getMessage());
        Assert.assertTrue(e.getMessage().contains("(no DOM, URL or visual change within "), e.getMessage());
    }

    /** r3 SPEC 2: a page request alone is a settle condition, never an EFFECT (default effect=corroborated). */
    @Test
    public void networkAloneIsNotAnEffectByDefault() {
        System.setProperty("keel.noeffect", "warn");
        try {
            String r = run("click", "netonly");
            Assert.assertTrue(r.startsWith("Result[settled=true"), r);
            Assert.assertTrue(r.contains("effect=NO_EFFECT"), r);
            Assert.assertTrue(elapsed(r) >= 600, "still waits for the page's own request: " + r);
        } finally {
            System.clearProperty("keel.noeffect");
        }
        AssertionError e = Assert.expectThrows(AssertionError.class, () -> run("click", "netonly"));
        Assert.assertEquals(e.getClass().getName(), "project.utils.settle.NoEffectError");
    }

    @Test
    public void networkAloneCountsWithEffectAloneOptIn() {
        System.setProperty("keel.settle.network.effect", "alone");
        try {
            String r = run("click", "netonly");
            Assert.assertTrue(r.contains("effect=EFFECT, effectSignal=network"), r);
        } finally {
            System.clearProperty("keel.settle.network.effect");
        }
    }

    @Test
    public void deadClickIsRecordedNoEffectButSettlesWithWarnOptOut() {
        System.setProperty("keel.noeffect", "warn");
        try {
            String r = run("click", "dead");
            Assert.assertTrue(r.startsWith("Result[settled=true"), r);
            Assert.assertTrue(r.contains("effect=NO_EFFECT"), r);
            Assert.assertTrue(elapsed(r) >= 1900 && elapsed(r) < 6000, r);   // effectWaitMs=2000 for click
        } finally {
            System.clearProperty("keel.noeffect");
        }
    }

    @Test
    public void navigateRequiresUrlChange() {
        String r = run("navigate", "nav");
        Assert.assertTrue(r.startsWith("Result[settled=true") && r.contains("next=1") && r.contains("effect=NOT_CHECKED"), r);
    }

    @Test
    public void urlSameViolationStopsImmediatelyAsAFact() {
        String r = run("type", "hash");   // type: url=same
        Assert.assertTrue(r.startsWith("Result[settled=false") && r.contains("unmet=[url-same],"), "only [url-same]: " + r);
        Assert.assertTrue(elapsed(r) < 1000, "r3: url-same is final, reported at once: " + r);
    }
}
