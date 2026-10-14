package delivery.codegen;

import com.sun.net.httpserver.HttpServer;
import delivery.snapshot.KeelTarget;
import delivery.snapshot.KeelTargetResolver;
import org.json.JSONArray;
import org.json.JSONObject;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebElement;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Contract v2 parity: template KeelLocate / KeelBy / KeelActions (compiled from
 * customer-framework-template) vs engine KeelTarget / KeelTargetResolver, driven by the shared golden
 * vectors (contract-v2-vectors.json) plus live text/provider/point/drag cases in headless Chrome.
 */
public class KeelContractV2ParityLiveSmokeTest {
    private static final String CANVAS_PAGE = "<!doctype html><html><body style='margin:0'>"
            + "<canvas id='c' width='400' height='200' style='width:400px;height:200px;display:block'></canvas>"
            + "<p id='hit'>none</p>"
            + "<div id='src' style='position:absolute;left:20px;top:260px;width:60px;height:40px;background:#c00'></div>"
            + "<div id='dst' style='position:absolute;left:300px;top:260px;width:100px;height:80px;background:#0c0'></div>"
            + "<p id='drag' style='position:absolute;top:360px'>none</p>"
            + "<script>"
            + "document.getElementById('c').addEventListener('click',e=>{const r=e.target.getBoundingClientRect();"
            + "document.getElementById('hit').textContent=Math.round(e.clientX-r.left)+','+Math.round(e.clientY-r.top);});"
            + "window.__keelProviders={demo:function(el,id){return id==='b1'?{x:10,y:20,w:20,h:40}:null;},"
            + "boom:function(){throw new Error('provider exploded');}};"
            + "let down=null;document.getElementById('src').addEventListener('mousedown',e=>{down=[e.clientX,e.clientY];});"
            + "document.addEventListener('mouseup',e=>{if(!down)return;const r=document.getElementById('dst').getBoundingClientRect();"
            + "document.getElementById('drag').textContent=(e.clientX>=r.left&&e.clientX<=r.right&&e.clientY>=r.top&&e.clientY<=r.bottom)"
            + "?'dropped '+Math.round(e.clientX-r.left)+','+Math.round(e.clientY-r.top):'missed';down=null;});"
            + "</script></body></html>";

    private HttpServer server;
    private String base;
    private WebDriver driver;
    private URLClassLoader loader;
    private Class<?> keelLocate;
    private Class<?> keelBy;
    private JSONObject vectors;

    @BeforeClass
    public void start() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/keel/contract-v2-vectors.json")) {
            vectors = new JSONObject(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            String p = ex.getRequestURI().getPath();
            byte[] b;
            if (p.equals("/canvas.html")) {
                b = CANVAS_PAGE.getBytes(StandardCharsets.UTF_8);
            } else if (p.equals("/blank.html")) {
                b = "<!doctype html><html><body></body></html>".getBytes(StandardCharsets.UTF_8);
            } else {
                try (InputStream in = getClass().getResourceAsStream("/fixtures/a2" + p)) {
                    if (in == null) { ex.sendResponseHeaders(404, -1); ex.close(); return; }
                    b = in.readAllBytes();
                }
            }
            ex.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            ex.sendResponseHeaders(200, b.length);
            ex.getResponseBody().write(b);
            ex.close();
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort() + "/";

        Path classes = Files.createTempDirectory("keel-v2");
        Path pkg = Path.of("customer-framework-template/src/main/java/project/utils/locators");
        loader = CustomerTemplateCompiler.compile(classes, pkg.resolve("KeelLocate.java"), pkg.resolve("KeelBy.java"),
                pkg.resolve("KeelHook.java"), pkg.resolve("KeelActions.java"), pkg.resolve("SmartWait.java"));
        Files.createDirectories(classes.resolve("keel"));
        for (String r : List.of("closed-shadow-hook.js", "keel-text-match.js")) {
            Files.copy(Path.of("customer-framework-template/src/main/resources/keel", r), classes.resolve("keel").resolve(r));
        }
        keelLocate = loader.loadClass("project.utils.locators.KeelLocate");
        keelBy = loader.loadClass("project.utils.locators.KeelBy");
        try {
            ChromeOptions o = new ChromeOptions();
            o.addArguments("--headless=new", "--no-sandbox", "--disable-gpu", "--window-size=1280,900");
            driver = new ChromeDriver(o);
        } catch (RuntimeException e) {
            throw new SkipException("Chrome unavailable: " + e.getMessage());
        }
        Assert.assertTrue((boolean) loader.loadClass("project.utils.locators.KeelHook")
                .getMethod("install", WebDriver.class).invoke(null, driver));
    }

    @AfterClass(alwaysRun = true)
    public void stop() throws Exception {
        if (driver != null) driver.quit();
        if (server != null) server.stop(0);
        if (loader != null) loader.close();
    }

