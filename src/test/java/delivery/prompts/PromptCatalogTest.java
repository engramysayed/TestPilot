package delivery.prompts;

import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class PromptCatalogTest {

    @AfterMethod
    public void cleanup() {
        System.clearProperty(PromptCatalog.PROP_DIR);
        PromptCatalog.setOverrideDir(null);
        PromptCatalog.clearCache();
    }

    @Test
    public void classpath_loadsFinalReviseAndNaming() {
        System.clearProperty(PromptCatalog.PROP_DIR);
        PromptCatalog.clearCache();
        String revise = PromptCatalog.get("revise/final-revise");
        Assert.assertTrue(revise.contains("You are TestPilot Final Revise"), revise);
        Assert.assertTrue(revise.contains("jobVerdict"), revise);
        String naming = PromptCatalog.get("codegen/test-method-naming");
        Assert.assertTrue(naming.contains("Return ONE Java test method identifier only"), naming);
        Assert.assertTrue(naming.contains("underscores between words"), naming);
    }

    @Test
    public void classpath_loadsAeCoreIds() {
        Assert.assertTrue(PromptCatalog.get("heal/pick").contains("candidateId"));
        Assert.assertTrue(PromptCatalog.get("heal/safety").startsWith("## Safety (must obey)"));
        Assert.assertTrue(PromptCatalog.get("hunt/ollama-system").contains("Bug Hunter"));
        Assert.assertTrue(PromptCatalog.get("vision/vision-template").contains("visual grounding"));
        Assert.assertTrue(PromptCatalog.get("grounding/locator-policy").contains("NEVER invent locators"));
        Assert.assertTrue(PromptCatalog.get("authoring/heal-pick-payload").contains("candidateId"));
    }

    @Test
    public void overrideDir_winsOverClasspath() throws Exception {
        Path dir = Files.createTempDirectory("keel-prompts-override");
        Path nested = dir.resolve("revise");
        Files.createDirectories(nested);
        String overrideBody = "OVERRIDE_FINAL_REVISE_BODY\n";
        Files.writeString(nested.resolve("final-revise.md"), overrideBody, StandardCharsets.UTF_8);

        System.setProperty(PromptCatalog.PROP_DIR, dir.toAbsolutePath().toString());
        PromptCatalog.clearCache();
        Assert.assertEquals(PromptCatalog.get("revise/final-revise"), overrideBody);

        // hot-reload: change file, next get sees new content
        String updated = "OVERRIDE_UPDATED\n";
        Files.writeString(nested.resolve("final-revise.md"), updated, StandardCharsets.UTF_8);
        Assert.assertEquals(PromptCatalog.get("revise/final-revise"), updated);
    }

    @Test
    public void setOverrideDir_winsOverProperty() throws Exception {
        Path propDir = Files.createTempDirectory("keel-prompts-prop");
        Files.createDirectories(propDir.resolve("heal"));
        Files.writeString(propDir.resolve("heal/pick.md"), "FROM_PROP\n", StandardCharsets.UTF_8);
        System.setProperty(PromptCatalog.PROP_DIR, propDir.toAbsolutePath().toString());

        Path progDir = Files.createTempDirectory("keel-prompts-prog");
        Files.createDirectories(progDir.resolve("heal"));
        Files.writeString(progDir.resolve("heal/pick.md"), "FROM_SETTER\n", StandardCharsets.UTF_8);
        PromptCatalog.setOverrideDir(progDir.toAbsolutePath());
        Assert.assertEquals(PromptCatalog.get("heal/pick"), "FROM_SETTER\n");
    }

    @Test
    public void format_replacesPlaceholders() {
        String out = PromptCatalog.format("heal/safety", Map.of(
                "crashBlock", "",
                "secretFlag", "true",
                "secretClause", ": NEVER echo\n",
                "sideEffectsWire", "none",
                "sideEffectsClause", ": read-only\n",
                "bannedBlock", "- Banned locators: (none)\n"
        ));
        Assert.assertTrue(out.startsWith("## Safety (must obey)\n"), out);
        Assert.assertTrue(out.contains("secret=true"), out);
        Assert.assertFalse(out.contains("{{"), out);
    }

    @Test
    public void unsetOverride_usesClasspath() {
        System.clearProperty(PromptCatalog.PROP_DIR);
        PromptCatalog.clearCache();
        String revise = PromptCatalog.get("revise/final-revise");
        Assert.assertTrue(revise.startsWith("You are TestPilot Final Revise"), revise);
    }
}
