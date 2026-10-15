package project.runtime.evidence;

import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

public class ProviderEvidenceTest {

    @AfterMethod
    public void clear() {
        ContextSwitch.clear();
    }

    @Test
    public void missingProviderIsNotPass() {
        ProviderEvidence.Result missing = ProviderEvidence.missing("cursor");
        Assert.assertFalse(missing.passed());
        Assert.assertEquals(missing.usedProvider(), "NONE");
        Assert.assertFalse(missing.countsAsCursor());
    }

    @Test
    public void keelFallbackIsNotCursorProof() {
        ProviderEvidence.Result fallback = ProviderEvidence.fallback("cursor", "EXHAUSTED");
        Assert.assertEquals(fallback.usedProvider(), "keel");
        Assert.assertFalse(fallback.passed());
        Assert.assertFalse(fallback.countsAsCursor());
    }

    @Test
    public void contextSwitchStoresPredicate() {
        ContextSwitch.to(null, "WEBVIEW", "com.example.test", "https://test.example/checkout", "Checkout");
        Assert.assertEquals(ContextSwitch.active(), "WEBVIEW");
        Assert.assertEquals(ContextSwitch.predicate(), "com.example.test|https://test.example/checkout|Checkout");
        Assert.assertFalse(ContextSwitch.predicate().contains("WEBVIEW_"));
        ContextSwitch.restoreNative();
        Assert.assertEquals(ContextSwitch.active(), "NATIVE");
    }

    @Test
    public void contextSwitchRejectsPid() {
        try {
            ContextSwitch.to(null, "WEBVIEW", "com.example.test", "WEBVIEW_9", "Checkout");
            Assert.fail("expected rejection");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains("WEBVIEW_"));
        }
    }
}
