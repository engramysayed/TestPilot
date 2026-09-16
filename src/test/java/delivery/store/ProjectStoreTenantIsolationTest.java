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
        ProjectStore alice = new ProjectStore(root, "https://same.example.com", TenantId.personal(1L));
        ProjectStore bob = new ProjectStore(root, "https://same.example.com", TenantId.personal(2L));
        Path fw = Files.createTempDirectory("fw-t");
        Files.writeString(fw.resolve("README.md"), "x");
        Path zip = Files.createTempFile("pkg-t", ".zip");
        Files.writeString(zip, "zip-a");
        alice.saveVersion("prj_same", fw, zip);
        Assert.assertTrue(alice.hasFramework("prj_same"));
        Assert.assertFalse(bob.hasFramework("prj_same"));
        Assert.assertTrue(alice.projectRoot("prj_same").toString().contains("ws_user_1"));
        Assert.assertTrue(bob.projectRoot("prj_same").toString().contains("ws_user_2"));
        Assert.assertNotEquals(alice.projectRoot("prj_same"), bob.projectRoot("prj_same"));
    }
}
