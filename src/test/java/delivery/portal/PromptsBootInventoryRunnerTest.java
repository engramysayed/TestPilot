package delivery.portal;

import delivery.portal.service.AdminPromptsService;
import delivery.prompts.PromptCatalog;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class PromptsBootInventoryRunnerTest {

    /** Golden fence matching resources/prompts at tip (excl VERSION). */
    private static final Set<String> GOLDEN_IDS = Set.of(
            "authoring/authoring-review",
            "authoring/authoring-review-cursor",
            "authoring/authoring-service",
            "authoring/heal-pick-payload",
            "codegen/test-method-naming",
            "grounding/free-invent",
            "grounding/hunt-planner",
            "grounding/locator-policy",
            "grounding/login-prelude",
            "grounding/vision-heal",
            "heal/ground-rank",
            "heal/ground-rank-batch",
            "heal/hunt-cursor",
            "heal/invent",
            "heal/pick",
            "heal/safety",
            "heal/solve",
            "hunt/ollama-system",
            "revise/final-revise",
            "vision/design-compare",
            "vision/qwen",
            "vision/qwen-assert-retry",
            "vision/ui-tars",
            "vision/ui-tars-assert",
            "vision/ui-tars-assert-retry",
            "vision/vision-few-shot",
            "vision/vision-template"
    );

    @AfterMethod
    public void cleanup() {
        System.clearProperty(PromptCatalog.PROP_DIR);
        PromptCatalog.setOverrideDir(null);
        PromptCatalog.clearCache();
    }

    @Test
    public void overrideUnset_inventoryClasspathOnly_noFail() throws Exception {
        PromptCatalog.setOverrideDir(null);
        PromptsBootInventoryRunner runner = newRunner();
        List<PromptsBootInventoryRunner.InventoryEntry> entries = runner.inventoryAndValidate();
        Assert.assertEquals(entries.size(), GOLDEN_IDS.size(), "scanned id count");
        TreeSet<String> scanned = new TreeSet<>();
        for (PromptsBootInventoryRunner.InventoryEntry e : entries) {
            scanned.add(e.id());
            Assert.assertEquals(e.source(), AdminPromptsService.SOURCE_CLASSPATH, e.id());
            Assert.assertNotNull(e.sha256());
            Assert.assertEquals(e.sha256().length(), 12, e.id());
        }
        Assert.assertEquals(scanned, new TreeSet<>(GOLDEN_IDS));
        Assert.assertEquals(PromptCatalog.version(), "2026.11.02");
    }

    @Test
    public void overrideComplete_allSourcesOverride_ok() throws Exception {
        Path dir = Files.createTempDirectory("keel-boot-inv-complete");
        AdminPromptsService svc = new AdminPromptsService();
        for (String id : svc.classpathIds()) {
            Path target = dir.resolve(id + ".md");
            Files.createDirectories(target.getParent());
            Files.writeString(target, "OVERRIDE:" + id + "\n", StandardCharsets.UTF_8);
        }
        PromptCatalog.setOverrideDir(dir.toAbsolutePath());
        PromptsBootInventoryRunner runner = newRunner();
        List<PromptsBootInventoryRunner.InventoryEntry> entries = runner.inventoryAndValidate();
        Assert.assertEquals(entries.size(), GOLDEN_IDS.size());
        for (PromptsBootInventoryRunner.InventoryEntry e : entries) {
            Assert.assertEquals(e.source(), AdminPromptsService.SOURCE_OVERRIDE, e.id());
            String expectedSha = PromptsBootInventoryRunner.shortSha256("OVERRIDE:" + e.id() + "\n");
            Assert.assertEquals(e.sha256(), expectedSha, e.id());
        }
    }

    @Test
    public void overrideIncomplete_failFastNamesMissingId() throws Exception {
        Path dir = Files.createTempDirectory("keel-boot-inv-incomplete");
        AdminPromptsService svc = new AdminPromptsService();
        for (String id : svc.classpathIds()) {
            if ("heal/pick".equals(id)) {
                continue;
            }
            Path target = dir.resolve(id + ".md");
            Files.createDirectories(target.getParent());
            Files.writeString(target, "x\n", StandardCharsets.UTF_8);
        }
        PromptCatalog.setOverrideDir(dir.toAbsolutePath());
        PromptsBootInventoryRunner runner = newRunner();
        try {
            runner.inventoryAndValidate();
            Assert.fail("expected IllegalStateException");
        } catch (IllegalStateException e) {
            Assert.assertTrue(e.getMessage().contains("heal/pick"), e.getMessage());
            Assert.assertTrue(e.getMessage().contains("incomplete"), e.getMessage());
        }
    }

    @Test
    public void shortSha256_stableForFixedBytes() {
        Assert.assertEquals(
                PromptsBootInventoryRunner.shortSha256("hello\n"),
                "5891b5b522d5");
    }

    private static PromptsBootInventoryRunner newRunner() {
        return new PromptsBootInventoryRunner(new AdminPromptsService(), new PromptsDirBinder());
    }
}
