package delivery.privacy;

import org.testng.Assert;
import org.testng.annotations.Test;

public class ProviderPolicyTest {

    @Test
    public void emptyAllowlistFailsClosedAndBlocksFallback() {
        ProviderPolicy policy = ProviderPolicy.parse("");
        Assert.assertFalse(policy.allows(ProviderPolicy.Kind.CURSOR));
        Assert.assertFalse(policy.allows(ProviderPolicy.Kind.OLLAMA));
        Assert.assertThrows(ProviderDisallowedException.class, () -> policy.require(ProviderPolicy.Kind.CURSOR));
        Assert.assertEquals(policy.inspect(ProviderPolicy.Kind.CURSOR).reason(),
                "provider allowlist empty; fail closed");
    }

    @Test
    public void ollamaOnlyRejectsCursorFallback() {
        ProviderPolicy policy = ProviderPolicy.parse("ollama");
        Assert.assertTrue(policy.allows(ProviderPolicy.Kind.OLLAMA));
        ProviderPolicy.Decision cursor = policy.inspect(ProviderPolicy.Kind.CURSOR);
        Assert.assertFalse(cursor.allowed());
        Assert.assertFalse(cursor.reason().toLowerCase().contains("secret"));
        Assert.assertFalse(cursor.reason().toLowerCase().contains("password"));
        Assert.assertThrows(ProviderDisallowedException.class, () -> policy.require(ProviderPolicy.Kind.CURSOR));
    }
}
