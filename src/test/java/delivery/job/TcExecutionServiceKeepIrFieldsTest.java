package delivery.job;

import delivery.codegen.LocatorCandidate;
import delivery.codegen.ProvenStep;
import drivers.WebDriverFactory;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.testng.Assert.*;

/**
 * Regression for 0036 (AE fix-keep-ir-fields): re-recording executed steps in {@link TcExecutionService}
 * must carry locatorCandidates, dropCandidates, sideEffects and secret over from the input IR step.
 * Before 0036 the recorded step used the 13-arg r2 constructor, which reset them to [], [], "unknown".
 */
public class TcExecutionServiceKeepIrFieldsTest {
    static final String PAGE = "<!doctype html><html><body>"
            + "<input id='token' type='text'>"
            + "<button id='go' onclick=\"document.getElementById('out').textContent='ok'\">Go</button>"
            + "<div id='out'></div></body></html>";

    @BeforeClass public void headless() { System.setProperty("delivery.browser.headless", "true"); }

    static WebDriverFactory factory() {
        try { return new WebDriverFactory(); } catch (RuntimeException e) { throw new SkipException("browser unavailable: " + e.getMessage()); }
    }

    @Test public void executedStepsKeepCandidatesDropsSideEffectsAndSecret() throws Exception {
        var typeCands = List.of(new LocatorCandidate("keel", "css=#token", 0.97), new LocatorCandidate("dom", "xpath=//input[1]", 0.6));
        var typeDrops = List.of(new LocatorCandidate("keel", "css=#out", 0.5));
        var clickCands = List.of(new LocatorCandidate("keel", "text=\"Go\"", 0.9));
        // secret on a plain text input: the flag must come from the IR, not from the password-target probe
        ProvenStep type = new ProvenStep("TC9", "Page", "elementAction", "type", "id", "token", "s3cr3t", "", "", true, "t", "",
                typeCands, typeDrops, ProvenStep.SIDE_EFFECTS_MUTATING, true);
        ProvenStep click = new ProvenStep("TC9", "Page", "elementAction", "click", "id", "go", "", "", "", true, "t", "",
                clickCands, List.of(), ProvenStep.SIDE_EFFECTS_NONE, false);

        WebDriverFactory f = factory();
        Path ev = Files.createTempDirectory("keel-keep-ir");
        try {
            f.get().get("data:text/html;charset=utf-8," + URLEncoder.encode(PAGE, StandardCharsets.UTF_8).replace("+", "%20"));
            TcOutcome o = new TcExecutionService(f).execute("TC9", List.of(type, click), ev);
            assertEquals(o.status(), TcStatus.PASSED, String.valueOf(o));
            List<ProvenStep> rec = o.provenSteps();
            assertEquals(rec.size(), 2);

            ProvenStep t = rec.get(0);
            assertEquals(t.locatorCandidates(), typeCands, "type: locatorCandidates");
            assertEquals(t.dropCandidates(), typeDrops, "type: dropCandidates");
            assertEquals(t.sideEffects(), ProvenStep.SIDE_EFFECTS_MUTATING, "type: sideEffects");
            assertTrue(t.secret(), "type: secret");
            assertFalse(t.toString().contains("s3cr3t"), "secret value never rendered");

            ProvenStep c = rec.get(1);
            assertEquals(c.locatorCandidates(), clickCands, "click: locatorCandidates");
            assertEquals(c.dropCandidates(), List.of(), "click: dropCandidates");
            assertEquals(c.sideEffects(), ProvenStep.SIDE_EFFECTS_NONE, "click: sideEffects");
            assertFalse(c.secret(), "click: secret");
        } finally { f.quit(); }
    }
}
