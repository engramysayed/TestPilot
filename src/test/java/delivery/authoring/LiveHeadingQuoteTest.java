package delivery.authoring;

import org.testng.Assert;
import org.testng.annotations.Test;

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
    public void zeroHeadings_unchanged() {
        String clause = "the Upload Transactions list area is visible";
        Assert.assertEquals(LiveHeadingQuote.maybeRewrite(clause, "<html><body><p>x</p></body></html>"),
                clause);
    }
}
