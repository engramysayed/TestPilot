package delivery.store;

import delivery.identity.TenantId;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;

public class ProjectStoreTenantIsolationTest {

    @Test
    public void twoTenantsCanUseTheSameProjectIdWithoutSharingFiles() throws Exception {
        Path root = Files.createTempDirectory("store-tenant");
        TenantId aliceId = TenantId.mint();
        TenantId bobId = TenantId.mint();
        ProjectStore alice = new ProjectStore(root, "https://same.example.com", aliceId);
        ProjectStore bob = new ProjectStore(root, "https://same.example.com", bobId);
        Path fw = Files.createTempDirectory("fw-t");
        Files.writeString(fw.resolve("README.md"), "x");
        Path zip = Files.createTempFile("pkg-t", ".zip");
        Files.writeString(zip, "zip-a");
        alice.saveVersion("prj_same", fw, zip);
        Assert.assertTrue(alice.hasFramework("prj_same"));
        Assert.assertFalse(bob.hasFramework("prj_same"));
        Assert.assertTrue(alice.projectRoot("prj_same").toString().contains(aliceId.value()));
        Assert.assertTrue(bob.projectRoot("prj_same").toString().contains(bobId.value()));
        Assert.assertNotEquals(alice.projectRoot("prj_same"), bob.projectRoot("prj_same"));
    }
}
