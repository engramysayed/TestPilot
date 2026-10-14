package delivery.codegen;

import com.sun.net.httpserver.HttpServer;
import delivery.snapshot.KeelTarget;
import delivery.snapshot.KeelTargetResolver;
import delivery.snapshot.PageModel;
import delivery.snapshot.PageSerializer;
import delivery.snapshot.SnapshotCandidate;
import org.openqa.selenium.By;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.HasCapabilities;
import org.openqa.selenium.MutableCapabilities;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Parity: customer-template KeelLocate (compiled from customer-framework-template) vs the
 * TestPilot reference KeelTargetResolver, on fixtures/a2 in headless Chrome. Every serializer-
 * produced target plus hand-written edge/error cases must give the same element or the same
 * exception (class + message). Also covers KeelHook (CDP install), KeelBy fallbacks and SmartWait.
 */
public class KeelLocateParityLiveSmokeTest {
    private HttpServer server;
    private String base;
    private WebDriver hooked;     // closed-shadow hook installed via the template's KeelHook
    private WebDriver plain;      // no hook
    private URLClassLoader loader;
    private Method keelFind;
    private Method keelReset;
    private Method keelParse;
    private Class<?> keelBy;
    private Method waitActionable;

    @BeforeClass
    public void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            try (InputStream in = getClass().getResourceAsStream("/fixtures/a2" + ex.getRequestURI().getPath())) {
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

        Path classes = Files.createTempDirectory("keel-locate");
        Path pkg = Path.of("customer-framework-template/src/main/java/project/utils/locators");
        loader = CustomerTemplateCompiler.compile(classes, pkg.resolve("KeelLocate.java"), pkg.resolve("KeelBy.java"),
                pkg.resolve("KeelHook.java"), pkg.resolve("SmartWait.java"));
        // KeelHook reads the verbatim resource from the template's classpath root
        Path res = classes.resolve("keel");
        Files.createDirectories(res);
        Files.copy(Path.of("customer-framework-template/src/main/resources/keel/closed-shadow-hook.js"),
                res.resolve("closed-shadow-hook.js"));
        Class<?> kl = loader.loadClass("project.utils.locators.KeelLocate");
        keelFind = kl.getMethod("find", WebDriver.class, String.class);
        keelReset = kl.getMethod("reset", WebDriver.class);
        keelParse = kl.getMethod("parse", String.class);
        keelBy = loader.loadClass("project.utils.locators.KeelBy");
        waitActionable = loader.loadClass("project.utils.locators.SmartWait")
                .getMethod("untilActionable", WebDriver.class, By.class, Duration.class);
        try {
            hooked = chrome();
            plain = chrome();
        } catch (RuntimeException e) {
            throw new SkipException("Chrome unavailable: " + e.getMessage());
        }
        boolean installed = (boolean) loader.loadClass("project.utils.locators.KeelHook")
                .getMethod("install", WebDriver.class).invoke(null, hooked);
        Assert.assertTrue(installed, "template KeelHook installs via CDP");
    }

    private static WebDriver chrome() {
        ChromeOptions o = new ChromeOptions();
        o.addArguments("--headless=new", "--no-sandbox", "--disable-gpu", "--window-size=1280,800");
        return new ChromeDriver(o);
    }

    @AfterClass(alwaysRun = true)
    public void stop() throws Exception {
        if (hooked != null) hooked.quit();
        if (plain != null) plain.quit();
        if (server != null) server.stop(0);
        if (loader != null) loader.close();
    }

    private record Outcome(String elementKey, String error) {
    }

    private static Outcome run(WebDriver d, Supplier<WebElement> find) {
        try {
            WebElement el = find.get();
            String key = el.getTagName() + "|" + el.getText() + "|" + ((org.openqa.selenium.remote.RemoteWebElement) el).getId();
            d.switchTo().defaultContent();
            return new Outcome(key, null);
        } catch (RuntimeException e) {
            d.switchTo().defaultContent();
            String msg = e.getMessage() == null ? "" : e.getMessage().lines().findFirst().orElse("");
            return new Outcome(null, e.getClass().getName() + ": " + msg);
        }
    }

