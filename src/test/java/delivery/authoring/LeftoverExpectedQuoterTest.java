package delivery.authoring;

import delivery.excel.ManualTestCase;
import org.testng.Assert;
import org.testng.annotations.Test;

public class LeftoverExpectedQuoterTest {
    @Test
    public void leftoverListArea_quotesUploadTransactions() {
        ManualTestCase tc = new ManualTestCase("TC_3",
                "Verify Bill Payment Settlement",
                "",
                "1. Click the 'Support' in the sidebar.\n2. Click the 'Bill Payment Settlement' in the Support sub-menu.",
                "1. The Support section expands and sub-menu items are shown.\n2. The Bill Payment Settlement page is displayed with the title 'Bill Payment Settlement' and the Upload Transactions list area is visible.",
                "P1", "");
        ManualTestCase out = LeftoverExpectedQuoter.quoteCase(tc);
        Assert.assertTrue(out.expectedResult().contains("'Upload Transactions'"), out.expectedResult());
        Assert.assertFalse(out.expectedResult().toLowerCase().contains("'upload transactions list area'"),
                out.expectedResult());
        Assert.assertTrue(out.expectedResult().contains("'Bill Payment Settlement'"));
    }

    @Test
    public void leftoverTokensAlreadyQuotedInSteps_reusesQuote() {
        ManualTestCase tc = new ManualTestCase("TC_1", "t", "",
                "1. Click the 'Save Draft' button.",
                "1. The Save Draft button is shown.",
                "P1", "");
        ManualTestCase out = LeftoverExpectedQuoter.quoteCase(tc);
        Assert.assertTrue(out.expectedResult().contains("'Save Draft'"), out.expectedResult());
    }

    @Test
    public void fillerOnly_unchanged() {
        ManualTestCase tc = new ManualTestCase("TC_1", "t", "",
                "1. Open home",
                "1. The page is shown",
                "P1", "");
        ManualTestCase out = LeftoverExpectedQuoter.quoteCase(tc);
        Assert.assertEquals(out.expectedResult(), tc.expectedResult());
    }
}
