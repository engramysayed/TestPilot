package delivery.store;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;

public class DomainStorePathsTest {
    @Test
    public void domainRootSitsAboveProjectFolders() {
        Path root = Path.of("delivery-store");
        Assert.assertEquals(
                DomainStorePaths.resolveDomainRoot(root, "https://opssit.axispay.app/login"),
                root.resolve("opssit-axispay-app"));
    }

    @Test
    public void sanitizesHostAndUrl() {
        Assert.assertEquals(DomainStorePaths.folderNameFromHostOrUrl("saucedemo.com"), "saucedemo-com");
        Assert.assertEquals(DomainStorePaths.folderNameFromHostOrUrl("https://www.saucedemo.com/inventory.html"),
                "saucedemo-com");
        Assert.assertEquals(DomainStorePaths.folderNameFromHostOrUrl("Facebook.com"), "facebook-com");
    }

    @Test
    public void resolvesNestedPreferringNewLayout() throws Exception {
        Path root = Files.createTempDirectory("domain-paths");
        Path nested = DomainStorePaths.resolveProjectRoot(root, "https://saucedemo.com", "prj_a");
        Assert.assertEquals(nested, root.resolve("saucedemo-com").resolve("prj_a"));
        Files.createDirectories(nested);
        Files.writeString(nested.resolve("project.json"), "{\"version\":1}");
        Assert.assertEquals(
                DomainStorePaths.resolveProjectRoot(root, "saucedemo.com", "prj_a"),
                nested);
    }

    @Test
    public void prefersLegacyFlatWhenPresent() throws Exception {
        Path root = Files.createTempDirectory("domain-flat");
        Path flat = root.resolve("saucedemo-com");
        Files.createDirectories(flat);
        Files.writeString(flat.resolve("project.json"), "{\"version\":1}");
        Path resolved = DomainStorePaths.resolveProjectRoot(root, "https://saucedemo.com", "saucedemo-com");
        Assert.assertEquals(resolved, flat);
    }

    @Test
    public void migrateMovesFlatProjectUnderDomain() throws Exception {
        Path root = Files.createTempDirectory("domain-mig");
        Path flat = root.resolve("example-com");
        Files.createDirectories(flat);
        Files.writeString(flat.resolve("project.json"), "{\"version\":1}");
        Files.writeString(root.resolve("portal-db.mv.db"), "keep");
        var moved = DomainStorePaths.migrateLegacyFlatProjects(root);
        Assert.assertFalse(moved.isEmpty());
        Assert.assertTrue(Files.isRegularFile(root.resolve("example-com/example-com/project.json")));
        Assert.assertTrue(Files.isRegularFile(root.resolve("portal-db.mv.db")));
    }
}
