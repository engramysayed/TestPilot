package delivery.authoring;

import delivery.codegen.ProvenStep;
import delivery.heal.CursorHealClient;
import delivery.heal.FailedLocator;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

public class PrecisionBindServiceTest {
    @Test public void writtenLoginClickCannotTargetUsernameInput() {
        var cursor = new CursorHealClient(true, "node noop", 1) {
            @Override public boolean isRuntimeReady() { return true; }
            @Override public String solve(String intent, String reason, String table, String excerpt,
                    java.nio.file.Path screenshot, List<String> prior) {
                return "{\"steps\":[{\"action\":\"click\",\"locatorStrategy\":\"id\",\"locatorValue\":\"username\"}]}";
            }
        };
        var service = new PrecisionBindService(new AuthoringService(new LocalLlmClient("", ""),
                new LocatorValidator()), cursor, new PrecisionCallBudget(2), true);
        var outcome = service.solveOnce("TC", new StepIntentBinder.IntentLine(StepIntentBinder.IntentKind.CLICK_LOGIN, "Click Login"),
                List.of(), List.of(), "", "<input id='username' aria-label='Login'>", null, List.of());
        Assert.assertTrue(outcome instanceof PrecisionBindOutcome.FallbackKeel, outcome.toString());
    }
    @Test public void thrownProviderErrorProducesExplicitFallback() {
        var cursor = new CursorHealClient(true, "node noop", 1) {
            @Override public boolean isRuntimeReady() { return true; }
            @Override public GroundRankResult groundRankResult(String intent, String table, String excerpt,
                    java.nio.file.Path screenshot, List<String> prior) { throw new IllegalStateException("offline"); }
        };
        var service = new PrecisionBindService(new AuthoringService(new LocalLlmClient("", ""),
                new LocatorValidator()), cursor, new PrecisionCallBudget(2), true);
        var outcome = service.bindIntent("TC", new StepIntentBinder.IntentLine(StepIntentBinder.IntentKind.CLICK, "Save"),
                "<button id='save'>Save</button>", null, List.of(), List.of(), List.of(), null);
        Assert.assertEquals(((PrecisionBindOutcome.FallbackKeel) outcome).reason(), "PROVIDER_ERROR");
    }
    @Test
    public void cursorChoiceIsNotReplacedByDeterministicFavorite() {
        String html = "<button id='save-primary'>Save</button><button id='save-secondary'>Save</button>";
        var candidates = DomCandidateExtractor.extract(html);
        String selected = candidates.stream().filter(c -> c.value().equals("save-secondary"))
                .findFirst().orElseThrow().id();
        CursorHealClient cursor = new CursorHealClient(true, "node noop", 1) {
            @Override public boolean isRuntimeReady() { return true; }
            @Override public GroundRankResult groundRankResult(String intent, String table, String excerpt,
                    java.nio.file.Path screenshot, List<String> prior) {
                return new GroundRankResult(selected, "high", "chosen by Cursor");
            }
        };
        var service = new PrecisionBindService(new AuthoringService(new LocalLlmClient("", ""),
                new LocatorValidator()), cursor, new PrecisionCallBudget(2), true);
        var outcome = service.bindIntent("TC", new StepIntentBinder.IntentLine(
                StepIntentBinder.IntentKind.CLICK, "Click Save"), html, null,
                List.of(), List.of(), List.of(), null);
        Assert.assertTrue(outcome instanceof PrecisionBindOutcome.Bound, outcome.toString());
        Assert.assertEquals(((PrecisionBindOutcome.Bound) outcome).steps().getFirst().locatorValue(), "save-secondary");
        Assert.assertEquals(service.callsUsed(), 1);
    }

    @Test
    public void preferredLoginCandidateCannotBeATextbox() {
        var service = new AuthoringService(new LocalLlmClient("", ""), new LocatorValidator());
        var steps = service.stepsPreferringCandidate("TC", new StepIntentBinder.IntentLine(
                StepIntentBinder.IntentKind.CLICK_LOGIN, "Click Login"),
                List.of(new DomCandidate("c1", "id", "username", "input", "Login")), "c1", true);
        Assert.assertTrue(steps.isEmpty() || steps.stream().noneMatch(ProvenStep::validated));
    }

    @Test
    public void disabledPrecisionFallsBackImmediatelyWithoutConsumingBudget() {
        PrecisionCallBudget budget = new PrecisionCallBudget(5);
        PrecisionBindService service = new PrecisionBindService(
                null, new CursorHealClient(false, "node noop", 1),
                budget, false);
        PrecisionBindOutcome outcome = service.bindIntent(
                "TC1",
                new StepIntentBinder.IntentLine(StepIntentBinder.IntentKind.CLICK, "Sign In"),
                "<button id='signin'>Sign In</button>",
                null,
                List.of(),
                List.of(),
                List.of(),
                null);
        Assert.assertTrue(outcome instanceof PrecisionBindOutcome.FallbackKeel);
        Assert.assertEquals("PROVIDER_UNAVAILABLE",
                ((PrecisionBindOutcome.FallbackKeel) outcome).reason());
        Assert.assertEquals(0, budget.used());
    }

