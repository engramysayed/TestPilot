package delivery.identity;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;

public class WorkspaceRoleEnforcementTest {

    @Test
    public void membershipAloneDoesNotAuthorizeMutateOrAdmin() throws Exception {
        Path store = Files.createTempDirectory("ws-roles");
        WorkspaceDirectory dir = WorkspaceDirectory.open(store);
        TenantId tenant = dir.ensurePersonalWorkspace(1L);
        dir.addMember(tenant, 2L, WorkspaceRole.MEMBER);
        dir.addMember(tenant, 3L, WorkspaceRole.ADMIN);

        Assert.assertTrue(dir.isMember(tenant, 2L));
        Assert.assertFalse(dir.canOperate(tenant, 2L));
        Assert.assertFalse(dir.canAdminister(tenant, 2L));
        Assert.assertThrows(SecurityException.class, () -> dir.requireOperate(tenant, 2L));
        Assert.assertThrows(SecurityException.class, () -> dir.requireAdminister(tenant, 2L));

        Assert.assertTrue(dir.canOperate(tenant, 3L));
        Assert.assertFalse(dir.canAdminister(tenant, 3L));
        Assert.assertTrue(dir.canOperate(tenant, 1L));
        Assert.assertTrue(dir.canAdminister(tenant, 1L));
    }
}
