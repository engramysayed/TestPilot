package delivery.snapshot;

import com.sun.net.httpserver.HttpServer;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Optional;

import static org.testng.Assert.*;

/** Live: headless Chrome against fixtures/a2 served over HTTP (same origin, so frames are readable). */
public class PageSerializerLiveSmokeTest {
    private HttpServer server;
    private WebDriver driver;
    private String base;

    @BeforeClass public void start() throws Exception {
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
        try {
            ChromeOptions o = new ChromeOptions();
            o.addArguments("--headless=new", "--no-sandbox", "--disable-gpu", "--window-size=1280,800");
            driver = new ChromeDriver(o);
        } catch (RuntimeException e) {
            throw new SkipException("Chrome unavailable: " + e.getMessage());
        }
        assertTrue(PageSerializer.installClosedShadowHook(driver), "CDP hook install");
    }

    @AfterClass(alwaysRun = true) public void stop() {
        if (driver != null) driver.quit();
        if (server != null) server.stop(0);
    }

    private PageModel open(String page, List<String> keywords) {
        driver.get(base + page);
        return PageSerializer.serialize(driver, keywords, List.of("data-axis-test-id"));
    }

    private static Optional<SnapshotCandidate> byName(PageModel m, String name) {
        return m.candidates().stream().filter(c -> c.name().equals(name)).findFirst();
    }

    @Test public void sidebarItem50IsKeptOffscreenInScrollContainerAndClickable() {
        PageModel m = open("sidebar.html", List.of("balance"));
        long items = m.candidates().stream().filter(c -> c.tag().equals("li") && c.name().startsWith("Item ")).count();
        assertEquals(items, 60, "all 60 visible-tree items kept; hidden mobile copy excluded");
        SnapshotCandidate c = byName(m, "Item 50").orElseThrow();
        assertTrue(c.offscreen(), "item 50 is clipped by the sidebar scroll container");
        assertEquals(c.scrollContainer(), "#nav");
        assertTrue(c.navItem() && c.interactive());
        assertTrue(c.target().isPlainCss());
        WebElement el = KeelTargetResolver.find(driver, c.target());
        ((org.openqa.selenium.JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'})", el);
        el.click();
        assertEquals(driver.findElement(org.openqa.selenium.By.id("out")).getText(), "clicked 50");
    }

    @Test public void stepKeywordKeepsNonInteractiveText() {
        PageModel m = open("sidebar.html", List.of("balance"));
        SnapshotCandidate kpi = byName(m, "Total balance").orElseThrow();
        assertTrue(kpi.keyword() && !kpi.interactive());
        assertFalse(open("sidebar.html", List.of()).candidates().stream().anyMatch(c -> c.name().equals("Total balance")));
    }

    @Test public void nestedFramesCarryFramePathAndResolve() {
        PageModel m = open("frames.html", List.of());
        SnapshotCandidate pay = byName(m, "Pay now").orElseThrow();
        assertEquals(pay.framePath().size(), 2);
        assertEquals(pay.framePath().get(0).selector(), "#outer");
        assertEquals(pay.framePath().get(1).selector(), "iframe[name=\"inner\"]");
        assertEquals(pay.framePath().get(1).index(), 0);
        assertEquals(pay.target().format(), "frame=#outer >> frame=iframe[name=\"inner\"] >> css=button");
        WebElement el = KeelTargetResolver.find(driver, pay.target());
        el.click();
        assertEquals(el.getText(), "Paid");
        KeelTargetResolver.reset(driver);
        assertEquals(byName(m, "Level one").orElseThrow().framePath().size(), 1);
    }

    @Test public void frameDepthIsCappedAtFive() {
        PageModel m = open("deep0.html", List.of());
        for (int k = 1; k <= 5; k++) {
            SnapshotCandidate c = byName(m, "Deep " + k).orElseThrow(() -> new AssertionError("missing depth"));
            assertEquals(c.framePath().size(), c.name().equals("Deep 1") ? 1 : Integer.parseInt(c.name().substring(5)));
        }
        assertTrue(byName(m, "Deep 6").isEmpty());
        assertEquals(KeelTargetResolver.find(driver, byName(m, "Deep 5").orElseThrow().target()).getText(), "Deep 5");
        KeelTargetResolver.reset(driver);
    }

    @Test public void openAndClosedShadowRootsArePiercedAndResolvable() {
        PageModel m = open("shadow.html", List.of());
        assertTrue(m.closedShadowHook());
        SnapshotCandidate openBtn = byName(m, "Open action").orElseThrow();
        assertEquals(openBtn.target().format(), "shadow=#o >> css=#ob");
        SnapshotCandidate closedBtn = byName(m, "Closed action").orElseThrow();
        assertEquals(closedBtn.target().format(), "shadow-closed=#c >> css=#cb");
        SnapshotCandidate nested = byName(m, "Nested action").orElseThrow();
        assertEquals(nested.target().format(), "shadow-closed=#c >> shadow=x-inner >> css=button");
        SnapshotCandidate inFrame = byName(m, "Frame in shadow").orElseThrow();
        assertEquals(inFrame.target().format(), "shadow=#o >> frame=#sf >> css=button");

        KeelTargetResolver.find(driver, closedBtn.target()).click();
        assertEquals(driver.findElement(org.openqa.selenium.By.id("out")).getText(), "closed");
        KeelTargetResolver.find(driver, nested.target()).click();
        assertEquals(driver.findElement(org.openqa.selenium.By.id("out")).getText(), "nested");
        KeelTargetResolver.find(driver, openBtn.target()).click();
        assertEquals(driver.findElement(org.openqa.selenium.By.id("out")).getText(), "open");
        assertEquals(KeelTargetResolver.find(driver, inFrame.target()).getText(), "Frame in shadow");
        KeelTargetResolver.reset(driver);
    }

    @Test public void withoutHookClosedRootIsInvisibleButOpenStillWorks() {
        ChromeOptions o = new ChromeOptions();
        o.addArguments("--headless=new", "--no-sandbox", "--disable-gpu");
        WebDriver plain = new ChromeDriver(o);
        try {
            plain.get(base + "shadow.html");
            PageModel m = PageSerializer.serialize(plain, List.of(), List.of());
            assertFalse(m.closedShadowHook());
            assertTrue(byName(m, "Open action").isPresent());
            assertTrue(byName(m, "Closed action").isEmpty());
        } finally {
            plain.quit();
        }
    }
}
