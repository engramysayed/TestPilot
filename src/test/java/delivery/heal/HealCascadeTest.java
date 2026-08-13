package delivery.heal;

import delivery.authoring.AuthoringService;
import delivery.authoring.DomCandidate;
import delivery.authoring.DomCandidateExtractor;
import delivery.authoring.LocalLlmClient;
import delivery.authoring.LocatorValidator;
import delivery.authoring.StepIntentBinder;
import delivery.codegen.ProvenStep;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class HealCascadeTest {

    private static final String HTML = """
            <body>
              <input id="username" name="username" />
              <input id="password" type="password" name="password" />
              <button id="submit">Submit</button>
              <a id="other">Other</a>
            </body>
            """;

    @Test
    public void ollamaPickThenSkipsCursor() {
        AtomicInteger cursorCalls = new AtomicInteger();
        LocalLlmClient fakeLlm = new LocalLlmClient("http://127.0.0.1:9", "dummy") {
            @Override
            public String completeJson(String system, String user) {
                List<DomCandidate> all = DomCandidateExtractor.extract(HTML);
                DomCandidate submit = all.stream()
                        .filter(c -> "submit".equalsIgnoreCase(c.value()) || c.label().toLowerCase().contains("submit"))
                        .findFirst()
                        .orElse(all.get(0));
                return "{\"candidateId\":\"" + submit.id() + "\"}";
            }

            @Override
            public String completeJson(String system, String user, byte[] imagePng) {
                return completeJson(system, user);
            }
        };
        AuthoringService authoring = new AuthoringService(fakeLlm, new LocatorValidator());
        CursorHealClient cursor = new CursorHealClient(true, "node -e \"process.exit(1)\"", 5) {
            @Override
            public String pickCandidateId(String intentText, String failureReason, String shortlistTable,
                                          String slimHtmlExcerpt, java.nio.file.Path screenshotPathOrNull,
                                          List<String> priorSteps) {
                cursorCalls.incrementAndGet();
                return super.pickCandidateId(intentText, failureReason, shortlistTable,
                        slimHtmlExcerpt, screenshotPathOrNull, priorSteps);
            }
        };
        HealCascade cascade = new HealCascade(authoring, cursor);
        StepIntentBinder.IntentLine intent = new StepIntentBinder.IntentLine(
                StepIntentBinder.IntentKind.CLICK_LOGIN, "Click the Submit button");
        HealResult result = cascade.heal("TC1", intent, HTML, new byte[]{1, 2, 3},
                "execute failed", null, true);
        Assert.assertTrue(result.ok(), result.reason());
        Assert.assertEquals(result.tierUsed(), "ollama");
        Assert.assertEquals(cursorCalls.get(), 0, "Cursor must not run when Ollama succeeds");
        Assert.assertTrue(result.steps().stream().allMatch(ProvenStep::validated));
    }

    @Test
    public void cursorUsedWhenOllamaFails() {
        LocalLlmClient failingLlm = new LocalLlmClient("http://127.0.0.1:9", "dummy") {
            @Override
            public String completeJson(String system, String user) {
                throw new RuntimeException("ollama down");
            }

            @Override
            public String completeJson(String system, String user, byte[] imagePng) {
                throw new RuntimeException("ollama down");
            }
        };
        AuthoringService authoring = new AuthoringService(failingLlm, new LocatorValidator());
        List<DomCandidate> all = DomCandidateExtractor.extract(HTML);
        DomCandidate submit = all.stream()
                .filter(c -> c.value().toLowerCase().contains("submit") || c.label().toLowerCase().contains("submit"))
                .findFirst()
                .orElseThrow();
        CursorHealClient cursor = new CursorHealClient(false, "unused", 5) {
            @Override
            public String pickCandidateId(String intentText, String failureReason, String shortlistTable,
                                          String slimHtmlExcerpt, java.nio.file.Path screenshotPathOrNull,
                                          List<String> priorSteps) {
                return submit.id();
            }
        };
        // Force enabled path via override only — constructor enabled=false unused because we override pick
        HealCascade cascade = new HealCascade(authoring, cursor);
        StepIntentBinder.IntentLine intent = new StepIntentBinder.IntentLine(
                StepIntentBinder.IntentKind.CLICK_LOGIN, "Click the Submit button");
        HealResult result = cascade.heal("TC1", intent, HTML, new byte[0],
                "bind failed", null, true);
        Assert.assertTrue(result.ok(), result.reason());
        Assert.assertEquals(result.tierUsed(), "cursor");
    }

    @Test
    public void exhaustedWhenBothFail() {
        LocalLlmClient failingLlm = new LocalLlmClient("http://127.0.0.1:9", "dummy") {
            @Override
            public String completeJson(String system, String user) {
                return "{\"candidateId\":\"not-in-list\"}";
            }

            @Override
            public String completeJson(String system, String user, byte[] imagePng) {
                return completeJson(system, user);
            }
        };
        AuthoringService authoring = new AuthoringService(failingLlm, new LocatorValidator());
        CursorHealClient cursor = new CursorHealClient(true, "node -e \"process.exit(1)\"", 5);
        HealCascade cascade = new HealCascade(authoring, cursor);
        StepIntentBinder.IntentLine intent = new StepIntentBinder.IntentLine(
                StepIntentBinder.IntentKind.CLICK, "Click nowhere");
        HealResult result = cascade.heal("TC1", intent, HTML, new byte[]{1},
                "fail", null, true);
        Assert.assertFalse(result.ok());
        Assert.assertTrue(result.reason().contains("HEAL_EXHAUSTED")
                || result.reason().contains("Cursor"), result.reason());
    }

    @Test
    public void cursorOnlyWhenTryOllamaFalse() {
        AtomicInteger ollamaCalls = new AtomicInteger();
        AtomicInteger cursorCalls = new AtomicInteger();
        java.util.concurrent.atomic.AtomicReference<List<String>> cursorHistory =
                new java.util.concurrent.atomic.AtomicReference<>();
        LocalLlmClient llm = new LocalLlmClient("http://127.0.0.1:9", "dummy") {
            @Override
            public String completeJson(String system, String user) {
                ollamaCalls.incrementAndGet();
                throw new RuntimeException("should not call ollama");
            }

            @Override
            public String completeJson(String system, String user, byte[] imagePng) {
                return completeJson(system, user);
            }
        };
        AuthoringService authoring = new AuthoringService(llm, new LocatorValidator());
        List<DomCandidate> all = DomCandidateExtractor.extract(HTML);
        DomCandidate submit = all.stream()
                .filter(c -> "submit".equalsIgnoreCase(c.value()) || c.label().toLowerCase().contains("submit"))
                .findFirst()
                .orElse(all.get(0));
        CursorHealClient cursor = new CursorHealClient(true, "unused", 5) {
            @Override
            public String pickCandidateId(String intentText, String failureReason, String shortlistTable,
                                          String slimHtmlExcerpt, java.nio.file.Path screenshotPathOrNull,
                                          List<String> priorSteps) {
                cursorCalls.incrementAndGet();
                cursorHistory.set(priorSteps);
                return submit.id();
            }
        };
        HealCascade cascade = new HealCascade(authoring, cursor);
        StepIntentBinder.IntentLine intent = new StepIntentBinder.IntentLine(
                StepIntentBinder.IntentKind.CLICK_LOGIN, "Click the Submit button");
        HealResult result = cascade.heal("TC1", intent, HTML, new byte[]{1},
                "re-execute after Ollama heal failed", null, false,
                List.of("type id username = alice"));
        Assert.assertTrue(result.ok(), result.reason());
        Assert.assertEquals(result.tierUsed(), "cursor");
        Assert.assertEquals(ollamaCalls.get(), 0);
        Assert.assertEquals(cursorCalls.get(), 1);
        Assert.assertEquals(cursorHistory.get(), List.of("type id username = alice"));
    }

    @Test
    public void rejectsOllamaPickThatSwapsNamedEntity() {
        String catalogHtml = """
                <body>
                  <button id="add-red-backpack">Add</button>
                  <button id="add-blue-lantern">Add</button>
                </body>
                """;
        LocalLlmClient wrongPick = new LocalLlmClient("http://127.0.0.1:9", "dummy") {
            @Override
            public String completeJson(String system, String user) {
                List<DomCandidate> all = DomCandidateExtractor.extract(catalogHtml);
                DomCandidate lantern = all.stream()
                        .filter(c -> c.value().toLowerCase().contains("lantern"))
                        .findFirst()
                        .orElseThrow();
                return "{\"candidateId\":\"" + lantern.id() + "\"}";
            }

            @Override
            public String completeJson(String system, String user, byte[] imagePng) {
                return completeJson(system, user);
            }
        };
        AuthoringService authoring = new AuthoringService(wrongPick, new LocatorValidator());
        AtomicInteger cursorCalls = new AtomicInteger();
        CursorHealClient cursor = new CursorHealClient(true, "unused", 5) {
            @Override
            public String pickCandidateId(String intentText, String failureReason, String shortlistTable,
                                          String slimHtmlExcerpt, java.nio.file.Path screenshotPathOrNull,
                                          List<String> priorSteps) {
                cursorCalls.incrementAndGet();
                // Also try wrong entity — must be rejected
                List<DomCandidate> all = DomCandidateExtractor.extract(catalogHtml);
                return all.stream()
                        .filter(c -> c.value().toLowerCase().contains("lantern"))
                        .findFirst()
                        .orElseThrow()
                        .id();
            }
        };
        // Invent disabled: this test asserts shortlist wrong-entity rejection only
        HealCascade cascade = new HealCascade(
                authoring,
                cursor,
                new FreeInventHealer(cursor, null, new LocatorValidator(), "cursor", false),
                true);
        StepIntentBinder.IntentLine intent = new StepIntentBinder.IntentLine(
                StepIntentBinder.IntentKind.CLICK, "Add the red backpack");
        HealResult result = cascade.heal("TC_ENT", intent, catalogHtml, new byte[]{1},
                "execute failed", null, true);
        Assert.assertFalse(result.ok(), "heal must not accept wrong named entity");
        Assert.assertTrue(result.reason().contains("HEAL_EXHAUSTED")
                        || result.reason().contains("distinctive"),
                result.reason());
        // Cursor may run after Ollama reject, but both wrong picks must fail
        Assert.assertTrue(cursorCalls.get() >= 1);
    }

    @Test
    public void ollamaPickKeepsNamedEntityWhenCorrect() {
        String catalogHtml = """
                <body>
                  <button id="add-red-backpack">Add</button>
                  <button id="add-blue-lantern">Add</button>
                </body>
                """;
        LocalLlmClient rightPick = new LocalLlmClient("http://127.0.0.1:9", "dummy") {
            @Override
            public String completeJson(String system, String user) {
                List<DomCandidate> all = DomCandidateExtractor.extract(catalogHtml);
                DomCandidate backpack = all.stream()
                        .filter(c -> c.value().toLowerCase().contains("backpack"))
                        .findFirst()
                        .orElseThrow();
                return "{\"candidateId\":\"" + backpack.id() + "\"}";
            }

            @Override
            public String completeJson(String system, String user, byte[] imagePng) {
                return completeJson(system, user);
            }
        };
        AuthoringService authoring = new AuthoringService(rightPick, new LocatorValidator());
        AtomicInteger cursorCalls = new AtomicInteger();
        CursorHealClient cursor = new CursorHealClient(true, "unused", 5) {
            @Override
            public String pickCandidateId(String intentText, String failureReason, String shortlistTable,
                                          String slimHtmlExcerpt, java.nio.file.Path screenshotPathOrNull,
                                          List<String> priorSteps) {
                cursorCalls.incrementAndGet();
                return "should-not-run";
            }
        };
        HealCascade cascade = new HealCascade(authoring, cursor);
        StepIntentBinder.IntentLine intent = new StepIntentBinder.IntentLine(
                StepIntentBinder.IntentKind.CLICK, "Add the red backpack");
        HealResult result = cascade.heal("TC_ENT", intent, catalogHtml, new byte[]{1},
                "execute failed", null, true);
        Assert.assertTrue(result.ok(), result.reason());
        Assert.assertEquals(result.tierUsed(), "ollama");
        Assert.assertEquals(cursorCalls.get(), 0);
        Assert.assertTrue(result.steps().get(0).locatorValue().contains("backpack"));
    }

    @Test
    public void passesPriorHistoryToOllamaAndParsesThoughtAction() {
        java.util.concurrent.atomic.AtomicReference<String> prompt = new java.util.concurrent.atomic.AtomicReference<>();
        LocalLlmClient llm = new LocalLlmClient("http://127.0.0.1:9", "dummy") {
            @Override
            public String completeJson(String system, String user) {
                prompt.set(user);
                DomCandidate submit = DomCandidateExtractor.extract(HTML).stream()
                        .filter(c -> c.value().toLowerCase().contains("submit"))
                        .findFirst().orElseThrow();
                return "Thought: the submit control matches.\nAction:\n{\"candidateId\":\"" + submit.id() + "\"}";
            }
        };
        HealCascade cascade = new HealCascade(
                new AuthoringService(llm, new LocatorValidator()),
                new CursorHealClient(false, "unused", 1));
        HealResult result = cascade.heal(
                "TC_HISTORY",
                new StepIntentBinder.IntentLine(StepIntentBinder.IntentKind.CLICK, "Click Submit"),
                HTML, null, "bind failed", null, true,
                List.of("type id username = alice"));

        Assert.assertTrue(result.ok(), result.reason());
        Assert.assertTrue(prompt.get().contains("## Already completed in this TC"));
        Assert.assertTrue(prompt.get().contains("type id username = alice"));
    }

    @Test
    public void widensWeakDistinctivePoolInsteadOfExhaustingBeforeAi() {
        String weakHtml = "<body><button id=\"continue-control\">Proceed</button></body>";
        AtomicInteger llmCalls = new AtomicInteger();
        LocalLlmClient llm = new LocalLlmClient("http://127.0.0.1:9", "dummy") {
            @Override
            public String completeJson(String system, String user, byte[] imagePng) {
                llmCalls.incrementAndGet();
                DomCandidate candidate = DomCandidateExtractor.extract(weakHtml).get(0);
                return "Thought: visible primary action.\n{\"candidateId\":\"" + candidate.id() + "\"}";
            }
        };
        HealCascade cascade = new HealCascade(
                new AuthoringService(llm, new LocatorValidator()),
                new CursorHealClient(false, "unused", 1));

        HealResult result = cascade.heal(
                "TC_WIDEN",
                new StepIntentBinder.IntentLine(StepIntentBinder.IntentKind.CLICK, "Click Hyperdrive"),
                weakHtml, new byte[]{1}, "bind failed", null, true, List.of());

        Assert.assertTrue(llmCalls.get() > 0, "weak distinctive pool must still reach AI");
        Assert.assertFalse(result.reason().contains("no DOM candidate matches distinctive tokens"),
                result.reason());
    }

    @Test
    public void inventRunsAfterCursorPickFails() {
        LocalLlmClient llm = new LocalLlmClient("http://127.0.0.1:9", "dummy") {
            @Override
            public String completeJson(String system, String user) {
                return "{\"candidateId\":\"not-present\"}";
            }
        };
        CursorHealClient cursor = new CursorHealClient(false, "unused", 1) {
            @Override
            public String pickCandidateId(String intentText, String failureReason, String shortlistTable,
                                          String slimHtmlExcerpt, java.nio.file.Path screenshotPathOrNull,
                                          List<String> priorSteps) {
                return "";
            }

            @Override
            public String inventSteps(String intentText, String failureReason, List<String> priorSteps,
                                      String slimHtmlExcerpt, java.nio.file.Path screenshotPathOrNull) {
                return """
                        {"thought":"last hope","steps":[{"action":"click",
                        "locatorStrategy":"id","locatorValue":"submit","value":"",
                        "assertionType":"","assertionExpected":""}]}
                        """;
            }
        };
        FreeInventHealer invent = new FreeInventHealer(
                cursor, null, new LocatorValidator(), "cursor", true);
        HealCascade cascade = new HealCascade(
                new AuthoringService(llm, new LocatorValidator()), cursor, invent, true);

        HealResult result = cascade.heal(
                "TC_INVENT",
                new StepIntentBinder.IntentLine(StepIntentBinder.IntentKind.CLICK, "Click Submit"),
                HTML, null, "pick failed", null, true, List.of("type id username = alice"));

        Assert.assertTrue(result.ok(), result.reason());
        Assert.assertEquals(result.tierUsed(), "invent");
        Assert.assertEquals(result.steps().get(0).locatorValue(), "submit");
    }
}
