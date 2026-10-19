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
    public void dashBullets_surviveQuoteCaseAndStillSplit() {
        ManualTestCase tc = new ManualTestCase("TC_3", "t", "",
                "1. Open the page",
                "- The page is shown\n- The Upload Transactions list area is visible",
                "P1", "");
        ManualTestCase out = LeftoverExpectedQuoter.quoteCase(tc);
        String[] lines = out.expectedResult().split("\\n");
        Assert.assertEquals(lines.length, 2, out.expectedResult());
        Assert.assertEquals(lines[0], "- The page is shown");
        Assert.assertTrue(lines[1].startsWith("- "), out.expectedResult());
        Assert.assertTrue(lines[1].contains("'Upload Transactions'"), out.expectedResult());
        Assert.assertFalse(lines[1].toLowerCase().contains("'upload transactions list area'"),
                out.expectedResult());
    }

    @Test
    public void quotedFillerPhrase_replacedWithFilteredDisplay() {
        ManualTestCase bad = new ManualTestCase("TC_3", "t", "",
                "1. Click the 'Upload Transactions' link.",
                "1. The title 'Upload Transactions list area' is visible.",
                "P1", "");
        ManualTestCase quoted = LeftoverExpectedQuoter.quoteCase(bad);
        Assert.assertTrue(quoted.expectedResult().contains("'Upload Transactions'"), quoted.expectedResult());
        Assert.assertFalse(quoted.expectedResult().toLowerCase().contains("'upload transactions list area'"),
                quoted.expectedResult());

        ManualTestCase kept = new ManualTestCase("TC_3", "t", "",
                "1. Open the page",
                "1. The title 'Bill Payment Settlement' is shown.",
                "P1", "");
        ManualTestCase untouched = LeftoverExpectedQuoter.quoteCase(kept);
        Assert.assertTrue(untouched.expectedResult().contains("'Bill Payment Settlement'"),
                untouched.expectedResult());
        Assert.assertFalse(untouched.expectedResult().contains("'Bill Payment'"),
                untouched.expectedResult());
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
