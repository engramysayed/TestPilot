package delivery.agent.harness.convert;

import delivery.agent.harness.AgentArtifactFingerprints;
import delivery.agent.harness.AgentHarnessCoordinator;
import delivery.agent.harness.AgentRunStore;
import delivery.agent.harness.AgentTask;
import delivery.agent.harness.AgentTaskStatus;
import delivery.portal.AgentHarnessProperties;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class AgentStageIndexResilienceTest {

    @Test
    public void evictedB_staysAuditable_notPromoted() throws Exception {
        Path project = Files.createTempDirectory("idx-evict");
        AgentHarnessProperties props = new AgentHarnessProperties();
        props.setEnabled(true);
        props.setFrameworkValidationEnabled(true);
        AgentRunStore runStore = new AgentRunStore();
        AgentStageIndexStore stageIndex = new AgentStageIndexStore();
        AgentHarnessJobBridge bridge = new AgentHarnessJobBridge(
                props, AgentHarnessCoordinator.forTests(props), runStore, stageIndex);

        Path zipA = zip(project, "a", "A");
        Path zipB = zip(project, "b", "B");
        Path zipC = zip(project, "c", "C");
        String fpA = AgentArtifactFingerprints.sha256Hex(zipA);
        String fpB = AgentArtifactFingerprints.sha256Hex(zipB);
        String fpC = AgentArtifactFingerprints.sha256Hex(zipC);
        String taskA = id("job_ev", fpA);
        String taskB = id("job_ev", fpB);
        String taskC = id("job_ev", fpC);

        seedActive(project, runStore, stageIndex, "job_ev", fpA, taskA, AgentTaskStatus.RUNNING);

        bridge.validateBeforePublish(project, "p", "job_ev", zipB, java.time.Duration.ofMinutes(1), () -> false);
        bridge.validateBeforePublish(project, "p", "job_ev", zipC, java.time.Duration.ofMinutes(1), () -> false);

        AgentStageIndexStore.IndexFile index = stageIndex.read(project, "job_ev");
        Assert.assertEquals(index.stages.get(AgentStageIndexStore.STAGE_CONVERT_PRE_PUBLISH).active.taskId(), taskA);
        Assert.assertEquals(index.stages.get(AgentStageIndexStore.STAGE_CONVERT_PRE_PUBLISH).deferred.taskId(), taskC);
        Assert.assertEquals(
                id("job_ev", fpB), taskB);

        Path eventsB = runStore.taskDir(project, "job_ev", taskB).resolve("events.jsonl");
        Assert.assertTrue(Files.isRegularFile(eventsB), "evicted fingerprint B must remain auditable");
        Assert.assertTrue(Files.readString(eventsB).contains("VALIDATION_SKIPPED"));
    }

    @Test
    public void activeTerminal_promotesDeferredFingerprint() throws Exception {
        Path project = Files.createTempDirectory("idx-promo");
        AgentHarnessProperties props = new AgentHarnessProperties();
        props.setEnabled(true);
        props.setFrameworkValidationEnabled(true);
        AgentRunStore runStore = new AgentRunStore();
        AgentStageIndexStore stageIndex = new AgentStageIndexStore();
        AgentHarnessJobBridge bridge = new AgentHarnessJobBridge(
                props, AgentHarnessCoordinator.forTests(props), runStore, stageIndex);

        Path zipA = zip(project, "a2", "A");
        Path zipC = zip(project, "c2", "C");
        String fpA = AgentArtifactFingerprints.sha256Hex(zipA);
        String fpC = AgentArtifactFingerprints.sha256Hex(zipC);
        String taskA = id("job_pr", fpA);
        String taskC = id("job_pr", fpC);

        seedActive(project, runStore, stageIndex, "job_pr", fpA, taskA, AgentTaskStatus.COMPLETED);
        AgentStageIndexStore.IndexFile idx = stageIndex.read(project, "job_pr");
        idx.stages.get(AgentStageIndexStore.STAGE_CONVERT_PRE_PUBLISH).deferred =
                new AgentStageIndexStore.TaskRef(fpC, taskC);
        stageIndex.write(project, "job_pr", idx);

        bridge.validateBeforePublish(project, "p", "job_pr", zipC, java.time.Duration.ofMinutes(1), () -> false);

        AgentStageIndexStore.IndexFile after = stageIndex.read(project, "job_pr");
        Assert.assertEquals(after.stages.get(AgentStageIndexStore.STAGE_CONVERT_PRE_PUBLISH).active.inputFingerprint(), fpC);
        Assert.assertNull(after.stages.get(AgentStageIndexStore.STAGE_CONVERT_PRE_PUBLISH).deferred);
    }

    @Test
    public void restart_reconstructsFromDisk() throws Exception {
        Path project = Files.createTempDirectory("idx-disk");
        AgentStageIndexStore stageIndex = new AgentStageIndexStore();
        AgentStageIndexStore.IndexFile index = new AgentStageIndexStore.IndexFile();
        index.stages.put(AgentStageIndexStore.STAGE_CONVERT_PRE_PUBLISH, new AgentStageIndexStore.StageEntry());
        index.stages.get(AgentStageIndexStore.STAGE_CONVERT_PRE_PUBLISH).active =
                new AgentStageIndexStore.TaskRef("fp_disk", "fp_disk_task");
        stageIndex.write(project, "job_disk", index);

        AgentStageIndexStore reloaded = new AgentStageIndexStore();
        Assert.assertEquals(
                reloaded.read(project, "job_disk").stages.get(AgentStageIndexStore.STAGE_CONVERT_PRE_PUBLISH).active.taskId(),
                "fp_disk_task");
    }

    @Test
    public void concurrentUpdates_doNotCorruptIndex() throws Exception {
        Path project = Files.createTempDirectory("idx-conc");
        AgentStageIndexStore stageIndex = new AgentStageIndexStore();
        AtomicInteger counters = new AtomicInteger();
        var pool = Executors.newFixedThreadPool(4);
        CountDownLatch start = new CountDownLatch(1);
        for (int i = 0; i < 8; i++) {
            final int n = i;
            pool.submit(() -> {
                try {
                    start.await(5, TimeUnit.SECONDS);
                    stageIndex.withLock(project, "job_conc", () -> {
                        AgentStageIndexStore.IndexFile index = stageIndex.read(project, "job_conc");
                        AgentStageIndexStore.StageEntry entry = index.stages.computeIfAbsent(
                                AgentStageIndexStore.STAGE_CONVERT_PRE_PUBLISH, k -> new AgentStageIndexStore.StageEntry());
                        entry.active = new AgentStageIndexStore.TaskRef("fp" + n, "fp_" + n);
                        stageIndex.write(project, "job_conc", index);
                        counters.incrementAndGet();
                        return null;
                    });
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }
        start.countDown();
        pool.shutdown();
        Assert.assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
        AgentStageIndexStore.IndexFile index = stageIndex.read(project, "job_conc");
        Assert.assertNotNull(index.stages.get(AgentStageIndexStore.STAGE_CONVERT_PRE_PUBLISH).active);
        Assert.assertTrue(counters.get() >= 8);
    }

    @Test
    public void sameFingerprint_noDuplicateTasks() throws Exception {
        Path project = Files.createTempDirectory("idx-dup");
        AgentHarnessProperties props = new AgentHarnessProperties();
        props.setEnabled(true);
        props.setFrameworkValidationEnabled(true);
        AgentRunStore runStore = new AgentRunStore();
        AgentHarnessJobBridge bridge = new AgentHarnessJobBridge(
                props, AgentHarnessCoordinator.forTests(props), runStore, new AgentStageIndexStore());
        Path zip = zip(project, "same", "S");
        bridge.validateBeforePublish(project, "p", "job_dup", zip, java.time.Duration.ofMinutes(1), () -> false);
        bridge.validateBeforePublish(project, "p", "job_dup", zip, java.time.Duration.ofMinutes(1), () -> false);
        String fp = AgentArtifactFingerprints.sha256Hex(zip);
        String taskId = id("job_dup", fp);
        Assert.assertTrue(runStore.loadTask(project, "job_dup", taskId).isPresent());
        List<Path> dirs = new ArrayList<>();
        try (var stream = Files.list(project.resolve("agent-runs/job_dup"))) {
            stream.filter(Files::isDirectory).forEach(dirs::add);
        }
        Assert.assertEquals(dirs.size(), 1, "duplicate fingerprint must not create extra task dirs");
    }

    private static void seedActive(
            Path project,
            AgentRunStore runStore,
            AgentStageIndexStore stageIndex,
            String parentJobId,
            String fp,
            String taskId,
            AgentTaskStatus status)
            throws Exception {
        AgentTask task = new AgentTask(
                taskId, parentJobId, "p", delivery.agent.harness.AgentTaskType.FRAMEWORK_VALIDATION_V1,
                status, "", java.util.List.of(), "",
                Instant.now(), Instant.now(), null, "aid", "rid", 1,
                delivery.agent.harness.AgentPolicy.disabled(), "", delivery.agent.harness.AgentProviderId.FAKE,
                fp, AgentStageIndexStore.STAGE_CONVERT_PRE_PUBLISH, 3);
        runStore.saveTask(project, task);
        AgentStageIndexStore.IndexFile index = new AgentStageIndexStore.IndexFile();
        index.stages.put(AgentStageIndexStore.STAGE_CONVERT_PRE_PUBLISH, new AgentStageIndexStore.StageEntry());
        index.stages.get(AgentStageIndexStore.STAGE_CONVERT_PRE_PUBLISH).active = new AgentStageIndexStore.TaskRef(fp, taskId);
        stageIndex.write(project, parentJobId, index);
    }

    private static String id(String parentJobId, String fp) {
        return AgentArtifactFingerprints.deterministicTaskId(
                parentJobId, AgentStageIndexStore.STAGE_CONVERT_PRE_PUBLISH, fp);
    }

    private static Path zip(Path project, String name, String content) throws Exception {
        Path z = project.resolve(name + ".zip");
        try (var zos = new java.util.zip.ZipOutputStream(Files.newOutputStream(z))) {
            zos.putNextEntry(new java.util.zip.ZipEntry("src/x.txt"));
            zos.write(content.getBytes());
            zos.closeEntry();
        }
        return z;
    }
}