    @Test
    public void capExceededFallsBackWithoutCallingCursor() {
        PrecisionCallBudget budget = new PrecisionCallBudget(0);
        PrecisionBindService service = new PrecisionBindService(
                new AuthoringService(new LocalLlmClient("http://127.0.0.1:11434", "model"),
                        new LocatorValidator()),
                new CursorHealClient(true, "node noop", 1) {
                    @Override
                    public boolean isRuntimeReady() {
                        return true;
                    }
                },
                budget,
                true);
        PrecisionBindOutcome outcome = service.bindIntent(
                "TC1",
                new StepIntentBinder.IntentLine(StepIntentBinder.IntentKind.CLICK, "Sign In"),
                "<button id='signin'>Sign In</button>",
                null,
                List.of(),
                List.of(),
                List.<FailedLocator>of(),
                null);
        Assert.assertTrue(outcome instanceof PrecisionBindOutcome.FallbackKeel);
        Assert.assertEquals("CAP_EXCEEDED",
                ((PrecisionBindOutcome.FallbackKeel) outcome).reason());
    }

    @Test
    public void missingApiKeyDoesNotConsumeBudget() {
        PrecisionCallBudget budget = new PrecisionCallBudget(5);
        PrecisionBindService service = new PrecisionBindService(
                new AuthoringService(new LocalLlmClient("http://127.0.0.1:11434", "model"),
                        new LocatorValidator()),
                new CursorHealClient(true, "node noop", 1),
                budget,
                true);
        PrecisionBindOutcome outcome = service.bindIntent(
                "TC1",
                new StepIntentBinder.IntentLine(StepIntentBinder.IntentKind.CLICK, "Sign In"),
                "<button id='signin'>Sign In</button>",
                null,
                List.of(),
                List.of(),
                List.of(),
                null);
        Assert.assertTrue(outcome instanceof PrecisionBindOutcome.FallbackKeel);
        Assert.assertEquals("PROVIDER_UNAVAILABLE",
                ((PrecisionBindOutcome.FallbackKeel) outcome).reason());
        Assert.assertEquals(0, budget.used());
    }

    @Test
    public void emptyHtmlDoesNotConsumeBudget() {
        PrecisionCallBudget budget = new PrecisionCallBudget(5);
        PrecisionBindService service = new PrecisionBindService(
                new AuthoringService(new LocalLlmClient("http://127.0.0.1:11434", "model"),
                        new LocatorValidator()),
                new CursorHealClient(true, "node noop", 1) {
                    @Override
                    public boolean isRuntimeReady() {
                        return true;
                    }
                },
                budget,
                true);
        PrecisionBindOutcome outcome = service.bindIntent(
                "TC1",
                new StepIntentBinder.IntentLine(StepIntentBinder.IntentKind.CLICK, "Sign In"),
                "",
                null,
                List.of(),
                List.of(),
                List.of(),
                null);
        Assert.assertTrue(outcome instanceof PrecisionBindOutcome.FallbackKeel);
        Assert.assertEquals(0, budget.used());
    }

    @Test
    public void solveOnceAcceptsWrittenStepsJson() {
        PrecisionCallBudget budget = new PrecisionCallBudget(2);
        CursorHealClient cursor = new CursorHealClient(true, "node noop", 1) {
            @Override
            public boolean isRuntimeReady() {
                return true;
            }

            @Override
            public String solve(
                    String intentText,
                    String failureReason,
                    String shortlistTable,
                    String slimHtmlExcerpt,
                    java.nio.file.Path screenshotPathOrNull,
                    List<String> priorSteps
            ) {
                return "{\"steps\":[{\"action\":\"click\",\"locatorStrategy\":\"id\","
                        + "\"locatorValue\":\"signin\",\"value\":\"\",\"assertionType\":\"\","
                        + "\"assertionExpected\":\"\"}]}";
            }
        };
        PrecisionBindService service = new PrecisionBindService(
                new AuthoringService(new LocalLlmClient("http://127.0.0.1:11434", "model"),
                        new LocatorValidator()),
                cursor,
                budget,
                true);
        PrecisionBindOutcome outcome = service.solveOnce(
                "TC1",
                new StepIntentBinder.IntentLine(StepIntentBinder.IntentKind.CLICK, "Sign In"),
                List.of(),
                List.of(),
                "| id | css | #signin | button | Sign In |",
                "<button id='signin'>Sign In</button>",
                null,
                List.of());
        Assert.assertTrue(outcome instanceof PrecisionBindOutcome.Bound);
        List<ProvenStep> steps = ((PrecisionBindOutcome.Bound) outcome).steps();
        Assert.assertFalse(steps.isEmpty());
        Assert.assertEquals(1, budget.used());
    }
}
