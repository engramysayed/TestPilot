package delivery.store;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;

public class GeneratedStoreLayoutTest {

    @Test
    public void usesProjectRootGeneratedWhenAlreadyThere() throws Exception {
        Path storeRoot = Files.createTempDirectory("gen-layout");
        String projectId = "prj_ops";
        Path nested = storeRoot.resolve("opssit-axispay-app").resolve(projectId);
        Files.createDirectories(nested.resolve("generated"));
        Files.writeString(nested.resolve("generated").resolve("latest.xlsx"), "nested");
        Files.writeString(nested.resolve("project.json"), "{\"version\":1}");

        Path resolved = GeneratedStoreLayout.resolveGeneratedDir(storeRoot, nested, projectId);
        Assert.assertEquals(resolved, nested.resolve("generated"));
        Assert.assertEquals(Files.readString(resolved.resolve("latest.xlsx")), "nested");
    }

    @Test
    public void movesFlatGeneratedIntoOpsProjectFolder() throws Exception {
        Path storeRoot = Files.createTempDirectory("gen-mig");
        String projectId = "prj_ops";
        Path nested = storeRoot.resolve("opssit-axispay-app").resolve(projectId);
        Files.createDirectories(nested);
        Files.writeString(nested.resolve("project.json"), "{\"version\":1}");

        Path flat = storeRoot.resolve(projectId).resolve("generated");
        Files.createDirectories(flat);
        Files.writeString(flat.resolve("latest.xlsx"), "library");
        Files.writeString(flat.resolve("latest.csv"), "csv");

        Path resolved = GeneratedStoreLayout.resolveGeneratedDir(storeRoot, nested, projectId);
        Assert.assertEquals(resolved, nested.resolve("generated"));
        Assert.assertEquals(Files.readString(resolved.resolve("latest.xlsx")), "library");
        Assert.assertEquals(Files.readString(resolved.resolve("latest.csv")), "csv");
        Assert.assertFalse(Files.isRegularFile(flat.resolve("latest.xlsx")));
    }
}
