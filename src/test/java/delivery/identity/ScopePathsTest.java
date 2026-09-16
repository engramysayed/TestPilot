package delivery.identity;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ScopePathsTest {

    @Test
    public void twoTenantsWithTheSameProjectIdDoNotShareARoot() throws Exception {
        Path store = Files.createTempDirectory("scope-store");
        Path alice = ScopePaths.projectRoot(store, TenantId.personal(1L), "prj_same");
        Path bob = ScopePaths.projectRoot(store, TenantId.personal(2L), "prj_same");
        Assert.assertNotEquals(alice, bob);
        Assert.assertTrue(alice.startsWith(store.resolve("tenants").resolve("ws_user_1")));
        Assert.assertTrue(bob.startsWith(store.resolve("tenants").resolve("ws_user_2")));
        Files.createDirectories(alice);
        Files.writeString(alice.resolve("secret.txt"), "alice");
        Assert.assertFalse(Files.exists(bob.resolve("secret.txt")));
    }

    @Test
    public void jobWorkDirectoriesAreExclusiveAndIgnoreHostTimestamps() throws Exception {
        Path work = Files.createTempDirectory("scope-work");
        TenantId tenant = TenantId.personal(7L);
        Path first = ScopePaths.createJobWorkDir(work, tenant, "job_abc");
        Assert.assertTrue(Files.isDirectory(first));
        Assert.assertTrue(first.endsWith(Path.of("tenants", "ws_user_7", "jobs", "job_abc")));
        try {
            ScopePaths.createJobWorkDir(work, tenant, "job_abc");
            Assert.fail("reusing a job work directory must fail exclusively");
        } catch (FileAlreadyExistsException expected) {
            Assert.assertTrue(Files.isDirectory(first));
        }
        Path other = ScopePaths.createJobWorkDir(work, TenantId.personal(8L), "job_abc");
        Assert.assertNotEquals(other, first);
    }
}
