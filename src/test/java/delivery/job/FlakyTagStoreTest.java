package delivery.job;

import delivery.evidence.Phase2EvidenceConfig;
import org.json.JSONArray;
import org.json.JSONObject;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class FlakyTagStoreTest {

    @AfterMethod
    public void clear() {
        System.clearProperty(Phase2EvidenceConfig.PROP_FLAKY_EMIT);
    }

    @Test
    public void appendCreatesArrayWithFlakyTag() throws Exception {
        Path tmp = Files.createTempDirectory("flaky-tag-store-");
        String id = FlakyTagStore.append(tmp, "TC_9", 1, "timed out waiting", "job-a", "timeout");
        Assert.assertNotNull(id);
        Path file = tmp.resolve(FlakyTagStore.FILE);
        Assert.assertTrue(Files.isRegularFile(file));
        JSONArray arr = new JSONArray(Files.readString(file, StandardCharsets.UTF_8));
        Assert.assertEquals(arr.length(), 1);
        JSONObject row = arr.getJSONObject(0);
        Assert.assertEquals(row.getString("tag"), "flaky");
        Assert.assertEquals(row.getString("tcId"), "TC_9");
        Assert.assertEquals(row.getInt("stepIndex"), 1);
        Assert.assertEquals(row.getString("signal"), "timeout");
        Assert.assertTrue(row.isNull("clearedAt"));
    }

    @Test
    public void signalFromReasonMapsTimeoutAndHeal() {
        Assert.assertEquals(FlakyTagStore.signalFromReason("TimeoutException: wait", null), "timeout");
        Assert.assertEquals(FlakyTagStore.signalFromReason("still missing", "retry"), "heal_retry");
        Assert.assertEquals(FlakyTagStore.signalFromReason("intermittent flake", "none"), "intermittent");
    }
}
