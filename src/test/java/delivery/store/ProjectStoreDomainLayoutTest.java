package delivery.store;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;

public class ProjectStoreDomainLayoutTest {
    @Test
    public void saveVersionUsesDomainProjectLayout() throws Exception {
        Path root = Files.createTempDirectory("store-nested");
        ProjectStore store = new ProjectStore(root, "https://saucedemo.com");
        Path framework = Files.createTempDirectory("fw-n");
        Files.writeString(framework.resolve("README.md"), "x");
        Path zip = Files.createTempFile("pkg-n", ".zip");
        Files.writeString(zip, "zip");
        store.saveVersion("prj_nested", framework, zip);
        Path expected = root.resolve("saucedemo-com").resolve("prj_nested");
        Assert.assertTrue(Files.isDirectory(expected.resolve("framework")));
        Assert.assertTrue(Files.exists(expected.resolve("versions/v1.zip")));
        Assert.assertTrue(store.hasFramework("prj_nested"));
    }
}
