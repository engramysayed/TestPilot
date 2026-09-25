package delivery.portal.web;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;

public class KeelVisualSignoffScriptTest {

    @Test
    public void visualSignoffScriptsPresent() throws Exception {
        Path root = Path.of("docs/design/keel-preview");
        Assert.assertTrue(Files.isRegularFile(root.resolve("visual-signoff.mjs")));
        Assert.assertTrue(Files.isRegularFile(root.resolve("visual-signoff.ps1")));
        String mjs = Files.readString(root.resolve("visual-signoff.mjs"));
        Assert.assertTrue(mjs.contains("prod-login.png"));
        Assert.assertTrue(mjs.contains("mock-overview.png"));
    }
}
