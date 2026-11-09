package delivery.portal.tunnel;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public class TunnelEnrollmentDiskStoreTest {

    @Test
    public void reEnrollRemovesPreviousTokenIndex() throws Exception {
        Path store = Files.createTempDirectory("tunnel-disk");
        Path project = store.resolve("p1");
        TunnelEnrollmentDiskStore disk = new TunnelEnrollmentDiskStore();
        disk.save(project, store, "old", "p1", "u", "s1", "pending");
        Assert.assertTrue(TunnelTokenIndex.projectIdForToken(store, "old").isPresent());
        disk.save(project, store, "new", "p1", "u", "s2", "pending");
        Assert.assertTrue(TunnelTokenIndex.projectIdForToken(store, "new").isPresent());
        Assert.assertFalse(TunnelTokenIndex.projectIdForToken(store, "old").isPresent(),
                "stale index for superseded token must be removed");
    }

    @Test
    public void ensureIndexRecreatesMissingIndex() throws Exception {
        Path store = Files.createTempDirectory("tunnel-disk-ix");
        TunnelEnrollmentDiskStore disk = new TunnelEnrollmentDiskStore();
        disk.ensureIndex(store, "tok", "p9");
        Assert.assertEquals(TunnelTokenIndex.projectIdForToken(store, "tok").orElse(""), "p9");
    }

    @Test
    public void liveOnlyWhileHeartbeatFresh() {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("status", "active");
        doc.put("lastHeartbeatAt", Instant.now().toString());
        Assert.assertTrue(TunnelEnrollmentDiskStore.isLive(doc, Duration.ofSeconds(60)));
        Assert.assertEquals(TunnelEnrollmentDiskStore.effectiveStatus(doc, Duration.ofSeconds(60)), "active");

        doc.put("lastHeartbeatAt", Instant.now().minusSeconds(300).toString());
        Assert.assertFalse(TunnelEnrollmentDiskStore.isLive(doc, Duration.ofSeconds(60)));
        Assert.assertEquals(TunnelEnrollmentDiskStore.effectiveStatus(doc, Duration.ofSeconds(60)), "stale");

        doc.remove("lastHeartbeatAt");
        Assert.assertFalse(TunnelEnrollmentDiskStore.isLive(doc, Duration.ofSeconds(60)));

        doc.put("status", "pending");
        doc.put("lastHeartbeatAt", Instant.now().toString());
        Assert.assertFalse(TunnelEnrollmentDiskStore.isLive(doc, Duration.ofSeconds(60)));
    }
}
