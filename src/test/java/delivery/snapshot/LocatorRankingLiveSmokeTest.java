package delivery.snapshot;

import com.sun.net.httpserver.HttpServer;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.HasCapabilities;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.MutableCapabilities;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.testng.Assert.*;

/** A3: element-id pick -> ranked keel locators -> replay cache, on fixtures/a2 (headless Chrome). */
public class LocatorRankingLiveSmokeTest {
    private static final List<String> HOOKS = List.of("data-axis-test-id");
    private HttpServer server;
    private WebDriver driver;
    private String base;

    @BeforeClass public void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            String p = ex.getRequestURI().getPath();
            // /orders/<id> and /customers/<id> both serve ranking.html (route tests)
            String file = p.matches("/(orders|customers)/[^/]+") ? "/ranking.html" : p;
            try (InputStream in = getClass().getResourceAsStream("/fixtures/a2" + file)) {
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
        try {
            ChromeOptions o = new ChromeOptions();
            o.addArguments("--headless=new", "--no-sandbox", "--disable-gpu", "--window-size=1280,800");
            driver = new ChromeDriver(o);
        } catch (RuntimeException e) {
            throw new SkipException("Chrome unavailable: " + e.getMessage());
        }
        PageSerializer.installClosedShadowHook(driver);
    }

    @AfterClass(alwaysRun = true) public void stop() {
        if (driver != null) driver.quit();
        if (server != null) server.stop(0);
    }

    private SnapshotCandidate pick(String page, String name) {
        driver.get(base + page);
        PageModel m = PageSerializer.serialize(driver, List.of(), HOOKS);
        return m.candidates().stream().filter(c -> c.name().equals(name)).findFirst().orElseThrow();
    }

    private static Function<PageModel, String> ai(String name, AtomicInteger calls) {
        return m -> {
            calls.incrementAndGet();
            for (int i = 0; i < m.candidates().size(); i++)
                if (m.candidates().get(i).name().equals(name)) return PageModel.idOf(i);
            return "none";
        };
    }

    @Test public void rankingOrderAndTopThree() {
        RankedLocators r = LocatorRanker.rank(driver, pick("orders/1", "Save order"), HOOKS, "").orElseThrow();
        assertEquals(r.main().value(), "css=button[data-axis-test-id=\"save-hook\"]");
        assertEquals(r.main().score(), 1.0);
        assertEquals(r.fallbacks().size(), 2);
        assertEquals(r.fallbacks().get(0).value(), "css=button[data-testid=\"save\"]");
        assertEquals(r.fallbacks().get(1).value(), "css=button[aria-label=\"Save order\"]");
        double prev = 2;
        for (ScoredLocator l : r.all()) { assertTrue(l.score() <= prev && l.score() >= 0 && l.score() <= 1); prev = l.score(); assertEquals(l.strategy(), "keel"); }
        var json = r.toIrJson();
        assertEquals(json.getString("locatorStrategy"), "keel");
        assertEquals(json.getJSONArray("locatorCandidates").length(), 3);
        assertEquals(json.getJSONArray("fallbacks").length(), 2);
        assertEquals(RankedLocators.fromIrJson(json).all(), r.all().stream()
                .map(l -> new ScoredLocator(l.strategy(), l.value(), l.score(), "")).toList());
    }

    @Test public void ambiguousCandidatesAreRejected() {
        SnapshotCandidate del = pick("orders/1", "Delete");
        List<ScoredLocator> gen = LocatorRanker.generate(del, HOOKS);
        assertTrue(gen.stream().anyMatch(l -> l.value().equals("css=button[aria-label=\"Delete\"]")), gen.toString());
        assertTrue(gen.stream().anyMatch(l -> l.value().equals("css=button.danger")), gen.toString());
        RankedLocators r = LocatorRanker.rank(driver, del, HOOKS, "").orElseThrow();
        List<String> values = r.all().stream().map(ScoredLocator::value).toList();
        assertFalse(values.contains("css=button[aria-label=\"Delete\"]"), values.toString());
        assertFalse(values.contains("css=button.danger"), values.toString());
        assertFalse(values.contains("css=button.btn"), values.toString());
        assertEquals(r.main().value(), "css=[id=\"del-1\"]");
    }

    @Test public void dynamicIdIsDemotedAndLabelTierUsed() {
        RankedLocators refresh = LocatorRanker.rank(driver, pick("orders/1", "Refresh"), HOOKS, "").orElseThrow();
        assertEquals(refresh.main().score(), LocatorRanker.CSS_ID_DYNAMIC);
        driver.navigate().refresh();
        SnapshotCandidate search = PageSerializer.serialize(driver, List.of(), HOOKS).candidates().stream()
                .filter(c -> "Search orders".equals(c.attrs().get("placeholder"))).findFirst().orElseThrow();
        RankedLocators r = LocatorRanker.rank(driver, search, HOOKS, "").orElseThrow();
        assertEquals(r.main().value(), "css=input[placeholder=\"Search orders\"]");
        assertEquals(r.fallbacks().get(0).value(), "css=input[name=\"q\"]");
    }

    @Test public void contextPrefixKeptForFramesAndClosedShadow() {
        RankedLocators pay = LocatorRanker.rank(driver, pick("frames.html", "Pay now"), HOOKS, "").orElseThrow();
        assertTrue(pay.main().value().startsWith("frame=#outer >> frame=iframe[name=\"inner\"] >> css="), pay.main().value());
        RankedLocators closed = LocatorRanker.rank(driver, pick("shadow.html", "Closed action"), HOOKS, "").orElseThrow();
        assertEquals(closed.main().value(), "shadow-closed=#c >> css=[id=\"cb\"]");
        assertTrue(closed.all().stream().allMatch(l -> l.value().startsWith("shadow-closed=#c >> ")));
    }

