package delivery.authoring;

import delivery.codegen.ProvenStep;
import delivery.heal.CursorHealClient;
import delivery.authoring.GroundRankBatchResult;
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
    public void bindIntentBatchConsumesOneCallForMultipleIntents() {
        String html = """
                <input data-axis-test-id='username_Input'/>
                <input data-axis-test-id='password-input'/>
                <button data-axis-test-id='sign_In_Button'>Sign in</button>
                """;
        var candidates = DomCandidateExtractor.extract(html);
        String userId = candidates.stream().filter(c -> c.value().contains("username_Input"))
                .findFirst().orElseThrow().id();
        String passId = candidates.stream().filter(c -> c.value().contains("password-input"))
                .findFirst().orElseThrow().id();
        String signId = candidates.stream().filter(c -> c.value().contains("sign_In_Button"))
                .findFirst().orElseThrow().id();
        CursorHealClient cursor = new CursorHealClient(true, "node noop", 1) {
            @Override
            public boolean isRuntimeReady() {
                return true;
            }

            @Override
            public GroundRankBatchResult groundRankBatchResult(
                    String intentsBlock,
                    String slimHtmlExcerpt,
                    java.nio.file.Path screenshotPathOrNull,
                    List<String> priorSteps
            ) {
                return GroundRankBatchResult.parse("""
                        {"picks":[
                          {"intentIndex":0,"candidateId":"%s","confidence":"high"},
                          {"intentIndex":1,"candidateId":"%s","confidence":"high"},
                          {"intentIndex":2,"candidateId":"%s","confidence":"high"}
                        ]}""".formatted(userId, passId, signId));
            }
        };
        PrecisionBindService service = new PrecisionBindService(
                new AuthoringService(new LocalLlmClient("", ""), new LocatorValidator()),
                cursor,
                new PrecisionCallBudget(3),
                true);
        List<StepIntentBinder.IntentLine> intents = List.of(
                new StepIntentBinder.IntentLine(StepIntentBinder.IntentKind.TYPE_USER, "Enter username"),
                new StepIntentBinder.IntentLine(StepIntentBinder.IntentKind.TYPE_PASS, "Enter password"),
                new StepIntentBinder.IntentLine(StepIntentBinder.IntentKind.CLICK_LOGIN, "Click Sign in"));
        var probes = new java.util.concurrent.atomic.AtomicInteger();
        delivery.heal.CandidateLivenessProbe probe = candidate -> {
            probes.incrementAndGet();
            return new delivery.heal.CandidateLiveness(true, !candidate.tag().equals("button"), 20, 20);
        };
        PrecisionBatchBindOutcome outcome = service.bindIntentBatch(
                "TC_01", intents, html, null, List.of(), List.of(), List.of(), probe);
        Assert.assertTrue(outcome instanceof PrecisionBatchBindOutcome.BatchBound);
        Assert.assertEquals(3, ((PrecisionBatchBindOutcome.BatchBound) outcome).boundInOrder().size());
        Assert.assertEquals(1, service.callsUsed());
        int distinctSelectors = (int) candidates.stream().map(c -> List.of(c.strategy(), c.value())).distinct().count();
        Assert.assertEquals(probes.get(), distinctSelectors, "one probe per selector, not per intent");
        service.bindIntentBatch("TC_02", intents, html, null, List.of(), List.of(), List.of(), probe);
        Assert.assertEquals(probes.get(), distinctSelectors * 2, "a new snapshot must never reuse old liveness");
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

    @Test public void malformedBatchIndicesAndUnknownCandidatesFailClosed() {
        String html = "<input id='email' aria-label='Email'><input id='password' type='password'>";
        for (var picks : List.of(
                List.of(new GroundRankBatchResult.Pick(99, "c1", "high")),
                List.of(new GroundRankBatchResult.Pick(0, "c1", "high"), new GroundRankBatchResult.Pick(0, "c2", "high")),
                List.of(new GroundRankBatchResult.Pick(0, "not-offered", "high")))) {
            var cursor = new CursorHealClient(true, "node noop", 1) {
                @Override public boolean isRuntimeReady() { return true; }
                @Override public GroundRankBatchResult groundRankBatchResult(String block, String excerpt,
                        java.nio.file.Path shot, List<String> prior) { return new GroundRankBatchResult(picks, ""); }
            };
            var service = new PrecisionBindService(new AuthoringService(new LocalLlmClient("", ""), new LocatorValidator()),
                    cursor, new PrecisionCallBudget(2), true);
            var result = service.bindIntentBatch("TC", List.of(
                    new StepIntentBinder.IntentLine(StepIntentBinder.IntentKind.TYPE_USER, "Enter email"),
                    new StepIntentBinder.IntentLine(StepIntentBinder.IntentKind.TYPE_PASS, "Enter password")),
                    html, null, List.of(), List.of(), List.of(), null);
            Assert.assertTrue(result instanceof PrecisionBatchBindOutcome.FallbackKeel, result.toString());
        }
    }

    @Test public void exhaustedBudgetDoesNotProbeTheBrowser() {
        var cursor = new CursorHealClient(true, "node noop", 1) {
            @Override public boolean isRuntimeReady() { return true; }
        };
        var service = new PrecisionBindService(new AuthoringService(new LocalLlmClient("", ""), new LocatorValidator()),
                cursor, new PrecisionCallBudget(0), true);
        var outcome = service.bindIntent("TC", new StepIntentBinder.IntentLine(StepIntentBinder.IntentKind.CLICK, "Click Save"),
                "<button id='save'>Save</button>", null, List.of(), List.of(), List.of(),
                candidate -> { throw new AssertionError("budget exhausted: browser should not be probed"); });
        Assert.assertEquals(((PrecisionBindOutcome.FallbackKeel) outcome).reason(), "CAP_EXCEEDED");
    }

    @Test public void textEntryDoesNotProbeIrrelevantButtons() {
        StringBuilder html = new StringBuilder("<input id='username' aria-label='Username'>");
        for (int i = 0; i < 100; i++) html.append("<button id='action-").append(i).append("'>Action</button>");
        var candidates = DomCandidateExtractor.extract(html.toString());
        String chosen = candidates.stream().filter(c -> c.value().equals("username")).findFirst().orElseThrow().id();
        var cursor = new CursorHealClient(true, "node noop", 1) {
            @Override public boolean isRuntimeReady() { return true; }
            @Override public GroundRankResult groundRankResult(String intent, String table, String excerpt,
                    java.nio.file.Path shot, List<String> prior) {
                Assert.assertFalse(table.contains("action-"), "incompatible controls must not reach model shortlist");
                return new GroundRankResult(chosen, "high", "");
            }
        };
        var service = new PrecisionBindService(new AuthoringService(new LocalLlmClient("", ""), new LocatorValidator()),
                cursor, new PrecisionCallBudget(1), true);
        var probes = new java.util.concurrent.atomic.AtomicInteger();
        var result = service.bindIntent("TC", new StepIntentBinder.IntentLine(StepIntentBinder.IntentKind.TYPE_USER, "Enter username"),
                html.toString(), null, List.of(), List.of(), List.of(), c -> {
                    probes.incrementAndGet();
                    Assert.assertEquals(c.tag(), "input");
                    return new delivery.heal.CandidateLiveness(true, true, 30, 20);
                });
        Assert.assertTrue(result instanceof PrecisionBindOutcome.Bound, result.toString());
        Assert.assertEquals(probes.get(), 1);
        Assert.assertTrue(candidates.size() >= 101);
    }
}
