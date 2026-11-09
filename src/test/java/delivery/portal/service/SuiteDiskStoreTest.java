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
    public void tryClaimDue_advancesNextRunAt() throws Exception {
        Path root = Files.createTempDirectory("suites-claim-");
        SuiteDiskStore disk = new SuiteDiskStore();
        Map<String, Object> created = disk.create(root, "Sched pack", List.of("ops"), List.of("TC_1"));
        String suiteId = String.valueOf(created.get("suiteId"));
        disk.putSchedule(root, suiteId, true, "interval", null, 5);
        Map<String, Object> suite = disk.get(root, suiteId).orElseThrow();
        @SuppressWarnings("unchecked")
        Map<String, Object> schedule = new java.util.LinkedHashMap<>((Map<String, Object>) suite.get("schedule"));
        schedule.put("nextRunAt", Instant.now().minusSeconds(10).toString());
        suite.put("schedule", schedule);
        java.util.List<Map<String, Object>> patched = new java.util.ArrayList<>();
        for (Map<String, Object> row : disk.list(root, null)) {
            if (suiteId.equals(String.valueOf(row.get("suiteId")))) {
                patched.add(suite);
            } else {
                patched.add(row);
            }
        }
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        mapper.writerWithDefaultPrettyPrinter().writeValue(root.resolve(SuiteDiskStore.FILE_NAME).toFile(), patched);

        Assert.assertTrue(disk.tryClaimDue(root, suiteId, Instant.now(), 60).isPresent());
        Assert.assertTrue(disk.tryClaimDue(root, suiteId, Instant.now(), 60).isEmpty());
        Map<String, Object> after = disk.get(root, suiteId).orElseThrow();
        @SuppressWarnings("unchecked")
        Map<String, Object> schedAfter = (Map<String, Object>) after.get("schedule");
        Assert.assertNotNull(schedAfter.get("lastClaimedAt"));
        Instant next = Instant.parse(String.valueOf(schedAfter.get("nextRunAt")));
        Assert.assertTrue(next.isAfter(Instant.now()));
    }

    @Test
    public void normalizeTcIds_dedupesPreservesOrder() {
        List<String> out = SuiteDiskStore.normalizeTcIds(List.of("A", "A", "B"));
        Assert.assertEquals(out, List.of("A", "B"));
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

    @Test
    public void cron_moreOftenThanFiveMinutes_rejected() throws Exception {
        Path root = Files.createTempDirectory("suites-cron-gap-");
        SuiteDiskStore disk = new SuiteDiskStore();
        String suiteId = String.valueOf(disk.create(root, "Gap", List.of("ops"), List.of("TC_1")).get("suiteId"));
        Assert.assertThrows(SuiteDiskStore.BadRequestException.class,
                () -> disk.putSchedule(root, suiteId, true, "cron", "* * * * *", null));
        Assert.assertThrows(SuiteDiskStore.BadRequestException.class,
                () -> disk.putSchedule(root, suiteId, true, "cron", "*/2 * * * *", null));
        disk.putSchedule(root, suiteId, true, "cron", "*/5 * * * *", null); // allowed
    }

    @Test
    public void cron_usesScheduleTimezone() {
        Map<String, Object> cairo = new java.util.LinkedHashMap<>(Map.of("kind", "cron", "cron", "0 9 * * *", "enabled", true));
        Map<String, Object> utc = new java.util.LinkedHashMap<>(cairo);
        utc.put("timezone", "UTC");
        Instant from = Instant.parse("2026-11-02T00:00:00Z");
        Assert.assertEquals(SuiteDiskStore.computeNextRunAt(utc, from), Instant.parse("2026-11-02T09:00:00Z"));
        Assert.assertNotEquals(SuiteDiskStore.computeNextRunAt(cairo, from), SuiteDiskStore.computeNextRunAt(utc, from));
    }

    @Test
    public void putSchedule_rejectsUnknownTimezone_keepsProfile() throws Exception {
        Path root = Files.createTempDirectory("suites-tz-");
        SuiteDiskStore disk = new SuiteDiskStore();
        String suiteId = String.valueOf(disk.create(root, "Tz", List.of("ops"), List.of("TC_1")).get("suiteId"));
        Assert.assertThrows(SuiteDiskStore.BadRequestException.class,
                () -> disk.putSchedule(root, suiteId, true, "cron", "0 2 * * *", null, "Mars/Olympus", null));
        @SuppressWarnings("unchecked")
        Map<String, Object> sched = (Map<String, Object>) disk.putSchedule(
                root, suiteId, true, "cron", "0 2 * * *", null, "Asia/Riyadh", "qa-user").get("schedule");
        Assert.assertEquals(sched.get("timezone"), "Asia/Riyadh");
        Assert.assertEquals(sched.get("credentialProfile"), "qa-user");
        @SuppressWarnings("unchecked")
        Map<String, Object> again = (Map<String, Object>) disk.putSchedule(
                root, suiteId, true, "cron", "0 3 * * *", null, "Asia/Riyadh", null).get("schedule");
        Assert.assertEquals(again.get("credentialProfile"), "qa-user", "profile kept when not resent");
    }

    @Test
    public void missedSlotIsSkippedNotFired() throws Exception {
        Path root = Files.createTempDirectory("suites-misfire-");
        SuiteDiskStore disk = new SuiteDiskStore();
        String suiteId = String.valueOf(disk.create(root, "Nightly", List.of("ops"), List.of("TC_1")).get("suiteId"));
        disk.putSchedule(root, suiteId, true, "cron", "0 2 * * *", null);
        forceNextRunAt(disk, root, suiteId, Instant.now().minusSeconds(5 * 3600));
        Assert.assertTrue(disk.tryClaimDue(root, suiteId, Instant.now(), 60).isEmpty(), "5h-old slot must not fire");
        @SuppressWarnings("unchecked")
        Map<String, Object> sched = (Map<String, Object>) disk.get(root, suiteId).orElseThrow().get("schedule");
        @SuppressWarnings("unchecked")
        Map<String, Object> failure = (Map<String, Object>) sched.get("lastFailure");
        Assert.assertEquals(failure.get("code"), "MISSED");
        Assert.assertTrue(Instant.parse(String.valueOf(sched.get("nextRunAt"))).isAfter(Instant.now()));
    }

    @Test
    public void concurrentClaims_onlyOneWins() throws Exception {
        Path root = Files.createTempDirectory("suites-race-");
        SuiteDiskStore disk = new SuiteDiskStore();
        String suiteId = String.valueOf(disk.create(root, "Race", List.of("ops"), List.of("TC_1")).get("suiteId"));
        disk.putSchedule(root, suiteId, true, "interval", null, 30);
        forceNextRunAt(disk, root, suiteId, Instant.now().minusSeconds(5));
        java.util.concurrent.atomic.AtomicInteger wins = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(8);
        java.util.List<java.util.concurrent.Future<?>> fs = new java.util.ArrayList<>();
        for (int i = 0; i < 16; i++) {
            fs.add(pool.submit(() -> {
                try {
                    if (new SuiteDiskStore().tryClaimDue(root, suiteId, Instant.now(), 60).isPresent()) {
                        wins.incrementAndGet();
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }));
        }
        for (var f : fs) f.get();
        pool.shutdown();
        Assert.assertEquals(wins.get(), 1, "exactly one claimer may start the slot");
    }

    @Test
    public void fireClearsFailure_outcomeRecordsMonitor() throws Exception {
        Path root = Files.createTempDirectory("suites-outcome-");
        SuiteDiskStore disk = new SuiteDiskStore();
        String suiteId = String.valueOf(disk.create(root, "Mon", List.of("ops"), List.of("TC_1")).get("suiteId"));
        disk.putSchedule(root, suiteId, true, "interval", null, 30);
        disk.recordScheduleFailure(root, suiteId, "HTTP_402", "no credits");
        disk.recordSuiteRunFire(root, suiteId, "exec_1");
        @SuppressWarnings("unchecked")
        Map<String, Object> sched = (Map<String, Object>) disk.get(root, suiteId).orElseThrow().get("schedule");
        Assert.assertNull(sched.get("lastFailure"));
        disk.promoteMonitor(root, suiteId, "prod", "exec_0");
        disk.recordRunOutcome(root, suiteId, "exec_1", "COMPLETED", 9, 1, true);
        Map<String, Object> after = disk.get(root, suiteId).orElseThrow();
        @SuppressWarnings("unchecked")
        Map<String, Object> mon = (Map<String, Object>) after.get("monitor");
        Assert.assertEquals(mon.get("lastStatus"), "degraded");
        Assert.assertNotNull(mon.get("lastCheckedAt"));
        @SuppressWarnings("unchecked")
        Map<String, Object> s2 = (Map<String, Object>) after.get("schedule");
        Assert.assertEquals(s2.get("lastJobStatus"), "COMPLETED");
    }

    /** Test helper: rewrite nextRunAt directly in suites.json. */
    @SuppressWarnings("unchecked")
    private static void forceNextRunAt(SuiteDiskStore disk, Path root, String suiteId, Instant at) throws Exception {
        com.fasterxml.jackson.databind.ObjectMapper m = new com.fasterxml.jackson.databind.ObjectMapper();
        Path file = disk.file(root);
        List<Map<String, Object>> rows = m.readValue(file.toFile(), List.class);
        for (Map<String, Object> r : rows) {
            if (suiteId.equals(String.valueOf(r.get("suiteId")))) {
                ((Map<String, Object>) r.get("schedule")).put("nextRunAt", at.toString());
            }
        }
        m.writeValue(file.toFile(), rows);
    }
}
