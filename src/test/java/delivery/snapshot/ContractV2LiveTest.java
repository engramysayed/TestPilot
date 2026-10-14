package delivery.snapshot;

import com.sun.net.httpserver.HttpServer;
import org.json.JSONArray;
import org.json.JSONObject;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
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

/** Contract v2 runtime: shared matcher vectors, text= in frames/shadow, point/provider, ranker text tier. */
public class ContractV2LiveTest {
    private static final List<String> HOOKS = List.of("data-axis-test-id");
    private HttpServer server;
    private WebDriver driver;
    private String base;

    @BeforeClass public void start() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", ex -> {
                String p = ex.getRequestURI().getPath();
                try (InputStream in = getClass().getResourceAsStream("/fixtures/a2" + p)) {
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
            o.addArguments("--headless=new", "--no-sandbox", "--disable-gpu", "--window-size=1280,800");
            driver = new ChromeDriver(o);
        } catch (Exception e) {
            throw new SkipException("Chrome unavailable: " + e.getMessage());
        }
        PageSerializer.installClosedShadowHook(driver);
    }

    @AfterClass(alwaysRun = true) public void stop() {
        System.clearProperty(LocatorRanker.MAX_VERSION_KEY);
        if (driver != null) driver.quit();
        if (server != null) server.stop(0);
    }

    private Object js(String body, Object... args) {
        return ((JavascriptExecutor) driver).executeScript(KeelTargetResolver.TEXT_MATCH_JS + "\n" + body, args);
    }

    @Test public void normalizationVectors() throws Exception {
        driver.get("about:blank");
        JSONArray a = ContractV2VectorsTest.vectors().getJSONArray("normalize");
        for (int i = 0; i < a.length(); i++) {
            JSONObject v = a.getJSONObject(i);
            assertEquals(js("return keelNormalizeText(arguments[0]);", v.getString("in")), v.getString("out"), v.toString());
        }
    }

    @Test public void matchVectors() throws Exception {
        driver.get(base + "ranking.html");
        JSONArray a = ContractV2VectorsTest.vectors().getJSONArray("match");
        for (int i = 0; i < a.length(); i++) {
            JSONObject v = a.getJSONObject(i);
            ((JavascriptExecutor) driver).executeScript("document.body.innerHTML = arguments[0];", v.getString("html"));
            List<?> found = (List<?>) js("return keelTextMatch(null, null, arguments[0]);", v.getString("text"));
            assertEquals(found.size(), v.getInt("count"), v.toString());
        }
    }

    @Test public void textHopScopedToFrameAndShadow() {
        driver.get(base + "frames.html");
        assertEquals(KeelTargetResolver.find(driver,
                KeelTarget.parse("frame=#outer >> frame=iframe[name=\"inner\"] >> text=\"Pay now\"")).getTagName(), "button");
        KeelTargetResolver.reset(driver);
        expectThrows(NoSuchElementException.class, () -> KeelTargetResolver.find(driver, KeelTarget.parse("text=\"Pay now\"")));
        KeelTargetResolver.reset(driver);
        driver.get(base + "shadow.html");
        assertEquals(KeelTargetResolver.find(driver, KeelTarget.parse("shadow-closed=#c >> text=\"Closed action\"")).getAttribute("id"), "cb");
    }

    @Test public void pointAndProviderOffsets() {
        driver.get(base + "ranking.html");
        ((JavascriptExecutor) driver).executeScript(
                "document.body.innerHTML='<canvas id=c width=400 height=200 style=\"width:400px;height:200px\"></canvas>';"
                + "window.__keelProviders={demo:function(el,id){return id==='b1'?{x:10,y:20,w:20,h:40}:null;}};");
        var p = KeelTargetResolver.resolveClick(driver, KeelTarget.parse("css=#c >> point=100,50@800x400"));
        assertEquals(p.dxFromCentre(), -150, 0.5);
        assertEquals(p.dyFromCentre(), -75, 0.5);
        var q = KeelTargetResolver.resolveClick(driver, KeelTarget.parse("css=#c >> provider=demo:b1"));
        assertEquals(q.dxFromCentre(), 20 - 200, 0.5);
        assertEquals(q.dyFromCentre(), 40 - 100, 0.5);
        var r = KeelTargetResolver.resolveClick(driver, KeelTarget.parse("css=#c >> provider=demo:missing >> point=400,200@800x400"));
        assertEquals(r.dxFromCentre(), 0, 0.5, "provider miss falls back to point");
    }

    private SnapshotCandidate pick(String page, String name) {
        driver.get(base + page);
        return PageSerializer.serialize(driver, List.of(), HOOKS).candidates().stream()
                .filter(c -> c.name().equals(name)).findFirst().orElseThrow();
    }

    @Test public void rankerTextTier() {
        System.setProperty(LocatorRanker.MAX_VERSION_KEY, "1");
        RankedLocators v1 = LocatorRanker.rank(driver, pick("ranking.html", "Refresh"), HOOKS, "").orElseThrow();
        assertTrue(v1.all().stream().noneMatch(l -> l.value().startsWith("text=")), "v1 consumer never sees text=");
        System.setProperty(LocatorRanker.MAX_VERSION_KEY, "2");
        try {
            RankedLocators refresh = LocatorRanker.rank(driver, pick("ranking.html", "Refresh"), HOOKS, "").orElseThrow();
            assertEquals(refresh.main().value(), "text=\"Refresh\"", "dynamic id only: text wins");
            assertEquals(refresh.toIrJson().getInt("contract"), 2);
            RankedLocators save = LocatorRanker.rank(driver, pick("ranking.html", "Save order"), HOOKS, "").orElseThrow();
            assertFalse(save.main().value().startsWith("text="), save.toString());
            RankedLocators del = LocatorRanker.rank(driver, pick("ranking.html", "Delete"), HOOKS, "").orElseThrow();
            assertTrue(del.all().stream().noneMatch(l -> l.value().startsWith("text=")), "ambiguous text rejected");
            SnapshotCandidate item = pick("sidebar.html", "Item 50");
            RankedLocators it = LocatorRanker.rank(driver, item, HOOKS, "").orElseThrow();
            assertTrue(it.all().stream().anyMatch(l -> l.value().endsWith("text=\"Item 50\"")), it.toString());
        } finally {
            System.clearProperty(LocatorRanker.MAX_VERSION_KEY);
        }
    }
}
