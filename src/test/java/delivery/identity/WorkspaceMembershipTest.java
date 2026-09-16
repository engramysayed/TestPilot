package delivery.identity;

import org.testng.Assert;
import org.testng.annotations.Test;

public class WorkspaceMembershipTest {

    @Test
    public void membershipDoesNotEncodeTheUserIntoTheTenantId() {
        TenantId aliceWs = TenantId.mint();
        TenantId bobWs = TenantId.mint();
        WorkspaceMembership alice = WorkspaceMembership.ownerOf(aliceWs, 11L);
        Assert.assertTrue(alice.belongsTo(aliceWs));
        Assert.assertFalse(alice.belongsTo(bobWs));
        Assert.assertTrue(alice.canMutate());
        Assert.assertNotEquals(alice.tenant().value(), "ws_user_11");
    }

    @Test
    public void membersCanReadButNotMutate() {
        TenantId dedicated = TenantId.mint();
        WorkspaceMembership member = new WorkspaceMembership(dedicated, 5L, WorkspaceRole.MEMBER);
        Assert.assertTrue(member.canRead());
        Assert.assertFalse(member.canMutate());
        Assert.assertTrue(new WorkspaceMembership(dedicated, 5L, WorkspaceRole.ADMIN).canMutate());
    }
}