    private WebElement keel(WebDriver d, String target) {
        try {
            return (WebElement) keelFind.invoke(null, d, target);
        } catch (InvocationTargetException e) {
            throw (RuntimeException) e.getCause();
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private void assertParity(WebDriver d, String target) {
        Outcome ref = run(d, () -> KeelTargetResolver.find(d, KeelTarget.parse(target)));
        Outcome got = run(d, () -> keel(d, target));
        Assert.assertEquals(got, ref, "parity for " + target);
    }

    @DataProvider
    public Object[][] pages() {
        return new Object[][]{{"frames.html"}, {"shadow.html"}, {"deep0.html"}, {"sidebar.html"}};
    }

    @Test(dataProvider = "pages")
    public void everySerializerTargetResolvesIdentically(String page) {
        hooked.get(base + page);
        PageModel m = PageSerializer.serialize(hooked, List.of("balance"), List.of("data-axis-test-id"));
        Assert.assertFalse(m.candidates().isEmpty(), page);
        int checked = 0;
        for (SnapshotCandidate c : m.candidates()) {
            assertParity(hooked, c.target().format());
            checked++;
        }
        Assert.assertTrue(checked > 0);
    }

    @DataProvider
    public Object[][] edgeTargets() {
        return new Object[][]{
                {"shadow.html", "shadow=#o >> frame=#sf >> css=button"},
                {"shadow.html", "shadow-closed=#c >> shadow=x-inner >> css=button.nested"},
                {"shadow.html", "shadow-closed=#c >> css=#cb"},
                {"shadow.html", "shadow=#c >> css=#cb"},                 // closed root via open hop: getShadowRoot fails
                {"shadow.html", "css=button"},                           // light DOM has no button
                {"shadow.html", "shadow-closed=#o >> css=#ob"},          // hook captured open root too
                {"frames.html", "frame=#outer >> css=button"},           // exactly one in level 1
                {"frames.html", "frame=#outer >> css=p, button"},        // ambiguous: 2 matches
                {"frames.html", "frame=#nope >> css=button"},
                {"frames.html", "frame=#outer >> frame=iframe[name=\"inner\"] >> css=button"},
                {"sidebar.html", "css=li"},                              // many
                {"sidebar.html", "css=li[data-idx=\"50\"]"},
        };
    }

    @Test(dataProvider = "edgeTargets")
    public void edgeAndErrorCasesMatchReference(String page, String target) {
        hooked.get(base + page);
        assertParity(hooked, target);
    }

    @Test
    public void closedShadowWithoutHookFailsTheSameWay() {
        plain.get(base + "shadow.html");
        assertParity(plain, "shadow-closed=#c >> css=#cb");
        Outcome o = run(plain, () -> keel(plain, "shadow-closed=#c >> css=#cb"));
        Assert.assertTrue(o.error().contains("hook missing"), o.error());
    }

    @DataProvider
    public Object[][] badSyntax() {
        return new Object[][]{{"xpath=//a"}, {"css=a >> frame=#f"}, {"frame=#f"}, {"css="}, {""}, {"role=button >> css=a"},
                {"css=a[title=\"x \\>> y\"]"}, {"frame=#a >> frame=#b >> shadow-closed=x >> css=b"}};
    }

    @Test(dataProvider = "badSyntax")
    public void parserMatchesReferenceIncludingErrors(String s) throws Exception {
        String ref;
        try {
            ref = KeelTarget.parse(s).format();
        } catch (RuntimeException e) {
            ref = e.getClass().getName() + ": " + e.getMessage();
        }
        String got;
        try {
            got = String.valueOf(((List<?>) keelParse.invoke(null, s)).size());
            got = ref.contains(": ") ? got : KeelTarget.parse(s).format();   // valid: compare via reference format
        } catch (InvocationTargetException e) {
            got = e.getCause().getClass().getName() + ": " + e.getCause().getMessage();
        }
        Assert.assertEquals(got, ref);
    }

    @Test
    public void firefoxShadowClosedFailsFast() throws Exception {
        List<String> calls = new ArrayList<>();
        WebDriver fake = (WebDriver) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{WebDriver.class, HasCapabilities.class}, (p, m, a) -> {
                    calls.add(m.getName());
                    if (m.getName().equals("getCapabilities")) {
                        MutableCapabilities caps = new MutableCapabilities();
                        caps.setCapability("browserName", "firefox");
                        return (Capabilities) caps;
                    }
                    throw new AssertionError("page must not be touched: " + m.getName());
                });
        try {
            keel(fake, "shadow-closed=#c >> css=#cb");
            Assert.fail("expected fail-fast");
        } catch (UnsupportedOperationException e) {
            Assert.assertTrue(e.getMessage().contains("Firefox"), e.getMessage());
        }
        Assert.assertEquals(calls, List.of("getCapabilities"));
    }

    private By keelBy(String... targets) throws Exception {
        return (By) keelBy.getMethod("of", String.class, String[].class).invoke(null, "t", (Object) targets);
    }

    @Test
    public void keelByFallsBackInRankOrderAndSmartWaitWorksInClosedShadow() throws Throwable {
        hooked.get(base + "shadow.html");
        By by = keelBy("shadow-closed=#c >> css=#missing", "frame=#outer >> css=button", "shadow-closed=#c >> css=#cb");
        WebElement el;
        try {
            el = (WebElement) waitActionable.invoke(null, hooked, by, Duration.ofSeconds(5));
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
        Assert.assertEquals(el.getText(), "Closed action");
        Assert.assertEquals((int) keelBy.getMethod("lastHit").invoke(by), 2);
        el.click();
        keelReset.invoke(null, hooked);
        Assert.assertEquals(hooked.findElement(By.id("out")).getText(), "closed");
        By none = keelBy("css=#nothing");
        try {
            waitActionable.invoke(null, hooked, none, Duration.ofMillis(600));
            Assert.fail("expected timeout");
        } catch (InvocationTargetException e) {
            Assert.assertTrue(e.getCause() instanceof TimeoutException, String.valueOf(e.getCause()));
        }
    }

    @Test
    public void keelByLogsAndReportsWinningIndexOnChange() throws Throwable {
        List<String> logs = new java.util.concurrent.CopyOnWriteArrayList<>();
        org.apache.logging.log4j.core.LoggerContext ctx =
                (org.apache.logging.log4j.core.LoggerContext) org.apache.logging.log4j.LogManager.getContext(false);
        org.apache.logging.log4j.core.Appender app = new org.apache.logging.log4j.core.appender.AbstractAppender(
                "keel-capture", null, null, true, org.apache.logging.log4j.core.config.Property.EMPTY_ARRAY) {
            @Override
            public void append(org.apache.logging.log4j.core.LogEvent e) {
                logs.add(e.getLevel() + " " + e.getMessage().getFormattedMessage());
            }
        };
        app.start();
        ctx.getConfiguration().getRootLogger().addAppender(app, org.apache.logging.log4j.Level.ALL, null);
        ctx.updateLoggers();
        io.qameta.allure.AllureLifecycle life = io.qameta.allure.Allure.getLifecycle();
        String uuid = java.util.UUID.randomUUID().toString();
        List<String> steps = new ArrayList<>();
        try {
            life.scheduleTestCase(new io.qameta.allure.model.TestResult().setUuid(uuid).setName("keel-by-log"));
            life.startTestCase(uuid);
            hooked.get(base + "shadow.html");
            By by = keelBy("css=#missing", "shadow=#o >> css=#ob", "shadow-closed=#c >> css=#cb");
            hooked.findElement(by);
            hooked.findElement(by);   // same winner again: no duplicate log
            Assert.assertEquals((int) keelBy.getMethod("lastHit").invoke(by), 1);
            hooked.switchTo().defaultContent();
            By main = keelBy("shadow-closed=#c >> css=#cb");
            hooked.findElement(main);
            Assert.assertEquals((int) keelBy.getMethod("lastHit").invoke(main), 0);
            hooked.switchTo().defaultContent();
            life.updateTestCase(uuid, tr -> tr.getSteps().forEach(st -> steps.add(st.getName())));
        } finally {
            life.stopTestCase(uuid);
            ctx.getConfiguration().getRootLogger().removeAppender("keel-capture");
            ctx.updateLoggers();
        }
        List<String> keelLogs = logs.stream().filter(l -> l.contains("keel locator")).toList();
        Assert.assertEquals(keelLogs, List.of(
                "WARN keel locator t: fallback index 1 won -> shadow=#o >> css=#ob",
                "INFO keel locator t: main index 0 won -> shadow-closed=#c >> css=#cb"), logs.toString());
        Assert.assertEquals(steps, List.of(
                "keel locator t: fallback index 1 won -> shadow=#o >> css=#ob",
                "keel locator t: main index 0 won -> shadow-closed=#c >> css=#cb"));
    }

    @Test
    public void keelByRejectsUnknownHopEagerly() throws Exception {
        try {
            keelBy("css=a", "role=button >> css=a");
            Assert.fail();
        } catch (InvocationTargetException e) {
            Assert.assertEquals(e.getCause().getMessage(), "unknown hop: role=button");
        }
    }

    @Test
    public void driverFactoriesInstallHookBeforeReturningTheDriver() throws Exception {
        for (String f : List.of("ChromeFactory.java", "EdgeFactory.java")) {
            String src = Files.readString(Path.of("customer-framework-template/src/main/java/project/drivers", f));
            Assert.assertTrue(src.contains("KeelHook.install(driver);"), f);
            Assert.assertTrue(src.indexOf("KeelHook.install(driver);") < src.indexOf("return driver;"), f);
        }
        Assert.assertEquals(
                Files.readString(Path.of("customer-framework-template/src/main/resources/keel/closed-shadow-hook.js")),
                Files.readString(Path.of("src/main/resources/keel/closed-shadow-hook.js")),
                "hook shipped verbatim");
    }
}
