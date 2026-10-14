package delivery.evidence;

import com.sun.net.httpserver.HttpServer;
import org.json.JSONArray;
import org.json.JSONObject;
import org.openqa.selenium.By;
import org.openqa.selenium.Rectangle;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.testng.Assert.*;

public class BindFailureEvidenceLiveTest {
    private HttpServer server;
    private WebDriver driver;
    private String base;

    @BeforeClass public void start() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", ex -> {
                try (InputStream in = getClass().getResourceAsStream("/fixtures/a9/evidence.html")) {
                    byte[] b = in.readAllBytes();
                    ex.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
                    ex.sendResponseHeaders(200, b.length);
                    ex.getResponseBody().write(b);
                }
                ex.close();
            });
            server.start();
            base = "http://127.0.0.1:" + server.getAddress().getPort() + "/checkout?session=s3cr3t";
            ChromeOptions o = new ChromeOptions();
            o.addArguments("--headless=new", "--no-sandbox", "--disable-gpu", "--window-size=1000,700");
            driver = new ChromeDriver(o);
        } catch (Exception e) {
            throw new SkipException("Chrome unavailable: " + e.getMessage());
        }
    }

    @AfterClass(alwaysRun = true) public void stop() {
        if (driver != null) driver.quit();
        if (server != null) server.stop(0);
    }

    @Test public void bundleIsRedactedMaskedAndRanked() throws Exception {
        driver.get(base);
        driver.findElement(By.id("card")).sendKeys("4111 1111 1111 1111");
        Path artifacts = Files.createTempDirectory("keel-a9");
        Path dir = BindFailureEvidence.capture(driver, new BindFailureEvidence.Request(artifacts, "TC1/step 3",
                "Click Sumbit payment", List.of("submit"), "css=button#pay-now", "no candidate matched", List.of(), "click", delivery.settle.SettlePolicy.SideEffects.MUTATING)).orElseThrow();
        assertTrue(dir.startsWith(artifacts.resolve("evidence")));
        assertTrue(dir.getFileName().toString().startsWith("bind-TC1_step_3-"));

        String dom = Files.readString(dir.resolve("dom.redacted.html"));
        for (String leak : new String[]{"john.doe@example.com", "prefilled@example.com", "hunter2", "4111", "0100 123", "Sayed", "secretToken", "secret123", "ref=abc"})
            assertFalse(dom.contains(leak), "leaked " + leak);
        assertTrue(dom.contains("[email]") && dom.contains("[digits]") && dom.contains("[name]"));
        assertTrue(dom.contains("data-keel-value=\"\u2022\u2022\u2022(19)\""), "typed value only as masked length");
        assertTrue(dom.contains("Submit order"), "non-PII text kept");

        BufferedImage shot = ImageIO.read(dir.resolve("screenshot.masked.png").toFile());
        Rectangle card = driver.findElement(By.id("card")).getRect();
        double dpr = ((Number) ((org.openqa.selenium.JavascriptExecutor) driver).executeScript("return devicePixelRatio")).doubleValue();
        int px = shot.getRGB((int) ((card.x + card.width / 2.0) * dpr), (int) ((card.y + card.height / 2.0) * dpr)) & 0xFFFFFF;
        assertEquals(px, 0, "input masked black");

        JSONObject cands = new JSONObject(Files.readString(dir.resolve("candidates.json")));
        JSONArray near = cands.getJSONArray("nearest");
        assertTrue(near.length() >= 1 && near.length() <= 5, near.toString());
        JSONObject top = near.getJSONObject(0);
        assertEquals(top.getString("name"), "Submit order", near.toString(2));
        assertTrue(top.getJSONArray("reasons").toString().contains("typo 'sumbit'"), top.toString());
        assertTrue(top.getDouble("score") > near.getJSONObject(near.length() - 1).getDouble("score") || near.length() == 1);
        for (int i = 0; i < near.length(); i++) assertTrue(near.getJSONObject(i).toString().length() <= 1024);

        JSONObject m = new JSONObject(Files.readString(dir.resolve("evidence.json")));
        assertEquals(m.getString("contract"), "keel.bind-evidence");
        assertEquals(m.getJSONObject("step").getString("sideEffects"), "mutating");
        assertFalse(m.getJSONObject("step").getBoolean("retrySafe"));
        assertEquals(m.getInt("version"), 1);
        assertFalse(m.getString("url").contains("s3cr3t"), "query stripped");
        assertFalse(m.getString("title").contains("john.doe"));
        assertEquals(m.getJSONArray("problems").length(), 0, m.toString());
        assertTrue(m.getJSONObject("maskedRegions").getInt("inputs") >= 3);
        assertTrue(m.getJSONObject("maskedRegions").getInt("piiText") >= 2);
    }

    @Test(dependsOnMethods = "bundleIsRedactedMaskedAndRanked")
    public void deadSessionNeverThrows() throws Exception {
        ChromeOptions o = new ChromeOptions();
        o.addArguments("--headless=new", "--no-sandbox");
        WebDriver d = new ChromeDriver(o);
        d.quit();
        Path artifacts = Files.createTempDirectory("keel-a9-dead");
        var r = BindFailureEvidence.capture(d, new BindFailureEvidence.Request(artifacts, "s", "x", List.of(), null, "e", List.of()));
        assertTrue(r.isPresent());
        JSONObject m = new JSONObject(Files.readString(r.get().resolve("evidence.json")));
        assertEquals(m.getJSONObject("step").getString("sideEffects"), "unknown");
        assertEquals(m.getJSONArray("problems").length(), 3, m.toString());
    }

    @Test(dependsOnMethods = "bundleIsRedactedMaskedAndRanked")
    public void secretStepValueNeverReachesTheBundle() throws Exception {
        driver.get(base);
        String v = "Zq9xx771";
        Path artifacts = Files.createTempDirectory("keel-a9-secret");
        Path dir = BindFailureEvidence.capture(driver, new BindFailureEvidence.Request(artifacts, "TC1/7",
                "Type " + v + " into the OTP field", List.of("otp"), "css=input[value='" + v + "']",
                "no element for " + v, List.of(), "type", delivery.settle.SettlePolicy.SideEffects.NONE, true, v)).orElseThrow();
        for (String f : new String[]{"evidence.json", "candidates.json", "dom.redacted.html"})
            assertFalse(Files.readString(dir.resolve(f)).contains(v), f);
        JSONObject m = new JSONObject(Files.readString(dir.resolve("evidence.json")));
        assertTrue(m.getJSONObject("step").getBoolean("secret"));
        assertTrue(m.getJSONObject("step").getString("text").contains("\u2022\u2022\u2022"));
    }
}
