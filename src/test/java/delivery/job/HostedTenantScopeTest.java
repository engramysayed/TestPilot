package delivery.job;

import delivery.identity.ScopePaths;
import delivery.identity.TenantId;
import delivery.store.DomainStorePaths;
import delivery.store.ProjectStore;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;

public class HostedTenantScopeTest {

    @Test
    public void hostedWorkDirFailsClosedWhenTenantOrJobIsMissing() throws Exception {
        Path work = Files.createTempDirectory("hosted-work");
        ConversionJobRequest missing = new ConversionJobRequest(
                "prj_hosted",
                Path.of("x.xlsx"),
                "https://same.example.com",
                "",
                "",
                work,
                work,
                Path.of("template"),
                "NEW",
                "",
                "",
                false,
                false,
                delivery.authoring.AuthoringEngine.KEEL,
                delivery.authoring.PrecisionJobConfig.DEFAULTS,
                null,
                null,
                TenantScope.HOSTED
        );
        try {
            JobWorkLayout.create(missing);
            Assert.fail("hosted jobs must not fall back to a legacy work directory");
        } catch (IllegalStateException expected) {
            Assert.assertTrue(expected.getMessage().contains("TENANT_REQUIRED"));
        }
        try (var stream = Files.list(work)) {
            Assert.assertEquals(stream.count(), 0, "no legacy host-timestamp folder may be created");
        }
    }

    @Test
    public void hostedProjectStoreDoesNotReadLegacyDomainFolders() throws Exception {
        Path storeRoot = Files.createTempDirectory("hosted-store");
        Path legacy = DomainStorePaths.resolveProjectRoot(storeRoot, "https://same.example.com", "prj_same");
        Files.createDirectories(legacy.resolve("framework"));
        Files.writeString(legacy.resolve("framework/secret.txt"), "LEGACY");
        TenantId tenant = TenantId.mint();
        ProjectStore hosted = new ProjectStore(storeRoot, "https://same.example.com", tenant);
        Assert.assertFalse(hosted.hasFramework("prj_same"),
                "hosted tenant stores must not dual-read legacy domain folders");
        Assert.assertTrue(hosted.projectRoot("prj_same").startsWith(
                ScopePaths.tenantRoot(storeRoot, tenant)));
        Assert.assertTrue(Files.isRegularFile(legacy.resolve("framework/secret.txt")));
    }
}
