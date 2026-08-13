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
                "<button id=\"save\">Save</button>",
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
}
