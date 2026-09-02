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
}
