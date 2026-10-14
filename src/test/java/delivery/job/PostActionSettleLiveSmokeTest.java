package delivery.job;

import org.openqa.selenium.By;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeDriverService;
import org.openqa.selenium.chrome.ChromeOptions;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.Test;
import java.nio.file.Path;
import java.nio.file.Files;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/** Local synthetic browser only; explicit existing chromedriver path, no automatic install. */
public class PostActionSettleLiveSmokeTest {
    @Test public void readyAndDelayedBusyPagesSettleWithoutWeakeningAssertions() throws Exception {
        String driverPath = System.getProperty("keel.performance.chromedriver", "");
        if (driverPath.isBlank()) throw new SkipException("Supply an existing chromedriver path explicitly");
        Assert.assertTrue(Files.isRegularFile(Path.of(driverPath)));
        String oldWait = System.getProperty(PostActionSettle.PROPERTY);
        String oldAdaptive = System.getProperty(PostActionSettle.ADAPTIVE_PROPERTY);
        ChromeDriver driver = null;
        Path fixture = Files.createTempFile("keel-performance-", ".html");
        try {
            Files.writeString(fixture, """
                <html><body><button id="start" onclick="document.body.setAttribute('aria-busy','true');
                  setTimeout(()=>{document.getElementById('result').textContent='Complete';
                  document.body.removeAttribute('aria-busy')},1500)">Start</button>
                <p id="result">Waiting</p></body></html>
                """);
            var service = new ChromeDriverService.Builder().usingDriverExecutable(Path.of(driverPath).toFile()).build();
            driver = new ChromeDriver(service, new ChromeOptions().addArguments("--headless=new", "--disable-gpu"));
            driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(15));
            System.setProperty(PostActionSettle.PROPERTY, "5000");
            System.setProperty(PostActionSettle.ADAPTIVE_PROPERTY, "true");
            driver.get(fixture.toUri().toString());
            long started = System.nanoTime();
            PostActionSettle.afterNavigation(driver, null, "ready");
            long readyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            Assert.assertTrue(readyMs < 4000, "ready page waited " + readyMs);
            driver.findElement(By.id("start")).click();
            started = System.nanoTime();
            PostActionSettle.afterNavigation(driver, driver.getCurrentUrl(), "Click Start");
            long delayedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            Assert.assertEquals(driver.findElement(By.id("result")).getText(), "Complete");
            Assert.assertTrue(delayedMs >= 1400, "did not await delayed update: " + delayedMs);
            Assert.assertFalse(driver.findElement(By.id("result")).getText().equals("Wrong expected value"));
            System.out.println("PERF_LIVE readyMs=" + readyMs + " delayedBusyMs=" + delayedMs + " configuredFixedMs=5000");
        } finally {
            try { if (driver != null) driver.quit(); }
            finally {
                Files.deleteIfExists(fixture);
                restore(PostActionSettle.PROPERTY, oldWait);
                restore(PostActionSettle.ADAPTIVE_PROPERTY, oldAdaptive);
            }
        }
    }
    private static void restore(String key, String value) {
        if (value == null) System.clearProperty(key); else System.setProperty(key, value);
    }
}
