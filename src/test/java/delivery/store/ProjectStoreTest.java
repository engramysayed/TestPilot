package delivery.store;

import delivery.excel.ManualTestCase;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class ProjectStoreTest {
    @Test
    public void roundTripVersion() throws Exception {
        Path root = Files.createTempDirectory("store");
        ProjectStore store = new ProjectStore(root);
        Path framework = Files.createTempDirectory("fw");
        Files.writeString(framework.resolve("README.md"), "x");
        Path zip = Files.createTempFile("pkg", ".zip");
        Files.writeString(zip, "zip");
        var saved = store.saveVersion("prj1", framework, zip);
        Assert.assertEquals(saved.version(), 1);
        Assert.assertTrue(store.hasFramework("prj1"));
        Assert.assertTrue(Files.exists(root.resolve("prj1/versions/v1.zip")));
    }

    @Test
    public void deleteProjectRemovesDirectory() throws Exception {
        Path root = Files.createTempDirectory("store-del");
        ProjectStore store = new ProjectStore(root);
        Path framework = Files.createTempDirectory("fw-del");
        Files.writeString(framework.resolve("README.md"), "x");
        Path zip = Files.createTempFile("pkg-del", ".zip");
        Files.writeString(zip, "zip");
        store.saveVersion("prj_del", framework, zip);
        Assert.assertTrue(store.hasFramework("prj_del"));
        store.deleteProject("prj_del");
        Assert.assertFalse(Files.exists(root.resolve("prj_del")));
        Assert.assertFalse(store.hasFramework("prj_del"));
    }

    @Test
    public void diffDetectsChangedSteps() {
        ManualTestCase a = new ManualTestCase("TC_001", "t", "", "step1", "ok", "", "");
        ManualTestCase b = new ManualTestCase("TC_001", "t", "", "step2", "ok", "", "");
        TcDiffService diff = new TcDiffService();
        var result = diff.diff(List.of(b), Map.of("TC_001", a.contentHash()));
        Assert.assertEquals(result.toAuthor().size(), 1);
    }

    @Test
    public void saveVersionScrubsLocalSecretProperties() throws Exception {
        Path root = Files.createTempDirectory("store-scrub");
        ProjectStore store = new ProjectStore(root);
        Path framework = Files.createTempDirectory("fw-scrub");
        Files.writeString(framework.resolve("README.md"), "ok");
        Path data = framework.resolve("src/test/resources/test-data");
        Files.createDirectories(data);
        Files.writeString(data.resolve("delivery-testdata.properties"), "k=v\n");
        Files.writeString(data.resolve("delivery-secrets.local.properties"), "password=SECRET_CANARY\n");
        Files.writeString(framework.resolve("delivery-target.local.properties"), "TARGET_PASSWORD=SECRET_CANARY\n");
        Path zip = Files.createTempFile("pkg-scrub", ".zip");
        // Build a real zip that also excludes secrets (packager contract)
        delivery.packager.FrameworkPackager packager = new delivery.packager.FrameworkPackager();
        packager.zip(framework, zip);
        try (java.util.zip.ZipFile zf = new java.util.zip.ZipFile(zip.toFile())) {
            Assert.assertNull(zf.getEntry("src/test/resources/test-data/delivery-secrets.local.properties"),
                    "ZIP must still exclude secrets file");
            Assert.assertNull(zf.getEntry("delivery-target.local.properties"));
            Assert.assertNotNull(zf.getEntry("src/test/resources/test-data/delivery-testdata.properties"));
        }
        var saved = store.saveVersion("prj_scrub", framework, zip);
        Assert.assertEquals(saved.version(), 1);
        Path stored = root.resolve("prj_scrub/framework");
        Assert.assertTrue(Files.exists(stored.resolve("README.md")));
        Assert.assertTrue(Files.exists(stored.resolve("src/test/resources/test-data/delivery-testdata.properties")));
        Assert.assertFalse(Files.exists(stored.resolve("src/test/resources/test-data/delivery-secrets.local.properties")),
                "store framework/ must not retain delivery-secrets.local.properties");
        Assert.assertFalse(Files.exists(stored.resolve("delivery-target.local.properties")),
                "store framework/ must not retain delivery-target.local.properties");
    }
}
