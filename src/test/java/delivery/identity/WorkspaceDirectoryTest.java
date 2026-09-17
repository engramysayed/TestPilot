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

    @Test
    public void ownerCanTransferAndRemoveMemberWithAuditTrail() throws Exception {
        Path store = Files.createTempDirectory("ws-collab");
        WorkspaceDirectory dir = WorkspaceDirectory.open(store);
        TenantId tenant = dir.ensurePersonalWorkspace(1L);
        dir.addMember(tenant, 2L, WorkspaceRole.ADMIN, 1L);
        dir.addMember(tenant, 3L, WorkspaceRole.MEMBER, 1L);

        dir.transferOwnership(tenant, 1L, 2L);
        Assert.assertEquals(dir.role(tenant, 2L), WorkspaceRole.OWNER);
        Assert.assertEquals(dir.role(tenant, 1L), WorkspaceRole.ADMIN);
        Assert.assertTrue(dir.canOperate(tenant, 1L));
        Assert.assertFalse(dir.canAdminister(tenant, 1L));

        dir.removeMember(tenant, 3L, 2L);
        Assert.assertFalse(dir.isMember(tenant, 3L));

        var audit = dir.audit(tenant);
        Assert.assertTrue(audit.stream().anyMatch(e -> "ADD_MEMBER".equals(e.action()) && e.targetUserId() == 3L));
        Assert.assertTrue(audit.stream().anyMatch(e -> "TRANSFER_OWNERSHIP".equals(e.action())));
        Assert.assertTrue(audit.stream().anyMatch(e -> "REMOVE_MEMBER".equals(e.action()) && e.targetUserId() == 3L));
    }

    @Test
    public void cannotRemoveLastOwnerOrTransferToStranger() throws Exception {
        Path store = Files.createTempDirectory("ws-owner-guard");
        WorkspaceDirectory dir = WorkspaceDirectory.open(store);
        TenantId tenant = dir.ensurePersonalWorkspace(1L);
        Assert.assertThrows(IllegalStateException.class, () -> dir.removeMember(tenant, 1L, 1L));
        Assert.assertThrows(IllegalArgumentException.class, () -> dir.transferOwnership(tenant, 1L, 99L));
        dir.addMember(tenant, 2L, WorkspaceRole.MEMBER, 1L);
        Assert.assertThrows(SecurityException.class, () -> dir.transferOwnership(tenant, 2L, 1L));
    }

    @Test
    public void serviceIdentityIsRevocableAndNeverOwner() throws Exception {
        Path store = Files.createTempDirectory("ws-svc");
        WorkspaceDirectory dir = WorkspaceDirectory.open(store);
        TenantId tenant = dir.ensurePersonalWorkspace(1L);
        var created = dir.createServiceIdentity(tenant, 1L, WorkspaceRole.ADMIN, "ci");
        Assert.assertTrue(created.token().startsWith("tp_svc_"));
        Assert.assertEquals(created.role(), WorkspaceRole.ADMIN);
        Assert.assertNotEquals(created.role(), WorkspaceRole.OWNER);
        Assert.assertEquals(dir.authenticateService(created.token()).orElseThrow().tenant().value(), tenant.value());

        dir.revokeServiceIdentity(tenant, created.id(), 1L);
        Assert.assertTrue(dir.authenticateService(created.token()).isEmpty());
        Assert.assertThrows(IllegalArgumentException.class,
                () -> dir.createServiceIdentity(tenant, 1L, WorkspaceRole.OWNER, "nope"));
    }

    @Test
    public void privateRunnerEnrollmentIsTenantBoundAndRevocable() throws Exception {
        Path store = Files.createTempDirectory("ws-runner");
        WorkspaceDirectory dir = WorkspaceDirectory.open(store);
        TenantId alice = dir.ensurePersonalWorkspace(1L);
        TenantId bob = dir.ensurePersonalWorkspace(2L);
        dir.addMember(alice, 3L, WorkspaceRole.ADMIN, 1L);
        dir.addMember(alice, 4L, WorkspaceRole.MEMBER, 1L);

        var created = dir.enrollRunner(alice, 3L, "office-1");
        Assert.assertTrue(created.token().startsWith("tp_run_"));
        var live = dir.authenticateRunner(created.token()).orElseThrow();
        Assert.assertEquals(live.tenant().value(), alice.value());
        Assert.assertNull(live.revokedAt());
        dir.touchRunnerHeartbeat(created.id(), alice, java.time.Instant.parse("2026-09-17T12:00:00Z"));
        Assert.assertEquals(dir.authenticateRunner(created.token()).orElseThrow().lastHeartbeat(),
                java.time.Instant.parse("2026-09-17T12:00:00Z"));

        Assert.assertThrows(SecurityException.class, () -> dir.enrollRunner(alice, 4L, "nope"));
        Assert.assertThrows(SecurityException.class, () -> dir.revokeRunner(alice, created.id(), 4L));
        Assert.assertTrue(dir.authenticateRunner(created.token()).isPresent());
        dir.revokeRunner(alice, created.id(), 1L);
        Assert.assertTrue(dir.authenticateRunner(created.token()).isEmpty());
        Assert.assertTrue(dir.listRunners(alice).stream().anyMatch(r -> r.id().equals(created.id()) && r.revokedAt() != null));
        Assert.assertTrue(dir.listRunners(bob).isEmpty());
    }

    @Test
    public void pendingInviteBecomesMembershipWhenAccountAppears() throws Exception {
        Path store = Files.createTempDirectory("ws-invite");
        WorkspaceDirectory dir = WorkspaceDirectory.open(store);
        TenantId tenant = dir.ensurePersonalWorkspace(1L);
        dir.inviteEmail(tenant, 1L, "New.User@example.com", WorkspaceRole.MEMBER);
        Assert.assertEquals(dir.pendingInvites(tenant).size(), 1);
        Assert.assertEquals(dir.consumePendingInvites("new.user@example.com", 44L), 1);
        Assert.assertEquals(dir.role(tenant, 44L), WorkspaceRole.MEMBER);
        Assert.assertTrue(dir.pendingInvites(tenant).isEmpty());
        Assert.assertTrue(dir.audit(tenant).stream().anyMatch(e -> "INVITE".equals(e.action())));
    }
}
