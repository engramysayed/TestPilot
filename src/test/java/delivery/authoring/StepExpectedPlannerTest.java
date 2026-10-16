package delivery.authoring;

import delivery.excel.ManualTestCase;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

public class StepExpectedPlannerTest {

    @Test
    public void interleavesAssertAfterEachStepWhenExpectedPresent() {
        ManualTestCase tc = new ManualTestCase(
                "TC_02", "Bulk Charge Back", "",
                """
                        1. Clicks "Support" in the sidebar.
                        2. Clicks "Bulk Charge Back" in the sub-menu.
                        """,
                """
                        1. The Support section expands and sub-menu items are shown.
                        2. The Bulk Charge Back screen is displayed
                        """,
                "P2", "");
        List<StepIntentBinder.IntentLine> intents = StepIntentBinder.parseIntents(tc);
        Assert.assertEquals(kinds(intents), List.of(
                StepIntentBinder.IntentKind.CLICK,
                StepIntentBinder.IntentKind.ASSERT_VISIBLE,
                StepIntentBinder.IntentKind.CLICK,
                StepIntentBinder.IntentKind.ASSERT_VISIBLE));
        Assert.assertTrue(intents.get(1).text().toLowerCase().contains("support section"));
        Assert.assertTrue(intents.get(3).text().toLowerCase().contains("bulk charge back"));
        Assert.assertFalse(intents.stream().anyMatch(i -> i.text().contains("1. The Support")
                && i.text().contains("Bulk Charge Back screen")),
                "whole expected blob must not be one trailing step: " + intents);
    }

    @Test
    public void foldsOrphanExpectedOntoLastRealStep() {
        ManualTestCase tc = new ManualTestCase(
                "TC_02", "Bulk Charge Back", "",
                """
                        1. Clicks "Support" in the sidebar.
                        2. Clicks "Bulk Charge Back" in the sub-menu.
                        3.
                        """,
                """
                        1. The Support section expands and sub-menu items are shown.
                        2. The Bulk Charge Back screen is displayed
                        3. and the "Upload Transaction List" section is visible.
                        """,
                "P2", "");
        List<StepIntentBinder.IntentLine> intents = StepIntentBinder.parseIntents(tc);
        Assert.assertEquals(kinds(intents), List.of(
                StepIntentBinder.IntentKind.CLICK,
                StepIntentBinder.IntentKind.ASSERT_VISIBLE,
                StepIntentBinder.IntentKind.CLICK,
                StepIntentBinder.IntentKind.ASSERT_VISIBLE,
                StepIntentBinder.IntentKind.ASSERT_VISIBLE));
        Assert.assertTrue(intents.get(3).text().toLowerCase().contains("bulk charge back"));
        Assert.assertTrue(intents.get(4).text().contains("Upload Transaction List")
                || intents.get(4).text().toLowerCase().contains("upload transaction"));
        Assert.assertEquals(
                intents.stream().filter(i -> i.kind() == StepIntentBinder.IntentKind.CLICK).count(),
                2L,
                "blank step 3 must not become an action: " + intents);
    }

    @Test
    public void skipsAssertWhenExpectedCellBlank() {
        ManualTestCase tc = new ManualTestCase(
                "TC_X", "x", "",
                "1. Click Save\n2. Click Home",
                "1. Dashboard is shown.\n2. ",
                "P2", "");
        List<StepIntentBinder.IntentLine> intents = StepIntentBinder.parseIntents(tc);
        Assert.assertEquals(kinds(intents), List.of(
                StepIntentBinder.IntentKind.CLICK,
                StepIntentBinder.IntentKind.ASSERT_VISIBLE,
                StepIntentBinder.IntentKind.CLICK));
    }

    @Test
    public void doesNotSplitExpandsAndSubMenuAsTwoClauses() {
        List<String> clauses = ExpectedClauseSplitter.clauses(
                "The Support section expands and sub-menu items are shown.");
        Assert.assertEquals(clauses.size(), 1, clauses.toString());
    }

    @Test
    public void splitsNumberedAndDashAndOutcomeAnd() {
        List<String> clauses = ExpectedClauseSplitter.clauses("""
                1. Support is shown
                2. Bulk Charge Back is displayed
                - Upload area is visible
                and the "Upload Transaction List" section is visible.
                """);
        Assert.assertTrue(clauses.size() >= 3, clauses.toString());
        Assert.assertTrue(clauses.stream().anyMatch(c -> c.toLowerCase().contains("support")));
        Assert.assertTrue(clauses.stream().anyMatch(c -> c.toLowerCase().contains("bulk charge back")));
        Assert.assertTrue(clauses.stream().anyMatch(c -> c.contains("Upload Transaction List")
                || c.toLowerCase().contains("upload area")));
    }

    @Test
    public void dummyOkExpectedDoesNotInventAnAssert() {
        ManualTestCase tc = new ManualTestCase(
                "TC6", "Add", "",
                "1. Add the red backpack to the cart",
                "ok", "P1", "");
        List<StepIntentBinder.IntentLine> intents = StepIntentBinder.parseIntents(tc);
        Assert.assertEquals(intents.size(), 1);
        Assert.assertEquals(intents.get(0).kind(), StepIntentBinder.IntentKind.CLICK);
    }

    private static List<StepIntentBinder.IntentKind> kinds(List<StepIntentBinder.IntentLine> intents) {
        return intents.stream().map(StepIntentBinder.IntentLine::kind).toList();
    }
}
