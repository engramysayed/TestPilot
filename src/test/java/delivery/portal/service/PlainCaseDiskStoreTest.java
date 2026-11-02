package delivery.portal.service;

import delivery.excel.ManualTestCase;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class PlainCaseDiskStoreTest {

    @Test
    public void saveListLoadUpdate_roundTrip() throws Exception {
        Path root = Files.createTempDirectory("plain-cases-store-");
        PlainCaseDiskStore disk = new PlainCaseDiskStore();

        Map<String, Object> created = disk.saveNew(
                root,
                "pc_abc123",
                "Login smoke",
                List.of("click Login", "expect Welcome", "  ", "click Profile")
        );
        Assert.assertEquals(created.get("id"), "pc_abc123");
        Assert.assertEquals(created.get("title"), "Login smoke");
        @SuppressWarnings("unchecked")
        List<String> lines = (List<String>) created.get("lines");
        Assert.assertEquals(lines, List.of("click Login", "expect Welcome", "click Profile"));
        Assert.assertTrue(String.valueOf(created.get("createdAt")).length() > 5);
        Assert.assertTrue(Files.isRegularFile(root.resolve("plain-cases").resolve("pc_abc123.json")));

        List<Map<String, Object>> listed = disk.list(root);
        Assert.assertEquals(listed.size(), 1);
        Assert.assertEquals(listed.get(0).get("id"), "pc_abc123");

        Map<String, Object> loaded = disk.load(root, "pc_abc123").orElseThrow();
        Assert.assertEquals(loaded.get("title"), "Login smoke");

        Map<String, Object> updated = disk.update(
                root,
                "pc_abc123",
                "Login smoke v2",
                List.of("click Sign in", "expect Dashboard")
        );
        Assert.assertEquals(updated.get("title"), "Login smoke v2");
        @SuppressWarnings("unchecked")
        List<String> updatedLines = (List<String>) updated.get("lines");
        Assert.assertEquals(updatedLines, List.of("click Sign in", "expect Dashboard"));
        Assert.assertNotEquals(String.valueOf(updated.get("updatedAt")), String.valueOf(created.get("updatedAt")));
    }

    @Test
    public void toManualTestCase_splitsExpectLines() {
        Map<String, Object> plain = Map.of(
                "id", "pc_1",
                "title", "Login",
                "lines", List.of("click Login", "expect Welcome", "click Profile", "expect Settings")
        );
        ManualTestCase tc = PlainCaseService.toManualTestCase(plain);
        Assert.assertEquals(tc.tcId(), "TC_PC_1");
        Assert.assertEquals(tc.title(), "Login");
        Assert.assertEquals(tc.keelPath(), "EXECUTE");
        Assert.assertTrue(tc.steps().contains("1. click Login"));
        Assert.assertTrue(tc.steps().contains("2. click Profile"));
        Assert.assertTrue(tc.expectedResult().contains("1. Welcome"));
        Assert.assertTrue(tc.expectedResult().contains("2. Settings"));
        Assert.assertEquals(tc.tags(), "plain-case");
    }

    @Test(expectedExceptions = PlainCaseDiskStore.BadRequestException.class)
    public void saveNew_rejectsEmptyLines() throws Exception {
        Path root = Files.createTempDirectory("plain-cases-empty-");
        new PlainCaseDiskStore().saveNew(root, "pc_empty", "t", List.of("  ", ""));
    }
}
