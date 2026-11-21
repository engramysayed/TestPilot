package delivery.portal.web;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;

public class KeelVisualSignoffScriptTest {

    @Test
    public void visualSignoffScriptsPresent() throws Exception {
        Path live = Path.of("docs/design/keel-preview");
        Path archived = Path.of("docs/archive/design/keel-preview");
        Path root = Files.isDirectory(live) ? live : archived;
        Assert.assertTrue(Files.isRegularFile(root.resolve("visual-signoff.mjs")),
                "visual-signoff.mjs missing under " + root);
        Assert.assertTrue(Files.isRegularFile(root.resolve("visual-signoff.ps1")),
                "visual-signoff.ps1 missing under " + root);
        String mjs = Files.readString(root.resolve("visual-signoff.mjs"));
        Assert.assertTrue(mjs.contains("prod-login.png"));
        Assert.assertTrue(mjs.contains("mock-overview.png"));
    }
}
