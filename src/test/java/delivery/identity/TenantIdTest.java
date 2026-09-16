package delivery.identity;

import org.testng.Assert;
import org.testng.annotations.Test;

public class TenantIdTest {

    @Test
    public void personalWorkspaceIsStableAndDistinctFromAnotherUser() {
        TenantId alice = TenantId.personal(11L);
        TenantId aliceAgain = TenantId.personal(11L);
        TenantId bob = TenantId.personal(12L);
        Assert.assertEquals(alice, aliceAgain);
        Assert.assertEquals(alice.value(), "ws_user_11");
        Assert.assertNotEquals(alice, bob);
    }

    @Test
    public void dedicatedInstallationUsesTheSameIdentityModel() {
        TenantId dedicated = TenantId.dedicated("acme");
        Assert.assertEquals(dedicated.value(), "ws_install_acme");
        Assert.assertEquals(TenantId.parse("ws_install_acme"), dedicated);
    }

    @Test
    public void rejectsPathTraversalAndBlankIds() {
        Assert.assertThrows(IllegalArgumentException.class, () -> TenantId.parse(""));
        Assert.assertThrows(IllegalArgumentException.class, () -> TenantId.parse("ws_user_1/../etc"));
        Assert.assertThrows(IllegalArgumentException.class, () -> TenantId.parse("tenants/ws_user_1"));
        Assert.assertThrows(IllegalArgumentException.class, () -> TenantId.dedicated("ACME/prod"));
    }
}
