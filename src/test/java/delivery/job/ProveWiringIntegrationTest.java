package delivery.job;

import com.sun.net.httpserver.HttpServer;
import delivery.codegen.ProvenStep;
import delivery.snapshot.PageSerializer;
import drivers.WebDriverFactory;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.testng.Assert.*;

/** Wiring of A2/A3/A7/A9 on the A2/A7/A9 fixtures: flags on change behaviour, flags off do not. */
public class ProveWiringIntegrationTest {
    static final String[] FLAGS = {ProveWiring.SERIALIZER, ProveWiring.RANKER, ProveWiring.EFFECT, ProveWiring.BIND_EVIDENCE};
    private HttpServer server;
    private String base;

    @BeforeClass public void start() throws Exception {
        System.setProperty("delivery.browser.headless", "true");
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            try (InputStream in = getClass().getResourceAsStream(ex.getRequestURI().getPath())) {
                if (in == null) { ex.sendResponseHeaders(404, -1); ex.close(); return; }
                byte[] b = in.readAllBytes();
                ex.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
                ex.sendResponseHeaders(200, b.length);
                ex.getResponseBody().write(b);
            }
            ex.close();
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort() + "/fixtures/";
    }

    @AfterClass(alwaysRun = true) public void stop() { if (server != null) server.stop(0); }

    @AfterMethod(alwaysRun = true) public void clear() { for (String f : FLAGS) System.clearProperty(f); }

    static WebDriverFactory factory() {
        try { return new WebDriverFactory(); } catch (RuntimeException e) { throw new SkipException("browser unavailable: " + e.getMessage()); }
    }

    static ProvenStep click(String strategy, String value) {
        return new ProvenStep("TC1", "Page", "elementAction", "click", strategy, value, "", "", "", true, "t");
    }

    @Test public void flagsOnChangeBehaviour() throws Exception {
        System.setProperty(ProveWiring.SERIALIZER, "v2");
        System.setProperty(ProveWiring.RANKER, "on");
        System.setProperty(ProveWiring.EFFECT, "strict");
        System.setProperty(ProveWiring.BIND_EVIDENCE, "on");
        WebDriverFactory f = factory();
        Path ev = Files.createTempDirectory("keel-wiring-on");
        try {
            // A2: hook installed by the factory -> closed shadow root visible; page model in authoring html
            f.get().get(base + "a2/shadow.html");
            assertTrue(PageSerializer.serialize(f.get(), List.of(), List.of()).closedShadowHook(), "hook installed in driver");
            String html = ProveWiring.authoringHtml(f.get(), "Click the closed button", 80000);
            assertTrue(html.contains("keel-page-model v2"), "v2 model appended");
            assertTrue(html.contains("closedShadowHook=true"));
            assertTrue(html.contains("shadow-closed") || html.contains(">>"), "context-qualified targets present");

            // A7 strict: a click with no effect fails the step; a real one passes
            TcExecutionService exec = new TcExecutionService(f);
            f.get().get(base + "a7/effect.html");
            TcOutcome noop = exec.execute("TC1", List.of(click("id", "noop")), ev);
            assertEquals(noop.status(), TcStatus.TODO);
            assertTrue(noop.failureReason().startsWith("EFFECT_NONE"), noop.failureReason());
            TcOutcome change = exec.execute("TC1", List.of(click("id", "change")), ev);
            assertEquals(change.status(), TcStatus.PASSED, change.failureReason());

            // A3: a positional xpath is replaced by the ranked test-id css
            f.get().get(base + "a2/ranking.html");
            TcOutcome ranked = exec.execute("TC1", List.of(click("xpath", "(//button)[1]")), ev);
            assertEquals(ranked.status(), TcStatus.PASSED, ranked.failureReason());
            ProvenStep p = ranked.provenSteps().get(0);
            assertEquals(p.locatorStrategy(), "css", p.toString());
            assertTrue(p.locatorValue().contains("save"), p.locatorValue());
            assertTrue(p.rationale().contains("was xpath=(//button)[1]"));

            // A9: bundle written on a final bind failure
            f.get().get(base + "a9/evidence.html");
            var dir = ProveWiring.bindFailure(f.get(), ev, "TC1", 2, "Click Sumbit payment", "CLICK", "", "no candidate");
            assertTrue(dir.isPresent());
            assertTrue(Files.exists(dir.get().resolve("evidence.json")));
            assertTrue(Files.exists(dir.get().resolve("candidates.json")));
        } finally { f.quit(); }
    }

    @Test public void flagsOffLeaveBehaviourUnchanged() throws Exception {
        WebDriverFactory f = factory();
        Path ev = Files.createTempDirectory("keel-wiring-off");
        try {
            assertFalse(ProveWiring.serializerV2() || ProveWiring.rankerOn() || ProveWiring.bindEvidenceOn());
            assertEquals(ProveWiring.effectMode(), ProveWiring.EffectMode.OFF);
            f.get().get(base + "a2/shadow.html");
            assertFalse(PageSerializer.serialize(f.get(), List.of(), List.of()).closedShadowHook(), "no hook by default");
            assertFalse(ProveWiring.authoringHtml(f.get(), "x", 80000).contains("keel-page-model"));
            TcExecutionService exec = new TcExecutionService(f);
            f.get().get(base + "a7/effect.html");
            assertEquals(exec.execute("TC1", List.of(click("id", "noop")), ev).status(), TcStatus.PASSED, "no effect check");
            f.get().get(base + "a2/ranking.html");
            ProvenStep p = exec.execute("TC1", List.of(click("xpath", "(//button)[1]")), ev).provenSteps().get(0);
            assertEquals(p.locatorStrategy(), "xpath");
            assertEquals(p.locatorValue(), "(//button)[1]");
            assertTrue(ProveWiring.bindFailure(f.get(), ev, "TC1", 0, "x", "CLICK", "", "r").isEmpty());
            // executed steps keep the Codegen IR fields (candidates, sideEffects) and the secret flag
            var cand = List.of(new delivery.codegen.LocatorCandidate("keel", "css=#change", 0.95));
            f.get().get(base + "a7/effect.html");
            ProvenStep in = new ProvenStep("TC1", "Page", "elementAction", "click", "id", "change", "", "", "", true, "t", "",
                    cand, List.of(), "none", false);
            ProvenStep out = exec.execute("TC1", List.of(in), ev).provenSteps().get(0);
            assertEquals(out.locatorCandidates(), cand);
            assertEquals(out.sideEffects(), "none");
        } finally { f.quit(); }
    }
}
