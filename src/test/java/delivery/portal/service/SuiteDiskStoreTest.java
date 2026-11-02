package delivery.portal.service;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public class SuiteDiskStoreTest {

    @Test
    public void firstRead_seedsBuiltinPacks() throws Exception {
        Path root = Files.createTempDirectory("suites-seed-");
        SuiteDiskStore disk = new SuiteDiskStore();
        List<Map<String, Object>> all = disk.list(root, null);
        Assert.assertEquals(all.size(), 3);
        Assert.assertTrue(Files.isRegularFile(root.resolve("suites.json")));
        Assert.assertTrue(all.stream().allMatch(s -> Boolean.TRUE.equals(s.get("builtIn"))));
        Assert.assertEquals(disk.list(root, "smoke").size(), 1);
        Assert.assertEquals(disk.list(root, "SMOKE").size(), 1);
    }

    @Test
    public void createUserPack_rejectsReservedTags() throws Exception {
        Path root = Files.createTempDirectory("suites-reserved-");
        SuiteDiskStore disk = new SuiteDiskStore();
        disk.list(root, null); // seed
        try {
            disk.create(root, "My Smoke", List.of("smoke"), List.of("TC_1"));
            Assert.fail("expected BadRequestException");
        } catch (SuiteDiskStore.BadRequestException expected) {
            Assert.assertTrue(expected.getMessage().toLowerCase().contains("reserved"));
        }
        Map<String, Object> created = disk.create(root, "Checkout pack", List.of("checkout"), List.of("TC_1", "TC_2"));
        Assert.assertEquals(created.get("builtIn"), false);
        Assert.assertEquals(((List<?>) created.get("tcIds")).size(), 2);
    }

    @Test
    public void builtIn_deleteConflict_andNameImmutable() throws Exception {
        Path root = Files.createTempDirectory("suites-builtin-");
        SuiteDiskStore disk = new SuiteDiskStore();
        Map<String, Object> smoke = disk.list(root, "smoke").get(0);
        String suiteId = String.valueOf(smoke.get("suiteId"));
        try {
            disk.delete(root, suiteId);
            Assert.fail("expected ConflictException");
        } catch (SuiteDiskStore.ConflictException expected) {
            Assert.assertTrue(expected.getMessage().toLowerCase().contains("built-in"));
        }
        try {
            disk.update(root, suiteId, "Renamed", null, null);
            Assert.fail("expected BadRequestException");
        } catch (SuiteDiskStore.BadRequestException expected) {
            Assert.assertTrue(expected.getMessage().toLowerCase().contains("immutable"));
        }
        Map<String, Object> updated = disk.update(root, suiteId, null, null, List.of("TC_9"));
        Assert.assertEquals(((List<?>) updated.get("tcIds")).get(0), "TC_9");
        Assert.assertEquals(updated.get("name"), smoke.get("name"));
    }

    @Test
    public void schedule_interval_and_due() throws Exception {
        Path root = Files.createTempDirectory("suites-sched-");
        SuiteDiskStore disk = new SuiteDiskStore();
        Map<String, Object> created = disk.create(root, "Nightly custom", List.of("ops"), List.of("TC_1"));
        String suiteId = String.valueOf(created.get("suiteId"));
        Map<String, Object> withSched = disk.putSchedule(root, suiteId, true, "interval", null, 5);
        @SuppressWarnings("unchecked")
        Map<String, Object> schedule = (Map<String, Object>) withSched.get("schedule");
        Assert.assertEquals(schedule.get("kind"), "interval");
        Assert.assertEquals(schedule.get("intervalMinutes"), 5);
        Assert.assertNotNull(schedule.get("nextRunAt"));

        // Force due by rewriting nextRunAt to the past via record fire then manual adjust
        disk.recordSuiteRunFire(root, suiteId, "exec_abc");
        Map<String, Object> suite = disk.get(root, suiteId).orElseThrow();
        @SuppressWarnings("unchecked")
        Map<String, Object> s2 = new java.util.LinkedHashMap<>((Map<String, Object>) suite.get("schedule"));
        s2.put("nextRunAt", Instant.now().minusSeconds(30).toString());
        // rewrite file
        List<Map<String, Object>> all = new java.util.ArrayList<>(disk.list(root, null));
        for (int i = 0; i < all.size(); i++) {
            if (suiteId.equals(String.valueOf(all.get(i).get("suiteId")))) {
                Map<String, Object> row = new java.util.LinkedHashMap<>(all.get(i));
                row.put("schedule", s2);
                all.set(i, row);
            }
        }
        // use putSchedule disabled then enabled won't help; write via create path — use clear+put with past next by mutate helper
        Assert.assertTrue(disk.isDue(Map.of(
                "schedule", s2
        ), Instant.now(), 60));
        Assert.assertFalse(disk.isDue(Map.of(
                "schedule", Map.of(
                        "enabled", true,
                        "kind", "interval",
                        "intervalMinutes", 5,
                        "nextRunAt", Instant.now().plusSeconds(3600).toString()
                )
        ), Instant.now(), 60));
    }

    @Test
    public void alert_and_monitor_roundTrip() throws Exception {
        Path root = Files.createTempDirectory("suites-alert-");
        SuiteDiskStore disk = new SuiteDiskStore();
        Map<String, Object> created = disk.create(root, "Alert pack", List.of("ops"), List.of());
        String suiteId = String.valueOf(created.get("suiteId"));
        Map<String, Object> bound = disk.bindAlert(root, suiteId, "slack", "warning");
        Assert.assertEquals(bound.get("alertChannelId"), "slack");
        Assert.assertEquals(bound.get("alertMinSeverity"), "warning");
        disk.clearAlert(root, suiteId);
        Assert.assertNull(disk.get(root, suiteId).orElseThrow().get("alertChannelId"));

        Map<String, Object> promoted = disk.promoteMonitor(root, suiteId, "staging", "exec_green");
        @SuppressWarnings("unchecked")
        Map<String, Object> monitor = (Map<String, Object>) promoted.get("monitor");
        Assert.assertEquals(monitor.get("target"), "staging");
        Assert.assertEquals(monitor.get("enabled"), true);
        Assert.assertEquals(disk.listMonitors(root).size(), 1);
        disk.demoteMonitor(root, suiteId);
        Assert.assertEquals(disk.listMonitors(root).size(), 0);
    }

    @Test
    public void cron_nextRunAt_parsesFiveField() {
        Map<String, Object> schedule = new java.util.LinkedHashMap<>();
        schedule.put("kind", "cron");
        schedule.put("cron", "0 9 * * *");
        schedule.put("enabled", true);
        Instant next = SuiteDiskStore.computeNextRunAt(schedule, Instant.parse("2026-11-02T06:00:00Z"));
        Assert.assertNotNull(next);
        Assert.assertTrue(next.isAfter(Instant.parse("2026-11-02T06:00:00Z")));
    }
}