    @Test public void provenLocatorBecomesMainAndFallbacksStayOrdered() {
        RankedLocators r = LocatorRanker.rank(driver, pick("orders/1", "Save order"), HOOKS, "").orElseThrow();
        RankedLocators p = r.withProven("css=button[aria-label=\"Save order\"]");
        assertEquals(p.main().value(), "css=button[aria-label=\"Save order\"]");
        assertEquals(p.fallbacks().get(0).score(), 1.0);
        assertEquals(p.fallbacks().get(1).score(), 0.95);
    }

    @Test public void cacheHitMissRouteMismatchAndStale() throws Exception {
        ReplayCache cache = new ReplayCache();
        LocatorPipeline pipe = new LocatorPipeline(cache, HOOKS);
        AtomicInteger calls = new AtomicInteger();
        String step = "Click the Save button.";

        driver.get(base + "orders/101");
        var first = pipe.locate(driver, step, List.of(), ai("Save order", calls)).orElseThrow();
        assertEquals(first.source(), LocatorPipeline.Source.AI);
        assertEquals(first.cacheOutcome(), ReplayCache.Outcome.MISS);
        assertEquals(first.ranked().route(), "127.0.0.1:" + server.getAddress().getPort() + "/orders/:id");

        driver.get(base + "orders/202");                      // same route pattern, different id -> HIT
        var second = pipe.locate(driver, "click the save button", List.of(), ai("Save order", calls)).orElseThrow();
        assertEquals(second.source(), LocatorPipeline.Source.CACHE);
        assertEquals(calls.get(), 1, "AI not called on cache hit");
        second.element().click();
        assertEquals(driver.findElement(org.openqa.selenium.By.id("out")).getText(), "saved");

        driver.get(base + "customers/7");                     // same page markup, other route -> no replay
        var third = pipe.locate(driver, step, List.of(), ai("Save order", calls)).orElseThrow();
        assertEquals(third.cacheOutcome(), ReplayCache.Outcome.ROUTE_MISMATCH);
        assertEquals(third.source(), LocatorPipeline.Source.AI);
        assertEquals(calls.get(), 2);

        driver.get(base + "orders/303");
        ((JavascriptExecutor) driver).executeScript("dupSave()"); // all 3 cached locators now ambiguous
        var fourth = pipe.locate(driver, step, List.of(), ai("Save order", calls));
        assertEquals(calls.get(), 3, "stale entry fell through to AI");
        // the AI-picked element has a unique id left -> re-ranked and re-cached
        assertEquals(fourth.orElseThrow().cacheOutcome(), ReplayCache.Outcome.STALE);
        assertEquals(fourth.get().ranked().main().value(), "css=[id=\"btn-save\"]");

        driver.get(base + "orders/404");                      // re-cached entry replays on a fresh page
        assertEquals(cache.lookup(driver, step, driver.getCurrentUrl()).outcome(), ReplayCache.Outcome.HIT);
        KeelTargetResolver.reset(driver);

        pipe.reportFailure(driver, step);                     // failure invalidates
        assertEquals(cache.lookup(driver, step, driver.getCurrentUrl()).outcome(), ReplayCache.Outcome.ROUTE_MISMATCH);

        Path f = Files.createTempFile("keel-cache", ".json");
        cache.save(f);
        assertEquals(ReplayCache.load(f).size(), cache.size());
    }

    @Test public void cacheFallbackUsedWhenMainNoLongerResolves() {
        ReplayCache cache = new ReplayCache();
        driver.get(base + "orders/1");
        RankedLocators r = LocatorRanker.rank(driver, pick("orders/1", "Save order"), HOOKS, "").orElseThrow();
        cache.put("save", driver.getCurrentUrl(), r);
        ((JavascriptExecutor) driver).executeScript("document.getElementById('btn-save').removeAttribute('data-axis-test-id')");
        var l = cache.lookup(driver, "save", driver.getCurrentUrl());
        assertEquals(l.outcome(), ReplayCache.Outcome.HIT);
        assertEquals(l.used().orElseThrow().value(), "css=button[data-testid=\"save\"]");
        KeelTargetResolver.reset(driver);
        ((JavascriptExecutor) driver).executeScript("dropSave()");
        assertEquals(cache.lookup(driver, "save", driver.getCurrentUrl()).outcome(), ReplayCache.Outcome.STALE);
        assertEquals(cache.size(), 0, "stale entry invalidated");
    }

    @Test public void firefoxFailsFastOnShadowClosed() {
        WebDriver ff = (WebDriver) java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{WebDriver.class, HasCapabilities.class}, (proxy, m, args) -> {
                    if (m.getName().equals("getCapabilities")) {
                        MutableCapabilities c = new MutableCapabilities();
                        c.setCapability("browserName", "firefox");
                        return (Capabilities) c;
                    }
                    throw new AssertionError("page must not be touched: " + m.getName());
                });
        KeelTarget t = KeelTarget.parse("shadow-closed=#c >> css=#cb");
        UnsupportedOperationException e = expectThrows(UnsupportedOperationException.class, () -> KeelTargetResolver.find(ff, t));
        assertEquals(e.getMessage(), "keel: shadow-closed= targets need Chrome/Edge (CDP closed-shadow hook); not supported on Firefox: shadow-closed=#c >> css=#cb");
        expectThrows(UnsupportedOperationException.class, () -> KeelTargetResolver.findAll(ff, t));
    }
}