    private Object call(String name, Class<?>[] types, Object... args) {
        try {
            Method m = keelLocate.getMethod(name, types);
            return m.invoke(null, args);
        } catch (InvocationTargetException e) {
            throw (RuntimeException) e.getCause();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private List<?> tParse(String s) {
        return (List<?>) call("parse", new Class<?>[]{String.class}, s);
    }

    private static String outcome(Supplier<Object> f) {
        try {
            return "ok:" + f.get();
        } catch (RuntimeException e) {
            return e.getClass().getName() + ": " + e.getMessage();
        }
    }

    @Test
    public void contractVersionsAgree() throws Exception {
        Assert.assertEquals(keelLocate.getField("CONTRACT_VERSION").getInt(null), KeelTarget.CONTRACT_VERSION);
        Assert.assertEquals(LocatorCandidate.CONTRACT_VERSION, KeelTarget.CONTRACT_VERSION);
        Assert.assertEquals(vectors.getInt("contract"), 2);
        Assert.assertEquals(Files.readString(Path.of("customer-framework-template/src/main/resources/keel/keel-text-match.js")),
                Files.readString(Path.of("src/main/resources/keel/keel-text-match.js")), "matcher shipped byte-identical");
    }

    @Test
    public void parseVectors() throws Exception {
        JSONArray arr = vectors.getJSONArray("parse");
        for (int i = 0; i < arr.length(); i++) {
            JSONObject v = arr.getJSONObject(i);
            List<?> hops = tParse(v.getString("in"));
            JSONArray want = v.getJSONArray("hops");
            Assert.assertEquals(hops.size(), want.length(), v.toString());
            for (int k = 0; k < hops.size(); k++) {
                Object h = hops.get(k);
                String kind = h.getClass().getMethod("prefix").invoke(h).toString().replace("=", "");
                Assert.assertEquals(kind, want.getJSONArray(k).getString(0), v.toString());
                Assert.assertEquals(h.getClass().getMethod("value").invoke(h), want.getJSONArray(k).getString(1), v.toString());
            }
            Assert.assertEquals(call("format", new Class<?>[]{List.class}, hops), v.getString("format"));
            Assert.assertEquals(call("requiredVersion", new Class<?>[]{List.class}, hops), v.getInt("version"));
            Assert.assertEquals(call("format", new Class<?>[]{List.class}, tParse(v.getString("format"))), v.getString("format"), "round trip");
            Assert.assertEquals(KeelTarget.parse(v.getString("in")).format(), v.getString("format"), "engine agrees");
        }
    }

    @Test
    public void invalidVectorsThrowTheSameErrorAsTheEngine() {
        JSONArray arr = vectors.getJSONArray("invalid");
        for (int i = 0; i < arr.length(); i++) {
            String s = arr.getString(i);
            String ref = outcome(() -> KeelTarget.parse(s));
            String got = outcome(() -> tParse(s));
            Assert.assertFalse(ref.startsWith("ok:"), "engine must reject: " + s);
            Assert.assertEquals(got, ref, "invalid: " + s);
        }
    }

    @Test
    public void normalizeVectorsThroughTheShippedMatcher() throws Exception {
        driver.get(base + "blank.html");
        String js = keelLocate.getField("TEXT_MATCH_JS").get(null) + "\nreturn keelNormalizeText(arguments[0]);";
        JSONArray arr = vectors.getJSONArray("normalize");
        for (int i = 0; i < arr.length(); i++) {
            JSONObject v = arr.getJSONObject(i);
            Assert.assertEquals(((JavascriptExecutor) driver).executeScript(js, v.getString("in")), v.getString("out"), v.toString());
        }
    }

    @Test
    public void matchVectorsSameCountAsEngine() {
        JSONArray arr = vectors.getJSONArray("match");
        for (int i = 0; i < arr.length(); i++) {
            JSONObject v = arr.getJSONObject(i);
            driver.get(base + "blank.html");
            ((JavascriptExecutor) driver).executeScript("document.body.innerHTML=arguments[0]", v.getString("html"));
            String target = "text=" + JSONObject.quote(v.getString("text"));
            int ref = KeelTargetResolver.findAll(driver, KeelTarget.parse(target)).size();
            int got = ((List<?>) call("findAll", new Class<?>[]{WebDriver.class, List.class}, driver, tParse(target))).size();
            Assert.assertEquals(ref, v.getInt("count"), "engine vs vector " + v);
            Assert.assertEquals(got, v.getInt("count"), "template vs vector " + v);
            String refOne = outcome(() -> KeelTargetResolver.find(driver, KeelTarget.parse(target)).getTagName());
            String gotOne = outcome(() -> ((WebElement) call("find", new Class<?>[]{WebDriver.class, String.class}, driver, target)).getTagName());
            Assert.assertEquals(gotOne, refOne, "find outcome " + v);
        }
    }

    @Test
    public void pointVectors() throws Exception {
        Class<?> point = loader.loadClass("project.utils.locators.KeelLocate$Point");
        JSONArray arr = vectors.getJSONArray("point");
        for (int i = 0; i < arr.length(); i++) {
            JSONObject v = arr.getJSONObject(i);
            Object p = point.getMethod("parse", String.class).invoke(null, v.getString("point"));
            double[] d = (double[]) point.getMethod("offsetFromCentre", double.class, double.class)
                    .invoke(p, v.getJSONArray("current").getDouble(0), v.getJSONArray("current").getDouble(1));
            Assert.assertEquals(d[0], v.getJSONArray("offset").getDouble(0), 1e-9, v.toString());
            Assert.assertEquals(d[1], v.getJSONArray("offset").getDouble(1), 1e-9, v.toString());
        }
    }

    @Test
    public void sanitizerVectorsGateCodegenTextLiterals() {
        JSONArray arr = vectors.getJSONArray("sanitizer");
        for (int i = 0; i < arr.length(); i++) {
            JSONObject v = arr.getJSONObject(i);
            String t = "text=" + JSONObject.quote(v.getString("text"));
            String emitted = outcome(() -> LocatorCandidate.of("keel", t).toTarget());
            boolean allowed = !emitted.equals("ok:null");
            Assert.assertEquals(allowed, v.getBoolean("allowed"), v + " -> " + emitted);
        }
    }

    private String elKey(WebElement e) {
        return e.getTagName() + "|" + e.getText() + "|" + ((RemoteWebElement) e).getId();
    }

    @Test
    public void textInFramesAndShadowRootsResolvesIdentically() {
        String[][] cases = {
                {"frames.html", "frame=#outer >> frame=iframe[name=\"inner\"] >> text=\"Pay now\""},
                {"frames.html", "frame=#outer >> text=\"Level one\""},
                {"frames.html", "text=\"Level one\""},                         // not pierced: 0
                {"shadow.html", "shadow-closed=#c >> text=\"Closed action\""},
                {"shadow.html", "shadow=#o >> text=\"Open action\""},
                {"shadow.html", "shadow=#o >> frame=#sf >> text=\"Frame in shadow\""},
                {"shadow.html", "shadow-closed=#c >> shadow=x-inner >> text=\"Nested action\""},
                {"shadow.html", "shadow-closed=#c >> text=\"Nested action\""},   // nested root not pierced
                {"sidebar.html", "text=\"Item 50\""},
        };
        for (String[] c : cases) {
            driver.get(base + c[0]);
            String ref = outcome(() -> elKey(KeelTargetResolver.find(driver, KeelTarget.parse(c[1]))));
            driver.switchTo().defaultContent();
            String got = outcome(() -> elKey((WebElement) call("find", new Class<?>[]{WebDriver.class, String.class}, driver, c[1])));
            driver.switchTo().defaultContent();
            Assert.assertEquals(got, ref, c[0] + " " + c[1]);
        }
    }

    @Test
    public void providerAndPointClickPointsMatchEngine() throws Exception {
        driver.get(base + "canvas.html");
        String[] targets = {
                "css=#c >> provider=demo:b1",
                "css=#c >> provider=demo:missing >> point=400,200@800x400",
                "css=#c >> provider=boom:x >> point=100,50@800x400",
                "css=#c >> provider=nope:x >> point=0,0@100x100",
                "css=#c >> point=100,50@800x400",
                "css=#c",
                "css=#c >> provider=demo:missing",
                "css=#c >> provider=boom:x",
                "css=#c >> provider=nope:x",
        };
        for (String t : targets) {
            String ref = outcome(() -> {
                var p = KeelTargetResolver.resolveClick(driver, KeelTarget.parse(t));
                return p.dxFromCentre() + "," + p.dyFromCentre();
            });
            String got = outcome(() -> {
                Object p = call("resolveClick", new Class<?>[]{WebDriver.class, String.class}, driver, t);
                try {
                    return p.getClass().getMethod("dxFromCentre").invoke(p) + "," + p.getClass().getMethod("dyFromCentre").invoke(p);
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(e);
                }
            });
            Assert.assertEquals(got.lines().findFirst().orElse(""), ref.lines().findFirst().orElse(""), t);
        }
    }

    private By keelBy(String... t) throws Exception {
        return (By) keelBy.getMethod("of", String.class, String[].class).invoke(null, "t", (Object) t);
    }

    private void actions(String m, Object... args) throws Throwable {
        Class<?> ka = loader.loadClass("project.utils.locators.KeelActions");
        Class<?>[] types = new Class<?>[args.length];
        types[0] = WebDriver.class;
        for (int i = 1; i < args.length; i++) types[i] = By.class;
        try {
            ka.getMethod(m, types).invoke(null, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    @Test
    public void keelActionsClickAtProviderAndPointOffsetsWithFallback() throws Throwable {
        driver.get(base + "canvas.html");
        actions("click", driver, keelBy("css=#c >> provider=demo:b1"));
        Assert.assertEquals(driver.findElement(By.id("hit")).getText(), "20,40");   // centre of {10,20,20,40}
        actions("click", driver, keelBy("css=#nope", "css=#c >> point=100,50@800x400"));
        Assert.assertEquals(driver.findElement(By.id("hit")).getText(), "50,25");   // scaled to 400x200
    }

    @Test
    public void keelActionsDragFromSourceToDropPoint() throws Throwable {
        driver.get(base + "canvas.html");
        actions("drag", driver, keelBy("css=#src"), keelBy("css=#dst >> point=75,60@100x80"));
        Assert.assertEquals(driver.findElement(By.id("drag")).getText(), "dropped 75,60");
    }
}
