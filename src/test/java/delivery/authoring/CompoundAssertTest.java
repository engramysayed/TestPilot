package delivery.authoring;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

public class CompoundAssertTest {

    @Test
    public void detectsAxisDashboardCompoundLine() {
        String line = "Confirm the Verification Code screen is not visible and the Dashboard is shown with "
                + "\"Welcome!\" and \"Start navigating through the portal\"";
        Assert.assertTrue(CompoundAssert.isCompound(line));
        List<String> clauses = CompoundAssert.splitClauses(line);
        Assert.assertTrue(clauses.size() >= 2);
        Assert.assertTrue(clauses.stream().anyMatch(c -> c.contains("Welcome!")));
        Assert.assertTrue(clauses.stream().anyMatch(c -> c.toLowerCase().contains("not visible")
                || c.toLowerCase().contains("verification")));
    }

    @Test
    public void singlePhraseIsNotCompound() {
        Assert.assertFalse(CompoundAssert.isCompound("Confirm the text \"Welcome!\" is visible"));
    }

    @Test
    public void splitsQuotedTailIntoVisibleChecks() {
        List<String> clauses = CompoundAssert.splitClauses(
                "Confirm \"Alpha\" is visible and \"Beta\" is shown");
        Assert.assertEquals(2, clauses.size());
        Assert.assertTrue(clauses.get(0).contains("Alpha"));
        Assert.assertTrue(clauses.get(1).contains("Beta"));
    }
}
