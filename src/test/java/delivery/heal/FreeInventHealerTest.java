package delivery.heal;

import delivery.authoring.LocatorValidator;
import delivery.authoring.StepIntentBinder;
import delivery.codegen.ProvenStep;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public class FreeInventHealerTest {

    @Test
    public void validatesAndReturnsAtMostThreeIntentSteps() {
        AtomicReference<List<String>> receivedHistory = new AtomicReference<>();
        CursorHealClient cursor = new CursorHealClient(false, "unused", 1) {
            @Override
            public String inventSteps(String intentText, String failureReason, List<String> priorSteps,
                                      String slimHtmlExcerpt, java.nio.file.Path screenshotPathOrNull) {
                receivedHistory.set(priorSteps);
                return """
                        Thought: use stable ids.
                        {"thought":"stable controls","steps":[
                          {"action":"click","locatorStrategy":"id","locatorValue":"save","value":"","assertionType":"","assertionExpected":""},
                          {"action":"click","locatorStrategy":"id","locatorValue":"save2","value":"","assertionType":"","assertionExpected":""},
                          {"action":"click","locatorStrategy":"id","locatorValue":"save3","value":"","assertionType":"","assertionExpected":""},
                          {"action":"click","locatorStrategy":"id","locatorValue":"save4","value":"","assertionType":"","assertionExpected":""}
                        ]}
                        """;
            }
        };
        FreeInventHealer healer = new FreeInventHealer(
                cursor, null, new LocatorValidator(), "cursor", true);

        Optional<List<ProvenStep>> result = healer.invent(
                "TC1",
                new StepIntentBinder.IntentLine(StepIntentBinder.IntentKind.CLICK, "Click Save"),
                """
                        <button id="save">Save</button>
                        <button id="save2">Save 2</button>
                        <button id="save3">Save 3</button>
                        <button id="save4">Save 4</button>
                        """,
                null,
                "shortlist exhausted",
                List.of("type id title = Draft"),
                "post_cursor",
                null);

        Assert.assertTrue(result.isPresent());
        Assert.assertEquals(result.orElseThrow().size(), 3);
        Assert.assertTrue(result.orElseThrow().stream().allMatch(ProvenStep::validated));
        Assert.assertEquals(receivedHistory.get(), List.of("type id title = Draft"));
    }

    @Test
    public void rejectsInvalidLocatorAndMismatchedAction() {
        CursorHealClient cursor = new CursorHealClient(false, "unused", 1) {
            @Override
            public String inventSteps(String intentText, String failureReason, List<String> priorSteps,
                                      String slimHtmlExcerpt, java.nio.file.Path screenshotPathOrNull) {
                return """
                        {"steps":[
                          {"action":"type","locatorStrategy":"xpath","locatorValue":"/html/body","value":"x"}
                        ]}
                        """;
            }
        };
        FreeInventHealer healer = new FreeInventHealer(
                cursor, null, new LocatorValidator(), "cursor", true);

        Optional<List<ProvenStep>> result = healer.invent(
                "TC1",
                new StepIntentBinder.IntentLine(StepIntentBinder.IntentKind.CLICK, "Click Save"),
                "<button>Save</button>", null, "empty candidates", List.of(),
                "empty_candidates", null);

        Assert.assertTrue(result.isEmpty());
    }

    @Test
    public void rejectsLocatorsAbsentFromThePage() {
        CursorHealClient cursor = new CursorHealClient(false, "unused", 1) {
            @Override
            public String inventSteps(String intentText, String failureReason, List<String> priorSteps,
                                      String slimHtmlExcerpt, java.nio.file.Path screenshotPathOrNull) {
                return """
                        {"steps":[
                          {"action":"click","locatorStrategy":"id","locatorValue":"imagined-save","value":""}
                        ]}
                        """;
            }
        };
        FreeInventHealer healer = new FreeInventHealer(
                cursor, null, new LocatorValidator(), "cursor", true);

        Optional<List<ProvenStep>> result = healer.invent(
                "TC1",
                new StepIntentBinder.IntentLine(StepIntentBinder.IntentKind.CLICK, "Click Save"),
                "<button id=\"save\">Save</button>", null, "shortlist exhausted",
                List.of(), "post_cursor", null);

        Assert.assertTrue(result.isEmpty(), "invented id is not in the page HTML");
    }

    @Test
    public void skipsBlankHtmlAndDisabledInvent() {
        CursorHealClient cursor = new CursorHealClient(false, "unused", 1) {
            @Override
            public String inventSteps(String intentText, String failureReason, List<String> priorSteps,
                                      String slimHtmlExcerpt, java.nio.file.Path screenshotPathOrNull) {
                throw new AssertionError("provider must not run");
            }
        };
        StepIntentBinder.IntentLine intent =
                new StepIntentBinder.IntentLine(StepIntentBinder.IntentKind.CLICK, "Click Save");

        Assert.assertTrue(new FreeInventHealer(
                cursor, null, new LocatorValidator(), "cursor", true)
                .invent("TC1", intent, " ", null, "failed", List.of(), "empty_candidates", null)
                .isEmpty());
        Assert.assertTrue(new FreeInventHealer(
                cursor, null, new LocatorValidator(), "cursor", false)
                .invent("TC1", intent, "<button>Save</button>", null, "failed",
                        List.of(), "empty_candidates", null)
                .isEmpty());
    }

    @Test
    public void lastLayerMayNavigateOnlyToTheExcelOpenPath() {
        CursorHealClient cursor = new CursorHealClient(false, "unused", 1) {
            @Override
            public String inventSteps(String intentText, String failureReason, List<String> priorSteps,
                                      String slimHtmlExcerpt, java.nio.file.Path screenshotPathOrNull) {
                return """
                        {"thought":"field is not on this page","steps":[
                          {"action":"navigate","locatorStrategy":"","locatorValue":"","value":"/reg/"}
                        ]}
                        """;
            }
        };
        FreeInventHealer healer = new FreeInventHealer(
                cursor, null, new LocatorValidator(), "cursor", true);
        StepIntentBinder.IntentLine intent = new StepIntentBinder.IntentLine(
                StepIntentBinder.IntentKind.ASSERT_VISIBLE, "Confirm the First name field is visible");

        Optional<List<ProvenStep>> ok = healer.invent(
                "TC1", intent, " ", null, "empty DOM candidate table", List.of(),
                "empty_candidates", null, "/reg/");
        Assert.assertTrue(ok.isPresent(), "empty HTML may still navigate to the Excel path");
        Assert.assertEquals(ok.orElseThrow().get(0).action(), "navigate");
        Assert.assertEquals(ok.orElseThrow().get(0).value(), "/reg/");

        Optional<List<ProvenStep>> evil = healer.invent(
                "TC1", intent, "<html></html>", null, "empty candidates", List.of(),
                "empty_candidates", null, "/reg/");
        Assert.assertTrue(evil.isEmpty() || evil.orElseThrow().stream().noneMatch(s ->
                        s.value() != null && s.value().contains("evil")),
                "must not accept a URL outside the Excel open-path");
    }

    @Test
    public void lastLayerRejectsNavigateOffTheExcelPath() {
        CursorHealClient cursor = new CursorHealClient(false, "unused", 1) {
            @Override
            public String inventSteps(String intentText, String failureReason, List<String> priorSteps,
                                      String slimHtmlExcerpt, java.nio.file.Path screenshotPathOrNull) {
                return """
                        {"steps":[
                          {"action":"navigate","value":"https://evil.example/phish"}
                        ]}
                        """;
            }
        };
        Optional<List<ProvenStep>> result = new FreeInventHealer(
                cursor, null, new LocatorValidator(), "cursor", true)
                .invent("TC1",
                        new StepIntentBinder.IntentLine(
                                StepIntentBinder.IntentKind.ASSERT_VISIBLE,
                                "Confirm the First name field is visible"),
                        "<html></html>", null, "empty candidates", List.of(),
                        "empty_candidates", null, "/reg/");
        Assert.assertTrue(result.isEmpty(), "arbitrary URLs must not be invented");
    }

    @Test
    public void parsesRecoveryModeFromInvent() {
        CursorHealClient cursor = new CursorHealClient(false, "unused", 1) {
            @Override
            public String inventSteps(String intentText, String failureReason, List<String> priorSteps,
                                      String slimHtmlExcerpt, java.nio.file.Path screenshotPathOrNull) {
                return """
                        {"mode":"recovery","thought":"field filled","recoverySteps":[
                          {"action":"clear","locatorStrategy":"css","locatorValue":"input[name='email']","value":""}
                        ],"automationNotes":["Leave email empty"]}
                        """;
            }
        };
        FreeInventHealer healer = new FreeInventHealer(
                cursor, null, new LocatorValidator(), "cursor", true);
        java.util.Optional<HealResult> result = healer.inventHealResult(
                "TC1",
                new StepIntentBinder.IntentLine(
                        StepIntentBinder.IntentKind.ASSERT_VISIBLE,
                        "Verify validation for empty email"),
                "<input name=\"email\" value=\"x\" />",
                null, "assert failed", List.of(), "post_cursor", null, null);
        Assert.assertTrue(result.isPresent());
        Assert.assertEquals(result.orElseThrow().tierUsed(), "recovery");
        Assert.assertEquals(result.orElseThrow().steps().get(0).action(), "clear");
        Assert.assertEquals(result.orElseThrow().automationNotes().size(), 1);
    }
}
