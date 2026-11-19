package delivery.portal.service;

import delivery.portal.model.JobRecord;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Round-trip tests for progress-log serialization + the terminal truncation rule that keeps DB
 * writes bounded for completed/failed/cancelled jobs.
 */
public class PortalStoreProgressLogTest {

    @Test
    public void emptyAndNull_returnNull() {
        Assert.assertNull(PortalStore.serializeProgressLog(null));
        Assert.assertNull(PortalStore.serializeProgressLog(List.of()));
    }

    @Test
    public void nonTerminal_keepsAllEntries() {
        List<JobRecord.ProgressEntry> big = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            big.add(new JobRecord.ProgressEntry(1000L + i, "step " + i));
        }
        String json = PortalStore.serializeProgressLog(big, false);
        List<JobRecord.ProgressEntry> back = PortalStore.deserializeProgressLog(json);
        Assert.assertEquals(back.size(), 40);
        Assert.assertEquals(back.get(0).message(), "step 0");
        Assert.assertEquals(back.get(39).message(), "step 39");
    }

    @Test
    public void terminal_truncatesToLast10ForDbSize() {
        List<JobRecord.ProgressEntry> big = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            big.add(new JobRecord.ProgressEntry(1000L + i, "step " + i));
        }
        String json = PortalStore.serializeProgressLog(big, true);
        List<JobRecord.ProgressEntry> back = PortalStore.deserializeProgressLog(json);
        Assert.assertEquals(back.size(), PortalStore.TERMINAL_PROGRESS_LOG_KEEP);
        // Keeps the TAIL, not the head — post-mortem reviewers care about the final steps.
        Assert.assertEquals(back.get(0).message(), "step 30");
        Assert.assertEquals(back.get(back.size() - 1).message(), "step 39");
    }

    @Test
    public void terminal_belowThreshold_keepsAll() {
        List<JobRecord.ProgressEntry> small = List.of(
                new JobRecord.ProgressEntry(1L, "started"),
                new JobRecord.ProgressEntry(2L, "done"));
        String json = PortalStore.serializeProgressLog(small, true);
        List<JobRecord.ProgressEntry> back = PortalStore.deserializeProgressLog(json);
        Assert.assertEquals(back.size(), 2);
        Assert.assertEquals(back.get(0).message(), "started");
    }

    @Test
    public void deserializeMalformedJson_returnsEmpty() {
        Assert.assertTrue(PortalStore.deserializeProgressLog("{not json").isEmpty());
        Assert.assertTrue(PortalStore.deserializeProgressLog("").isEmpty());
        Assert.assertTrue(PortalStore.deserializeProgressLog(null).isEmpty());
    }

    @Test
    public void unicodeAndNewlinesSurviveRoundtrip() {
        List<JobRecord.ProgressEntry> input = List.of(
                new JobRecord.ProgressEntry(42L, "Phase2 emit: писать tests ✓"),
                new JobRecord.ProgressEntry(43L, "line1 \"quoted\" and \\ backslash"));
        List<JobRecord.ProgressEntry> back =
                PortalStore.deserializeProgressLog(PortalStore.serializeProgressLog(input, false));
        Assert.assertEquals(back.get(0).message(), "Phase2 emit: писать tests ✓");
        Assert.assertEquals(back.get(1).message(), "line1 \"quoted\" and \\ backslash");
    }
}
