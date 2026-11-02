package delivery.portal.service;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class HealProposalDiskStoreTest {

    @Test
    public void createListReject_roundTrip() throws Exception {
        Path root = Files.createTempDirectory("heal-proposals-");
        HealProposalDiskStore disk = new HealProposalDiskStore();

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("proposalId", "hp_test1");
        input.put("projectId", "proj1");
        input.put("jobId", "exec_1");
        input.put("tcId", "TC_1");
        input.put("stepIndex", 1);
        input.put("pageClass", "Login_Page");
        input.put("locatorBefore", Map.of("strategy", "cssSelector", "value", "#old"));
        input.put("locatorAfter", Map.of("strategy", "cssSelector", "value", "#new"));
        input.put("tier", "ollama");
        input.put("reason", "stale locator");

        Map<String, Object> created = disk.create(root, input);
        Assert.assertEquals(created.get("status"), "pending");
        Assert.assertTrue(Files.isRegularFile(root.resolve("heal-proposals/hp_test1.json")));

        List<Map<String, Object>> pending = disk.list(root, "pending");
        Assert.assertEquals(pending.size(), 1);

        Map<String, Object> rejected = disk.markStatus(root, "hp_test1", "rejected");
        Assert.assertEquals(rejected.get("status"), "rejected");
        Assert.assertNotNull(rejected.get("decidedAt"));
        Assert.assertEquals(disk.list(root, "pending").size(), 0);
    }

    @Test
    public void accept_patchesExistingPageOnly() throws Exception {
        Path root = Files.createTempDirectory("heal-accept-");
        Path page = root.resolve("src/main/java/project/pages/Login_Page.java");
        Files.createDirectories(page.getParent());
        String java = """
                package project.pages;
                import org.openqa.selenium.By;
                public class Login_Page {
                    protected final By user_Locator = By.cssSelector("#old");
                    public void click() { }
                }
                """;
        Files.writeString(page, java);

        HealProposalDiskStore disk = new HealProposalDiskStore();
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("proposalId", "hp_acc1");
        input.put("projectId", "p");
        input.put("jobId", "exec_1");
        input.put("tcId", "TC_1");
        input.put("stepIndex", 0);
        input.put("pageClass", "Login_Page");
        input.put("locatorBefore", Map.of("strategy", "cssSelector", "value", "#old"));
        input.put("locatorAfter", Map.of("strategy", "cssSelector", "value", "[data-test=user]"));
        disk.create(root, input);

        HealJavaLocatorPatcher.PatchOutcome patch = HealJavaLocatorPatcher.applyLocatorPatch(
                root,
                "Login_Page",
                Map.of("strategy", "cssSelector", "value", "#old"),
                Map.of("strategy", "cssSelector", "value", "[data-test=user]")
        );
        Assert.assertTrue(patch.changed());
        String updated = Files.readString(page);
        Assert.assertTrue(updated.contains("By.cssSelector(\"[data-test=user]\")"));
        Assert.assertFalse(updated.contains("#old"));

        Map<String, Object> accepted = disk.markStatus(root, "hp_acc1", "accepted");
        Assert.assertEquals(accepted.get("status"), "accepted");
    }

    @Test(expectedExceptions = HealJavaLocatorPatcher.MissingPageException.class)
    public void accept_missingPage_doesNotCreate() throws Exception {
        Path root = Files.createTempDirectory("heal-missing-page-");
        HealJavaLocatorPatcher.applyLocatorPatch(
                root,
                "Missing_Page",
                Map.of("strategy", "id", "value", "a"),
                Map.of("strategy", "id", "value", "b")
        );
    }
}
