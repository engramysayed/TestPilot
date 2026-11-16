package delivery.agent.validation.contract;

import org.testng.Assert;
import org.testng.annotations.Test;

public class QuotedEntityExtractorTest {

    @Test
    public void extract_singleAndDoubleQuoted() {
        var found = QuotedEntityExtractor.extractFromText(
                "Open 'Login Page' and type in \"Username Field\".");
        Assert.assertEquals(found, java.util.List.of("Login Page", "Username Field"));
    }

    @Test
    public void stepsLikelyNeedQuotedEntities_detectsUiVerbs() {
        Assert.assertTrue(QuotedEntityExtractor.stepsLikelyNeedQuotedEntities("Click Login Button"));
        Assert.assertFalse(QuotedEntityExtractor.stepsLikelyNeedQuotedEntities("1. Wait"));
    }
}