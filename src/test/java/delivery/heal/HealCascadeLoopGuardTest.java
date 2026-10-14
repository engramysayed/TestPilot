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

    @Test
    public void emptyHtmlPathInitializesGuardBeforeInvent() {
        System.setProperty("delivery.guard.loop", "on");
        AtomicInteger inventCalls = new AtomicInteger();
        CursorHealClient cursor = new CursorHealClient(true, "true", 5) {
            @Override
            public String inventSteps(String intentText, String failureReason, List<String> priorSteps,
                                      String slimHtmlExcerpt, java.nio.file.Path screenshotPathOrNull) {
                inventCalls.incrementAndGet();
                return "{\"steps\":[]}";
            }
        };
        HealCascade cascade = new HealCascade(
                new AuthoringService(new LocalLlmClient("", ""), new LocatorValidator()), cursor);
        cascade.beginIntent();
        var intent = new StepIntentBinder.IntentLine(StepIntentBinder.IntentKind.CLICK, "Click Submit");
        // FreeInventHealer skips blank HTML without open-path; still must not bypass guard init.
        HealResult r = cascade.heal("TC_EMPTY", intent, "", null, "no html", null,
                false, List.of(), true, List.of(), List.of(), null);
        assertFalse(r.ok());
        assertTrue(r.reason().contains("HEAL_EXHAUSTED") || r.reason().startsWith("HEAL_STOPPED"), r.reason());
        assertEquals(inventCalls.get(), 0, "blank HTML invent skipped without open-path");
    }

    @Test
    public void inventSuccessRecordsLoopGuardAndStopsRepeats() {
        System.setProperty("delivery.guard.loop", "on");
        System.setProperty("delivery.cursor-heal.enabled", "true");
        AtomicInteger inventCalls = new AtomicInteger();
        CursorHealClient cursor = new CursorHealClient(true, "true", 5) {
            @Override
            public String solve(String a, String b, String c, String d, java.nio.file.Path e, List<String> f) {
                return "";
            }

            @Override
            public String inventSteps(String intentText, String failureReason, List<String> priorSteps,
                                      String slimHtmlExcerpt, java.nio.file.Path screenshotPathOrNull) {
                inventCalls.incrementAndGet();
                return "{\"steps\":[{\"action\":\"click\",\"locatorStrategy\":\"id\","
                        + "\"locatorValue\":\"submit\",\"value\":\"\",\"assertionType\":\"\",\"assertionExpected\":\"\"}]}";
            }
        };
        HealCascade cascade = new HealCascade(
                new AuthoringService(new LocalLlmClient("", ""), new LocatorValidator()),
                cursor,
                new FreeInventHealer(cursor, null, new LocatorValidator(), "cursor", true),
                true);
        cascade.beginIntent();
        var intent = new StepIntentBinder.IntentLine(StepIntentBinder.IntentKind.CLICK, "Click Submit");
        String html = "<body><button id=\"submit\">Submit</button></body>";
        HealResult first = cascade.heal("TC_INV_1", intent, html, null, "pick failed", null,
                false, List.of(), true, List.of(), List.of(), null);
        assertTrue(first.ok(), first.reason());
        assertEquals(first.tierUsed(), "invent");
        assertEquals(inventCalls.get(), 1);

        HealResult last = first;
        for (int i = 2; i <= 5; i++) {
            last = cascade.heal("TC_INV_" + i, intent, html, null, "pick failed", null,
                    false, List.of(), true, List.of(), List.of(), null);
        }
        assertTrue(last.reason().startsWith("HEAL_STOPPED") || inventCalls.get() <= 3,
                "expected guard stop or invent capped, got reason=" + last.reason()
                        + " inventCalls=" + inventCalls.get());
        assertTrue(inventCalls.get() >= 1);
        assertTrue(inventCalls.get() <= 3,
                "LoopGuard must record invent and stop repeats; calls=" + inventCalls.get());
    }
}
