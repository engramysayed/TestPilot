package delivery.portal.service;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class FlakyTagDiskStoreTest {

    @Test
    public void addListClear_roundTrip() throws Exception {
        Path root = Files.createTempDirectory("flaky-tags-");
        FlakyTagDiskStore disk = new FlakyTagDiskStore();

        Map<String, Object> created = disk.add(root, "TC_9", 3, "intermittent timeout", "exec_1", "timeout");
        Assert.assertEquals(created.get("tag"), "flaky");
        Assert.assertEquals(created.get("signal"), "timeout");
        Assert.assertEquals(created.get("stepIndex"), 3);
        Assert.assertNull(created.get("clearedAt"));
        Assert.assertTrue(Files.isRegularFile(root.resolve("flaky-tags.json")));

        List<Map<String, Object>> active = disk.list(root, false);
        Assert.assertEquals(active.size(), 1);

        Map<String, Object> cleared = disk.clear(root, String.valueOf(created.get("tagId")));
        Assert.assertNotNull(cleared.get("clearedAt"));
        Assert.assertEquals(disk.list(root, false).size(), 0);
        Assert.assertEquals(disk.list(root, true).size(), 1);
    }

    @Test
    public void add_wholeCase_nullStep() throws Exception {
        Path root = Files.createTempDirectory("flaky-case-");
        Map<String, Object> created = new FlakyTagDiskStore().add(
                root, "TC_A", null, "flaky case", null, "manual");
        Assert.assertNull(created.get("stepIndex"));
        Assert.assertEquals(created.get("signal"), "manual");
    }
}
