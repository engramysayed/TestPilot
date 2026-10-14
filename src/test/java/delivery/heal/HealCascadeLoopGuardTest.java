package delivery.heal;

import delivery.authoring.AuthoringService;
import delivery.authoring.LocalLlmClient;
import delivery.authoring.LocatorValidator;
import delivery.authoring.StepIntentBinder;
import delivery.guard.LoopGuard;
import delivery.settle.SettlePolicy;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.testng.Assert.*;

/** P0: HealCascade stops when A10 LoopGuard terminals fire (incl. UNSAFE_RETRY). */
public class HealCascadeLoopGuardTest {
    @AfterMethod(alwaysRun = true) public void clear() {
        System.clearProperty("delivery.guard.loop");
        System.clearProperty("delivery.cursor-heal.enabled");
    }

    @Test public void unsafeRetryStopsSecondMutatingFailOnSameAction() {
        LoopGuard g = LoopGuard.of(LoopGuard.Loop.HEAL);
        var se = SettlePolicy.SideEffects.MUTATING;
        assertFalse(g.record(new LoopGuard.Step(null, "solve:miss", false, 1, 1, se)).stop());
        var v = g.record(new LoopGuard.Step(null, "solve:miss", false, 1, 1, se));
        assertTrue(v.stop());
        assertEquals(v.terminal(), LoopGuard.Terminal.UNSAFE_RETRY);
    }

    @Test public void crashHtmlAbortsWithoutCallingCursor() {
        System.setProperty("delivery.cursor-heal.enabled", "true");
        AtomicInteger invokes = new AtomicInteger();
        CursorHealClient cursor = new CursorHealClient(true, "node -e \"process.exit(1)\"", 5) {
            @Override public String solve(String a, String b, String c, String d, java.nio.file.Path e, List<String> f) {
                invokes.incrementAndGet();
                return "{\"candidateId\":\"\"}";
            }
        };
        HealCascade cascade = new HealCascade(new AuthoringService(new LocalLlmClient("", ""), new LocatorValidator()), cursor);
        cascade.beginIntent();
        var intent = new StepIntentBinder.IntentLine(StepIntentBinder.IntentKind.CLICK, "Click Save");
        HealResult r = cascade.heal("TC1", intent,
                "<html><body>Aw Snap! page crashed</body></html>", null,
                "renderer crashed", null, false, List.of(), false, List.of(), List.of(), null);
        assertFalse(r.ok());
        assertTrue(r.reason().startsWith("HEAL_ABORTED"), r.reason());
        assertEquals(invokes.get(), 0);
    }

    @Test public void safetyBlockIsAttachedToSolveRequest() {
        System.setProperty("delivery.cursor-heal.enabled", "true");
        String[] seen = {""};
        CursorHealClient cursor = new CursorHealClient(true, "true", 5) {
            @Override public String solve(String a, String b, String c, String d, java.nio.file.Path e, List<String> f) {
                seen[0] = b == null ? "" : b;
                return "";
            }
        };
        String html = "<html><body><button id=\"other\">UniqueSaveNow</button></body></html>";
        HealCascade cascade = new HealCascade(new AuthoringService(new LocalLlmClient("", ""), new LocatorValidator()), cursor);
        cascade.beginIntent();
        var intent = new StepIntentBinder.IntentLine(StepIntentBinder.IntentKind.CLICK, "Click UniqueSaveNow");
        cascade.heal("TC1", intent, html, null, "not found", null, false, List.of(), false,
                List.of(), List.of(), null);
        assertTrue(seen[0].contains("## Safety"), seen[0]);
        assertTrue(seen[0].contains("sideEffects="), seen[0]);
    }
}
