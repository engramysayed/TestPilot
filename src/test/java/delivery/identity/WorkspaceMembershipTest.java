package delivery.identity;

import org.testng.Assert;
import org.testng.annotations.Test;

public class WorkspaceMembershipTest {

    @Test
    public void personalOwnerCannotActInsideAnotherUsersWorkspace() {
        WorkspaceMembership alice = WorkspaceMembership.personalOwner(11L);
        TenantId bob = TenantId.personal(12L);
        Assert.assertTrue(alice.belongsTo(TenantId.personal(11L)));
        Assert.assertFalse(alice.belongsTo(bob));
        Assert.assertTrue(alice.canMutate());
        Assert.assertNotEquals(alice.tenant(), bob);
    }

    @Test
    public void membersCanReadButNotMutate() {
        WorkspaceMembership member = new WorkspaceMembership(
                TenantId.dedicated("acme"), 5L, WorkspaceRole.MEMBER);
        Assert.assertTrue(member.canRead());
        Assert.assertFalse(member.canMutate());
        Assert.assertTrue(new WorkspaceMembership(
                TenantId.dedicated("acme"), 5L, WorkspaceRole.ADMIN).canMutate());
    }
}
