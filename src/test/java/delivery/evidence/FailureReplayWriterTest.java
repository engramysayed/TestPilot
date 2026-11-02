package delivery.evidence;

import delivery.prompts.PromptCatalog;
import org.json.JSONObject;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class FailureReplayWriterTest {

    @AfterMethod
    public void clearProps() {
        System.clearProperty(Phase2EvidenceConfig.PROP_FAILURE_EVIDENCE);
        FailureDiagCapture.clearCurrent();
    }

    @Test
    public void writesMetaAndScreenOmitsEmptyNetworkConsole() throws Exception {
        Path tmp = Files.createTempDirectory("failure-replay-writer-");
        Path tcDir = tmp.resolve("evidence").resolve("TC_1");
        Files.createDirectories(tcDir);
        byte[] png = new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47};
        Path written = FailureReplayWriter.write(new FailureReplayWriter.CaptureRequest(
                tcDir, "job-1", "TC_1", 2, "locator miss", "https://ex/app", "prove", png), null);
        Assert.assertTrue(Files.isDirectory(written));
        Assert.assertTrue(Files.isRegularFile(FailureReplayPaths.screen(written)));
        Assert.assertFalse(Files.isRegularFile(FailureReplayPaths.network(written)));
        Assert.assertFalse(Files.isRegularFile(FailureReplayPaths.console(written)));
        JSONObject meta = new JSONObject(Files.readString(FailureReplayPaths.meta(written), StandardCharsets.UTF_8));
        Assert.assertEquals(meta.getString("jobId"), "job-1");
        Assert.assertEquals(meta.getString("tcId"), "TC_1");
        Assert.assertEquals(meta.getInt("stepIndex"), 2);
        Assert.assertTrue(meta.getBoolean("hasScreen"));
        Assert.assertFalse(meta.getBoolean("hasNetwork"));
        Assert.assertFalse(meta.getBoolean("hasConsole"));
        Assert.assertEquals(meta.getString("source"), "prove");
        Assert.assertTrue(meta.has("promptsVersion"));
        Assert.assertEquals(meta.getString("promptsVersion"), PromptCatalog.version());
        Assert.assertFalse(meta.getString("promptsVersion").isBlank());
        Assert.assertEquals(written, tcDir.resolve("failure").resolve("2"));
    }

    @Test
    public void disabledFlagSkipsWrite() throws Exception {
        Path tmp = Files.createTempDirectory("failure-replay-disabled-");
        System.setProperty(Phase2EvidenceConfig.PROP_FAILURE_EVIDENCE, "false");
        Path written = FailureReplayWriter.write(new FailureReplayWriter.CaptureRequest(
                tmp, "j", "TC_1", 0, "x", null, "execute", new byte[] {1}), null);
        Assert.assertNull(written);
    }

    @Test
    public void stampsPromptsVersionFromCatalog() throws Exception {
        Path tmp = Files.createTempDirectory("failure-replay-prompts-ver-");
        Path tcDir = tmp.resolve("evidence").resolve("TC_2");
        Files.createDirectories(tcDir);
        Path written = FailureReplayWriter.write(new FailureReplayWriter.CaptureRequest(
                tcDir, "job-2", "TC_2", 0, "timeout", null, "execute", null), null);
        Assert.assertNotNull(written);
        JSONObject meta = new JSONObject(Files.readString(FailureReplayPaths.meta(written), StandardCharsets.UTF_8));
        Assert.assertEquals(meta.getString("promptsVersion"), PromptCatalog.version());
        Assert.assertEquals(meta.getString("source"), "execute");
    }
}