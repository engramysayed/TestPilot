package delivery.portal.service;

import delivery.prompts.PromptCatalog;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class AdminPromptsServiceTest {

    @AfterMethod
    public void cleanup() {
        System.clearProperty(PromptCatalog.PROP_DIR);
        PromptCatalog.setOverrideDir(null);
        PromptCatalog.clearCache();
    }

    @Test
    public void list_nonEmptyFromClasspath() throws Exception {
        PromptCatalog.setOverrideDir(null);
        AdminPromptsService svc = new AdminPromptsService();
        Map<String, Object> list = svc.listPrompts();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) list.get("items");
        Assert.assertNotNull(items);
        Assert.assertTrue(items.size() >= 5, "expected classpath prompts, got " + items.size());
        Assert.assertEquals(list.get("promptsVersion"), "2026.11.02");
        Assert.assertEquals(list.get("overrideDirConfigured"), Boolean.FALSE);
        boolean sawPick = items.stream().anyMatch(r -> "heal/pick".equals(r.get("id")));
        Assert.assertTrue(sawPick);
    }

    @Test
    public void get_classpathText() {
        PromptCatalog.setOverrideDir(null);
        AdminPromptsService svc = new AdminPromptsService();
        Map<String, Object> one = svc.getPrompt("heal/pick");
        Assert.assertEquals(one.get("id"), "heal/pick");
        Assert.assertEquals(one.get("source"), "classpath");
        Assert.assertTrue(String.valueOf(one.get("text")).contains("candidateId"));
        Assert.assertTrue(String.valueOf(one.get("classpathText")).contains("candidateId"));
    }

    @Test
    public void save_requiresOverrideDir() {
        PromptCatalog.setOverrideDir(null);
        AdminPromptsService svc = new AdminPromptsService();
        try {
            svc.savePrompt("heal/pick", "x");
            Assert.fail("expected UnsetDirException");
        } catch (AdminPromptsService.UnsetDirException e) {
            Assert.assertTrue(e.getMessage().contains("keel.prompts.dir"), e.getMessage());
            Assert.assertTrue(e.getMessage().contains("KEEL_PROMPTS_DIR"), e.getMessage());
        } catch (Exception e) {
            Assert.fail("wrong exception: " + e);
        }
    }

    @Test
    public void save_andReloadFromTempOverride() throws Exception {
        Path dir = Files.createTempDirectory("keel-admin-prompts");
        PromptCatalog.setOverrideDir(dir.toAbsolutePath());
        AdminPromptsService svc = new AdminPromptsService();
        Map<String, Object> saved = svc.savePrompt("heal/pick", "ADMIN_OVERRIDE_BODY\n");
        Assert.assertEquals(saved.get("source"), "override");
        Assert.assertEquals(saved.get("text"), "ADMIN_OVERRIDE_BODY\n");
        Assert.assertTrue(String.valueOf(saved.get("classpathText")).contains("candidateId"));
        Assert.assertEquals(
                Files.readString(dir.resolve("heal/pick.md"), StandardCharsets.UTF_8),
                "ADMIN_OVERRIDE_BODY\n");
        Map<String, Object> again = svc.getPrompt("heal/pick");
        Assert.assertEquals(again.get("text"), "ADMIN_OVERRIDE_BODY\n");
        Map<String, Object> diff = svc.diffPrompt("heal/pick");
        Assert.assertEquals(diff.get("identical"), Boolean.FALSE);
        Assert.assertTrue(String.valueOf(diff.get("unified")).contains("ADMIN_OVERRIDE_BODY"));
    }
}
