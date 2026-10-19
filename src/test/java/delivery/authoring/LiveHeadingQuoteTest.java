package delivery.authoring;

import delivery.excel.ManualTestCase;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

public class LiveHeadingQuoteTest {
    @Test
    public void oneCardTitle_rewritesExactHeading() {
        String html = """
                <html><body>
                <h1>Bill Payment Settlement</h1>
                <div class="ant-card-head-title">Upload Transactions List</div>
                </body></html>
                """;
        String out = LiveHeadingQuote.maybeRewrite(
                "the Upload Transactions list area is visible", html);
        Assert.assertEquals(out, "'Upload Transactions List'");
    }

    @Test
    public void twoMatchingTitles_unchanged() {
        String html = """
                <html><body>
                <h2>Upload Transactions List</h2>
                <div class="card-title">Upload Transactions Archive</div>
                </body></html>
                """;
        String clause = "the Upload Transactions list area is visible";
        Assert.assertEquals(LiveHeadingQuote.maybeRewrite(clause, html), clause);
    }

    @Test
    public void shortQuotedLeftover_upgradesToLiveHeading() {
        String html = """
                <html><body><h1>Upload Transactions List</h1></body></html>
                """;
        Assert.assertEquals(
                LiveHeadingQuote.maybeRewrite("'Upload Transactions'", html),
                "'Upload Transactions List'");
    }

    @Test
    public void normalizedLeftoverAndShortQuote_rewriteToLiveHeading() {
        String html = """
                <html><body>
                <h1>Bill Payment Settlement</h1>
                <div class="ant-card-head-title">Upload Transactions List</div>
                </body></html>
                """;
        String leftover = CompoundAssert.normalizeClause("the Upload Transactions list area is visible");
        String shortQuote = CompoundAssert.normalizeClause("'Upload Transactions'");
        Assert.assertEquals(LiveHeadingQuote.maybeRewrite(leftover, html), "'Upload Transactions List'");
        Assert.assertEquals(LiveHeadingQuote.maybeRewrite(shortQuote, html), "'Upload Transactions List'");
    }

    @Test
    public void parseIntents_tc3Leftover_rewritesToUploadTransactionsList() {
        ManualTestCase tc = new ManualTestCase(
                "TC_3",
                "Verify Bill Payment Settlement",
                "",
                "1. Click the 'Support' in the sidebar.\n2. Click the 'Bill Payment Settlement' in the Support sub-menu.",
                "1. The Support section expands and sub-menu items are shown.\n"
                        + "2. The Bill Payment Settlement page is displayed with the title 'Bill Payment Settlement'"
                        + " and the Upload Transactions list area is visible.",
                "P1", "");
        String html = """
                <html><body>
                <h1>Bill Payment Settlement</h1>
                <div class="ant-card-head-title">Upload Transactions List</div>
                </body></html>
                """;
        List<StepIntentBinder.IntentLine> intents = StepIntentBinder.parseIntents(tc);
        StepIntentBinder.IntentLine leftover = intents.stream()
                .filter(i -> i.kind() == StepIntentBinder.IntentKind.ASSERT_VISIBLE)
                .filter(i -> i.text().toLowerCase().contains("upload"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no upload assert in " + intents));
        Assert.assertTrue(leftover.text().toLowerCase().startsWith("confirm"),
                "execute binds the normalized wrapper: " + leftover.text());
        Assert.assertEquals(LiveHeadingQuote.maybeRewrite(leftover.text(), html), "'Upload Transactions List'");
    }

    @Test
    public void zeroHeadings_unchanged() {
        String clause = "the Upload Transactions list area is visible";
        Assert.assertEquals(LiveHeadingQuote.maybeRewrite(clause, "<html><body><p>x</p></body></html>"),
                clause);
    }
}
