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
        TenantId aliceId = TenantId.mint();
        TenantId bobId = TenantId.mint();
        Path alice = ScopePaths.projectRoot(store, aliceId, "prj_same");
        Path bob = ScopePaths.projectRoot(store, bobId, "prj_same");
        Assert.assertNotEquals(alice, bob);
        Assert.assertTrue(alice.startsWith(store.resolve("tenants").resolve(aliceId.value())));
        Assert.assertTrue(bob.startsWith(store.resolve("tenants").resolve(bobId.value())));
        Files.createDirectories(alice);
        Files.writeString(alice.resolve("secret.txt"), "alice");
        Assert.assertFalse(Files.exists(bob.resolve("secret.txt")));
    }

    @Test
    public void jobWorkDirectoriesAreExclusiveAndIgnoreHostTimestamps() throws Exception {
        Path work = Files.createTempDirectory("scope-work");
        TenantId tenant = TenantId.mint();
        Path first = ScopePaths.createJobWorkDir(work, tenant, "job_abc");
        Assert.assertTrue(Files.isDirectory(first));
        Assert.assertTrue(first.endsWith(Path.of("tenants", tenant.value(), "jobs", "job_abc")));
        try {
            ScopePaths.createJobWorkDir(work, tenant, "job_abc");
            Assert.fail("reusing a job work directory must fail exclusively");
        } catch (FileAlreadyExistsException expected) {
            Assert.assertTrue(Files.isDirectory(first));
        }
        Path other = ScopePaths.createJobWorkDir(work, TenantId.mint(), "job_abc");
        Assert.assertNotEquals(other, first);
    }
}
