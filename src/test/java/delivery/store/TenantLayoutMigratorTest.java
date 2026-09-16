package delivery.store;

import delivery.identity.ScopePaths;
import delivery.identity.TenantId;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class TenantLayoutMigratorTest {

    @Test
    public void knownProjectMovesUnderTenantAndWritesReversibleManifest() throws Exception {
        Path store = Files.createTempDirectory("mig-known");
        TenantId alice = TenantId.mint();
        Path legacy = store.resolve("same-example-com").resolve("prj_alice");
        Files.createDirectories(legacy);
        Files.writeString(legacy.resolve("project.json"), "{\"projectId\":\"prj_alice\",\"version\":1}");
        Files.writeString(legacy.resolve("sentinel.txt"), "ALICE_ONLY");

        TenantLayoutMigrator.Result result = TenantLayoutMigrator.migrate(
                store, Map.of("prj_alice", alice));

        Path scoped = ScopePaths.projectRoot(store, alice, "prj_alice");
        Assert.assertTrue(Files.isRegularFile(scoped.resolve("sentinel.txt")));
        Assert.assertEquals(Files.readString(scoped.resolve("sentinel.txt")), "ALICE_ONLY");
        Assert.assertFalse(Files.exists(legacy.resolve("sentinel.txt")));
        Assert.assertTrue(Files.isRegularFile(result.manifest()));
        String manifest = Files.readString(result.manifest());
        Assert.assertTrue(manifest.contains("prj_alice"));
        Assert.assertTrue(manifest.contains(alice.value()));
        Assert.assertEquals(result.migrated(), 1);

        TenantLayoutMigrator.revert(store, result.manifest());
        Assert.assertTrue(Files.isRegularFile(legacy.resolve("sentinel.txt")));
        Assert.assertFalse(Files.exists(scoped.resolve("sentinel.txt")));
    }

    @Test
    public void ambiguousDomainSharedFilesAreQuarantinedNotAssigned() throws Exception {
        Path store = Files.createTempDirectory("mig-q");
        TenantId alice = TenantId.mint();
        Path domain = store.resolve("same-example-com");
        Files.createDirectories(domain.resolve("prj_alice"));
        Files.writeString(domain.resolve("prj_alice").resolve("project.json"), "{}");
        Files.writeString(domain.resolve(PreferredHooksStore.FILE_NAME), "{\"attributes\":[\"data-shared\"]}");
        Files.writeString(domain.resolve(DomainLocatorMemory.FILE_NAME), "{\"slots\":[]}");

        TenantLayoutMigrator.Result result = TenantLayoutMigrator.migrate(
                store, Map.of("prj_alice", alice));

        Assert.assertTrue(result.quarantined() >= 2);
        Path aliceSite = ScopePaths.siteRoot(store, alice, "https://same.example.com");
        Assert.assertFalse(Files.exists(aliceSite.resolve(PreferredHooksStore.FILE_NAME)),
                "ambiguous hooks must not be assigned to a tenant");
        Assert.assertFalse(Files.exists(domain.resolve(PreferredHooksStore.FILE_NAME)));
        Assert.assertTrue(Files.isDirectory(result.quarantine()));
        try (var walk = Files.walk(result.quarantine())) {
            boolean foundHooks = walk.anyMatch(p -> PreferredHooksStore.FILE_NAME.equals(p.getFileName().toString()));
            Assert.assertTrue(foundHooks);
        }
    }

    @Test
    public void crashHalfwayThenRecoverFinishesWithoutLosingProjects() throws Exception {
        Path store = Files.createTempDirectory("mig-crash");
        TenantId alice = TenantId.mint();
        TenantId bob = TenantId.mint();
        Path aliceLegacy = store.resolve("same-example-com").resolve("prj_alice");
        Path bobLegacy = store.resolve("same-example-com").resolve("prj_bob");
        Files.createDirectories(aliceLegacy);
        Files.createDirectories(bobLegacy);
        Files.writeString(aliceLegacy.resolve("project.json"), "{}");
        Files.writeString(aliceLegacy.resolve("sentinel.txt"), "ALICE_ONLY");
        Files.writeString(bobLegacy.resolve("project.json"), "{}");
        Files.writeString(bobLegacy.resolve("sentinel.txt"), "BOB_ONLY");

        try {
            TenantLayoutMigrator.migrate(store, Map.of("prj_alice", alice, "prj_bob", bob), 1);
            Assert.fail("expected simulated crash after the first move");
        } catch (TenantLayoutMigrator.InterruptedMigrationException expected) {
            Assert.assertTrue(Files.isRegularFile(TenantLayoutMigrator.journalFile(store)));
        }

        TenantLayoutMigrator.Result recovered = TenantLayoutMigrator.recover(store);
        Assert.assertTrue(Files.isRegularFile(ScopePaths.projectRoot(store, alice, "prj_alice").resolve("sentinel.txt")));
        Assert.assertTrue(Files.isRegularFile(ScopePaths.projectRoot(store, bob, "prj_bob").resolve("sentinel.txt")));
        Assert.assertEquals(Files.readString(ScopePaths.projectRoot(store, alice, "prj_alice").resolve("sentinel.txt")),
                "ALICE_ONLY");
        Assert.assertEquals(Files.readString(ScopePaths.projectRoot(store, bob, "prj_bob").resolve("sentinel.txt")),
                "BOB_ONLY");
        Assert.assertFalse(Files.exists(aliceLegacy.resolve("sentinel.txt")));
        Assert.assertFalse(Files.exists(bobLegacy.resolve("sentinel.txt")));
        Assert.assertTrue(recovered.migrated() >= 2);
    }

    @Test
    public void recoverIsIdempotentAcrossRepeatedInterruptionsAndPreservesContents() throws Exception {
        Path store = Files.createTempDirectory("mig-repeat");
        TenantId alice = TenantId.mint();
        TenantId bob = TenantId.mint();
        TenantId cara = TenantId.mint();
        Path aliceLegacy = store.resolve("same-example-com").resolve("prj_alice");
        Path bobLegacy = store.resolve("same-example-com").resolve("prj_bob");
        Path caraLegacy = store.resolve("same-example-com").resolve("prj_cara");
        Files.createDirectories(aliceLegacy);
        Files.createDirectories(bobLegacy);
        Files.createDirectories(caraLegacy);
        Files.writeString(aliceLegacy.resolve("project.json"), "{}");
        Files.writeString(aliceLegacy.resolve("sentinel.txt"), "ALICE_ONLY");
        Files.writeString(bobLegacy.resolve("project.json"), "{}");
        Files.writeString(bobLegacy.resolve("sentinel.txt"), "BOB_ONLY");
        Files.writeString(caraLegacy.resolve("project.json"), "{}");
        Files.writeString(caraLegacy.resolve("sentinel.txt"), "CARA_ONLY");
        Map<String, TenantId> known = Map.of(
                "prj_alice", alice,
                "prj_bob", bob,
                "prj_cara", cara);

        try {
            TenantLayoutMigrator.migrate(store, known, 1);
            Assert.fail("expected first interruption");
        } catch (TenantLayoutMigrator.InterruptedMigrationException expected) {
            Assert.assertTrue(Files.isRegularFile(TenantLayoutMigrator.journalFile(store)));
        }

        try {
            TenantLayoutMigrator.recover(store, 2);
            Assert.fail("expected second interruption during recover");
        } catch (TenantLayoutMigrator.InterruptedMigrationException expected) {
            Assert.assertTrue(Files.isRegularFile(TenantLayoutMigrator.journalFile(store)));
        }

        TenantLayoutMigrator.Result finished = TenantLayoutMigrator.recover(store);
        TenantLayoutMigrator.Result again = TenantLayoutMigrator.recover(store);
        Assert.assertEquals(again.migrated(), finished.migrated());
        Assert.assertEquals(
                Files.readString(ScopePaths.projectRoot(store, alice, "prj_alice").resolve("sentinel.txt")),
                "ALICE_ONLY");
        Assert.assertEquals(
                Files.readString(ScopePaths.projectRoot(store, bob, "prj_bob").resolve("sentinel.txt")),
                "BOB_ONLY");
        Assert.assertEquals(
                Files.readString(ScopePaths.projectRoot(store, cara, "prj_cara").resolve("sentinel.txt")),
                "CARA_ONLY");
        Assert.assertFalse(Files.readString(
                ScopePaths.projectRoot(store, alice, "prj_alice").resolve("sentinel.txt")).contains("BOB"));
        Assert.assertFalse(Files.readString(
                ScopePaths.projectRoot(store, bob, "prj_bob").resolve("sentinel.txt")).contains("ALICE"));
        Assert.assertFalse(Files.exists(aliceLegacy.resolve("sentinel.txt")));
        Assert.assertFalse(Files.exists(bobLegacy.resolve("sentinel.txt")));
        Assert.assertFalse(Files.exists(caraLegacy.resolve("sentinel.txt")));
        Assert.assertTrue(finished.migrated() >= 3);
    }
}
