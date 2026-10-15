package delivery.mobile.result;

import org.testng.Assert;
import org.testng.annotations.Test;

public class ProviderUseTest {

    @Test
    public void missingProviderIsNotPass() {
        ProviderUse use = ProviderUse.missing("WEBVIEW", "com.example.test|https://test.example/checkout|Checkout", "cursor");
        Assert.assertFalse(use.passed());
        Assert.assertEquals(use.usedProvider(), "NONE");
        Assert.assertEquals(use.fallbackReason(), "MISSING_PROVIDER");
        Assert.assertNotEquals(use.allowedProvider(), use.usedProvider());
    }

    @Test
    public void fallbackIsNotLabeledAsCursorProof() {
        ProviderUse use = ProviderUse.fallback("NATIVE", "", "cursor", "EXHAUSTED");
        Assert.assertEquals(use.usedProvider(), "keel");
        Assert.assertFalse(use.passed());
        Assert.assertFalse(use.countsAsCursor());
    }

    @Test
    public void volatileWebViewHandleCannotBeStored() {
        try {
            new ProviderUse("WEBVIEW", "WEBVIEW_42", "cursor", "cursor", "", "ui-tars", true);
            Assert.fail("expected rejection");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains("WEBVIEW_"));
        }
    }
}
