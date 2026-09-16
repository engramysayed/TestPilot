package delivery.identity;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;

public class WorkspaceDirectoryTest {

    @Test
    public void personalWorkspaceSurvivesAccountRebind() throws Exception {
        Path store = Files.createTempDirectory("ws-dir");
        WorkspaceDirectory dir = WorkspaceDirectory.open(store);
        TenantId original = dir.ensurePersonalWorkspace(11L);
        Assert.assertEquals(dir.ensurePersonalWorkspace(11L), original);
        Assert.assertTrue(original.value().matches("ws_[a-f0-9]{32}"));
        dir.rebindAccount(11L, 99L);
        Assert.assertEquals(dir.ensurePersonalWorkspace(99L), original);
        Assert.assertTrue(dir.isMember(original, 99L));
        Assert.assertFalse(dir.isMember(original, 11L));
        Assert.assertEquals(WorkspaceDirectory.open(store).ensurePersonalWorkspace(99L), original);
    }

    @Test
    public void installationSlugRenameDoesNotChangeTenantId() throws Exception {
        Path store = Files.createTempDirectory("ws-install");
        WorkspaceDirectory dir = WorkspaceDirectory.open(store);
        TenantId acme = dir.ensureDedicatedWorkspace("acme");
        Assert.assertEquals(dir.displaySlug(acme), "acme");
        dir.renameDisplaySlug(acme, "acme-renamed");
        Assert.assertEquals(dir.ensureDedicatedWorkspace("acme-renamed"), acme);
        Assert.assertEquals(dir.displaySlug(acme), "acme-renamed");
        Assert.assertNotEquals(acme.value(), "ws_install_acme");
    }

    @Test
    public void membershipIsRequiredForCrossTenantRejection() throws Exception {
        Path store = Files.createTempDirectory("ws-mem");
        WorkspaceDirectory dir = WorkspaceDirectory.open(store);
        TenantId alice = dir.ensurePersonalWorkspace(1L);
        TenantId bob = dir.ensurePersonalWorkspace(2L);
        Assert.assertTrue(dir.isMember(alice, 1L));
        Assert.assertFalse(dir.isMember(alice, 2L));
        Assert.assertThrows(SecurityException.class, () -> dir.requireMember(bob, 1L));
    }
}
