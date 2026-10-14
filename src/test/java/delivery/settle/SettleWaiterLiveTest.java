package delivery.settle;

import com.sun.net.httpserver.HttpServer;
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
import java.util.concurrent.Executors;

import static org.testng.Assert.*;

public class SettleWaiterLiveTest {
    private HttpServer server;
    protected WebDriver driver;
    private String base;
    private final SettlePolicy policy = SettlePolicy.builtin();

    @BeforeClass public void start() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(Executors.newFixedThreadPool(4));
            server.createContext("/", ex -> {
                String p = ex.getRequestURI().getPath();
                byte[] b;
                if (p.equals("/slow")) {
                    try { Thread.sleep(700); } catch (InterruptedException ignored) { }
                    b = "slow-done".getBytes();
                } else {
                    try (InputStream in = getClass().getResourceAsStream("/fixtures/a8/settle.html")) { b = in.readAllBytes(); }
                }
                ex.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
                ex.sendResponseHeaders(200, b.length);
                ex.getResponseBody().write(b);
                ex.close();
            });
            server.start();
            base = "http://127.0.0.1:" + server.getAddress().getPort() + "/";
            ChromeOptions o = new ChromeOptions();
            o.addArguments("--headless=new", "--no-sandbox", "--disable-gpu");
            driver = new ChromeDriver(o);
        } catch (Exception e) {
            throw new SkipException("Chrome unavailable: " + e.getMessage());
        }
    }

    @AfterClass(alwaysRun = true) public void stop() {
        if (driver != null) driver.quit();
        if (server != null) server.stop(0);
    }

    protected SettleWaiter.Report click(String id, SettlePolicy.Action a) {
        driver.get(base + "settle.html");
        noise();
        String before = SettleWaiter.prime(driver, policy);
        try { Thread.sleep(600); } catch (InterruptedException ignored) { } // page-load quiet
        before = SettleWaiter.prime(driver, policy);
        driver.findElement(By.id(id)).click();
        return SettleWaiter.await(driver, policy, a, before);
    }

    /** Hook: background traffic to inject after load (none here; see SettleWaiterNoiseLiveTest). */
    protected void noise() {
    }

    @Test public void quietClickSettlesFast() {
        var r = click("nothing", SettlePolicy.Action.CLICK);
        assertTrue(r.settled(), r.toString());
        assertTrue(r.elapsedMs() < 1500, r.toString());
    }

    @Test public void waitsForSpinnerToDisappear() {
        var r = click("spin", SettlePolicy.Action.CLICK);
        assertTrue(r.settled(), r.toString());
        assertTrue(r.elapsedMs() >= 550, r.toString());
    }

    @Test public void waitsForAnimationEnd() {
        var r = click("anim", SettlePolicy.Action.CLICK);
        assertTrue(r.settled(), r.toString());
        assertTrue(r.elapsedMs() >= 500, r.toString());
    }

    @Test public void waitsForNetworkIdle() {
        var r = click("fetch", SettlePolicy.Action.CLICK);
        assertTrue(r.settled(), r.toString());
        assertTrue(r.elapsedMs() >= 700, r.toString());
        assertEquals(driver.findElement(By.id("out")).getText(), "slow-done");
    }

    @Test public void waitsForDomStableWindow() {
        var r = click("mutate", SettlePolicy.Action.CLICK);
        assertTrue(r.settled(), r.toString());
        assertEquals(driver.findElement(By.id("out")).getText(), "m10");
    }

    @Test public void urlExpectations() {
        var nav = click("push", SettlePolicy.Action.NAVIGATE);
        assertTrue(nav.settled(), "url changed: " + nav);
        var noNav = click("nothing", SettlePolicy.Action.NAVIGATE);
        assertFalse(noNav.settled());
        assertEquals(noNav.unmet(), java.util.List.of("url-changed"));
        var typed = click("push", SettlePolicy.Action.TYPE);
        assertFalse(typed.settled(), "type must not navigate");
        assertEquals(typed.unmet(), java.util.List.of("url-same"));
        assertTrue(typed.elapsedMs() < 1000, "url-same violation reported immediately");
    }
}
