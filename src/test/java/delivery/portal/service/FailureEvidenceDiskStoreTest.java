package delivery.portal.service;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class FailureEvidenceDiskStoreTest {

    @Test
    public void writeListGet_roundTripWithBlobs() throws Exception {
        Path root = Files.createTempDirectory("failure-evidence-");
        FailureEvidenceDiskStore disk = new FailureEvidenceDiskStore();
        String jobId = "exec_abc123";
        String tcId = "TC_1";

        Map<String, Object> written = disk.writeFailure(
                root,
                jobId,
                tcId,
                null,
                2,
                "element not found",
                "https://example.com/app",
                "prove",
                "png-bytes".getBytes(StandardCharsets.UTF_8),
                List.of(Map.of("url", "https://example.com/api", "status", 500)),
                List.of(Map.of("level", "severe", "text", "boom"))
        );
        Assert.assertEquals(written.get("tcId"), tcId);
        Assert.assertEquals(written.get("stepIndex"), 2);
        Assert.assertEquals(written.get("source"), "prove");
        Assert.assertEquals(written.get("hasScreen"), true);
        Assert.assertEquals(written.get("hasNetwork"), true);
        Assert.assertEquals(written.get("hasConsole"), true);

        Path meta = root.resolve("execute-runs").resolve(jobId).resolve("evidence")
                .resolve(String.valueOf(written.get("tcFolder")))
                .resolve("failure").resolve("2").resolve("meta.json");
        Assert.assertTrue(Files.isRegularFile(meta));

        List<Map<String, Object>> listed = disk.listFailures(root, jobId);
        Assert.assertEquals(listed.size(), 1);
        Assert.assertEquals(listed.get(0).get("reason"), "element not found");

        Map<String, Object> got = disk.getFailure(root, jobId, tcId, 2).orElseThrow();
        Assert.assertEquals(got.get("pageUrl"), "https://example.com/app");
        Assert.assertTrue(disk.resolveScreen(root, jobId, tcId, 2).isPresent());
        Assert.assertTrue(disk.resolveNetwork(root, jobId, tcId, 2).isPresent());
        Assert.assertTrue(disk.resolveConsole(root, jobId, tcId, 2).isPresent());
        Assert.assertTrue(disk.listFailuresForTc(root, jobId, tcId).size() == 1);
    }

    @Test
    public void write_omitsMissingBlobs() throws Exception {
        Path root = Files.createTempDirectory("failure-evidence-sparse-");
        FailureEvidenceDiskStore disk = new FailureEvidenceDiskStore();
        Map<String, Object> written = disk.writeFailure(
                root, "exec_x", "TC_2", "TC_2", 0, "timeout", null, "execute",
                null, null, null);
        Assert.assertEquals(written.get("hasScreen"), false);
        Assert.assertEquals(written.get("hasNetwork"), false);
        Assert.assertEquals(written.get("hasConsole"), false);
        Path dir = root.resolve("execute-runs/exec_x/evidence/TC_2/failure/0");
        Assert.assertTrue(Files.isRegularFile(dir.resolve("meta.json")));
        Assert.assertFalse(Files.exists(dir.resolve("screen.png")));
    }
}
