package delivery.prompts;

import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class PromptCatalogVersionTest {

    @AfterMethod
    public void cleanup() {
        System.clearProperty(PromptCatalog.PROP_DIR);
        PromptCatalog.setOverrideDir(null);
        PromptCatalog.clearCache();
    }

    @Test
    public void version_resolvesClasspathPack() {
        System.clearProperty(PromptCatalog.PROP_DIR);
        PromptCatalog.setOverrideDir(null);
        PromptCatalog.clearCache();
        String v = PromptCatalog.version();
        Assert.assertEquals(v, "2026.11.07", v);
    }

    @Test
    public void version_overrideWins() throws Exception {
        Path dir = Files.createTempDirectory("keel-prompts-ver");
        Files.writeString(dir.resolve("VERSION"), "9.9.9-override\n", StandardCharsets.UTF_8);
        PromptCatalog.setOverrideDir(dir.toAbsolutePath());
        Assert.assertEquals(PromptCatalog.version(), "9.9.9-override");
    }

    @Test
    public void tryOverrideText_saveReloadRoundTrip() throws Exception {
        Path dir = Files.createTempDirectory("keel-prompts-ov");
        Files.createDirectories(dir.resolve("heal"));
        PromptCatalog.setOverrideDir(dir.toAbsolutePath());
        Assert.assertTrue(PromptCatalog.tryOverrideText("heal/pick").isEmpty());
        Files.writeString(dir.resolve("heal/pick.md"), "OVERRIDE_PICK\n", StandardCharsets.UTF_8);
        Assert.assertEquals(PromptCatalog.tryOverrideText("heal/pick").orElseThrow(), "OVERRIDE_PICK\n");
        Assert.assertTrue(PromptCatalog.hasOverrideFile("heal/pick"));
        Assert.assertTrue(PromptCatalog.tryClasspathText("heal/pick").orElse("").contains("candidateId"));
    }
}
