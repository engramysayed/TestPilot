package delivery.job;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class SuiteStoreTest {

    @Test
    public void loadParsesContractFields() throws Exception {
        Path root = Files.createTempDirectory("suite-store-");
        Files.writeString(root.resolve(SuiteStore.FILE), """
                [{
                  "suiteId":"s1",
                  "name":"Smoke",
                  "tags":["smoke"],
                  "tcIds":["TC_1","TC_2"],
                  "builtIn":true,
                  "alertChannelId":"slack",
                  "schedule":{"enabled":true,"kind":"interval","intervalMinutes":15},
                  "monitor":{"enabled":true,"target":"staging"}
                }]
                """, StandardCharsets.UTF_8);
        List<SuiteStore.Suite> suites = SuiteStore.load(root);
        Assert.assertEquals(suites.size(), 1);
        SuiteStore.Suite s = suites.get(0);
        Assert.assertEquals(s.suiteId(), "s1");
        Assert.assertEquals(s.tags(), List.of("smoke"));
        Assert.assertEquals(s.tcIds(), List.of("TC_1", "TC_2"));
        Assert.assertEquals(s.alertChannelId(), "slack");
        Assert.assertEquals(s.monitorTarget().orElse(""), "staging");
        Assert.assertEquals(SuiteStore.resolveTcIds(root, null, "SMOKE"), List.of("TC_1", "TC_2"));
        Assert.assertTrue(SuiteStore.findById(root, "missing").isEmpty());
    }

    @Test
    public void missingFileReturnsEmpty() throws Exception {
        Path root = Files.createTempDirectory("suite-missing-");
        Assert.assertTrue(SuiteStore.load(root).isEmpty());
        Assert.assertTrue(SuiteStore.resolveTcIds(root, "x", "smoke").isEmpty());
    }
}
