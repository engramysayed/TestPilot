package delivery.job;

import com.sun.net.httpserver.HttpServer;
import delivery.codegen.ProvenStep;
import drivers.WebDriverFactory;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.testng.Assert.*;

/** r3: ProvePhase (TcExecutionService) records ProvenStep.sideEffects from the bound element. */
public class SideEffectWiringIntegrationTest {
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
        base = "http://127.0.0.1:" + server.getAddress().getPort() + "/fixtures/r3/side-effects.html";
    }

    @AfterClass(alwaysRun = true) public void stop() { if (server != null) server.stop(0); }

    static ProvenStep click(String id) {
        return new ProvenStep("TC1", "Page", "elementAction", "click", "id", id, "", "", "", true, "t");
    }

    @Test public void recordedStepsCarryTheClassifiedSideEffects() throws Exception {
        WebDriverFactory f = ProveWiringIntegrationTest.factory();
        Path ev = Files.createTempDirectory("keel-r3-se");
        try {
            TcExecutionService exec = new TcExecutionService(f);
            java.util.Map<String, String> want = new java.util.LinkedHashMap<>();
            want.put("save", "mutating");      // implicit submit of a POST form
            want.put("go", "none");            // submit of a GET search form
            want.put("details", "none");       // plain link
            want.put("tab2", "none");          // role=tab
            want.put("menu", "none");          // aria-haspopup
            want.put("logout", "mutating");    // href logout
            want.put("mystery", "unknown");    // not provable -> left unknown
            for (var e : want.entrySet()) {
                f.get().get(base);
                TcOutcome o = exec.execute("TC1", List.of(click(e.getKey())), ev);
                assertEquals(o.status(), TcStatus.PASSED, e.getKey() + ": " + o.failureReason());
                assertEquals(o.provenSteps().get(0).sideEffects(), e.getValue(), e.getKey());
            }
            // a value the step already carries is kept
            f.get().get(base);
            ProvenStep in = click("save").withSideEffects("none");
            assertEquals(exec.execute("TC1", List.of(in), ev).provenSteps().get(0).sideEffects(), "none");
            // type into a field inside a POST form: table default for type decides (not overwritten to mutating by the form)
            f.get().get(base);
            ProvenStep type = new ProvenStep("TC1", "Page", "elementAction", "type", "id", "email", "a@b.c", "", "", true, "t");
            String se = exec.execute("TC1", List.of(type), ev).provenSteps().get(0).sideEffects();
            assertTrue(ProvenStep.SIDE_EFFECTS_VALUES.contains(se), se);
        } finally { f.quit(); }
    }
}
