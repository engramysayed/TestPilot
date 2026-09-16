package delivery.identity;

import org.testng.Assert;
import org.testng.annotations.Test;

public class TenantIdTest {

    @Test
    public void mintedIdsAreOpaqueAndStableUnderParse() {
        TenantId first = TenantId.mint();
        TenantId second = TenantId.mint();
        Assert.assertNotEquals(first, second);
        Assert.assertTrue(first.value().startsWith("ws_"));
        Assert.assertEquals(first.value().length(), 35);
        Assert.assertEquals(TenantId.parse(first.value()), first);
        Assert.assertFalse(first.value().contains("user"));
        Assert.assertFalse(first.value().contains("install"));
    }

    @Test
    public void accountAndSlugStringsAreNotTenantIds() {
        Assert.assertThrows(IllegalArgumentException.class, () -> TenantId.parse("ws_user_11"));
        Assert.assertThrows(IllegalArgumentException.class, () -> TenantId.parse("ws_install_acme"));
        Assert.assertThrows(IllegalArgumentException.class, () -> TenantId.parse(""));
        Assert.assertThrows(IllegalArgumentException.class, () -> TenantId.parse("ws_user_1/../etc"));
        Assert.assertThrows(IllegalArgumentException.class, () -> TenantId.parse("tenants/ws_abc"));
    }
}
