package delivery.agent.harness.convert;

import delivery.agent.harness.AgentArtifactFingerprints;
import delivery.agent.harness.AgentHarnessCoordinator;
import delivery.agent.harness.AgentHarnessException;
import delivery.agent.harness.AgentRunHandle;
import delivery.agent.harness.AgentRunResult;
import delivery.agent.harness.AgentRunStore;
import delivery.agent.harness.AgentTask;
import delivery.agent.harness.AgentTaskStatus;
import delivery.agent.harness.AgentHarnessProperties;
import delivery.store.ProjectStore;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class AgentHarnessPhase3Test {

    @Test
    public void deterministicTaskId_stableAcrossJvm() {
        String a = AgentArtifactFingerprints.deterministicTaskId("job1", "CONVERT_PRE_PUBLISH", "abc");
        String b = AgentArtifactFingerprints.deterministicTaskId("job1", "CONVERT_PRE_PUBLISH", "abc");
        Assert.assertEquals(a, b);
        Assert.assertTrue(a.startsWith("fp_"));
    }

    @Test
    public void advisoryFailure_stillReturnsFingerprint() throws Exception {
        Path project = Files.createTempDirectory("p3-adv");
        Path zip = writeZip(project, "ok.txt", "hello");
        AgentHarnessProperties props = new AgentHarnessProperties();
        props.setEnabled(true);
        props.setFrameworkValidationEnabled(true);
        AgentHarnessCoordinator coordinator = AgentHarnessCoordinator.forTests(props);
        AgentHarnessJobBridge bridge = new AgentHarnessJobBridge(
                props, coordinator, new AgentRunStore(), new AgentStageIndexStore());
        String fp = bridge.validateBeforePublish(
                project, "proj", "job_adv", zip, Duration.ofMinutes(1), () -> false);
        Assert.assertFalse(fp.isBlank());
        Path fw = Files.createTempDirectory("fw-adv");
        Files.writeString(fw.resolve("README.md"), "x");
        ProjectStore store = new ProjectStore(project);
        var saved = store.saveVersion(
                "proj",
                fw,
                zip,
                null,
                new delivery.job.PublicationGuard() {
                    public java.util.Optional<String> expectedZipFingerprint() {
                        return java.util.Optional.of(fp);
                    }

                    public <T> T publish(java.util.concurrent.Callable<T> action) throws Exception {
                        return action.call();
                    }
                });
        Assert.assertEquals(saved.version(), 1);
    }

    @Test
    public void stageIndex_deferredLatestWins() throws Exception {
        Path project = Files.createTempDirectory("p3-stage");
        AgentHarnessProperties props = new AgentHarnessProperties();
        props.setEnabled(true);
        props.setFrameworkValidationEnabled(true);
        AgentRunStore runStore = new AgentRunStore();
        AgentStageIndexStore stageIndex = new AgentStageIndexStore();
        AgentHarnessCoordinator coordinator = AgentHarnessCoordinator.forTests(props);
        AgentHarnessJobBridge bridge = new AgentHarnessJobBridge(props, coordinator, runStore, stageIndex);

        Path zipA = writeZipWithContent(project, "a.zip", "A");
        Path zipB = writeZipWithContent(project, "b.zip", "B");
        Path zipC = writeZipWithContent(project, "c.zip", "C");
        String fpA = AgentArtifactFingerprints.sha256Hex(zipA);
        String fpB = AgentArtifactFingerprints.sha256Hex(zipB);
        String fpC = AgentArtifactFingerprints.sha256Hex(zipC);

        String taskA = AgentArtifactFingerprints.deterministicTaskId(
                "job_stage", AgentStageIndexStore.STAGE_CONVERT_PRE_PUBLISH, fpA);
        AgentTask active = new AgentTask(
                taskA, "job_stage", "p", delivery.agent.harness.AgentTaskType.FRAMEWORK_VALIDATION_V1,
                AgentTaskStatus.RUNNING, "", java.util.List.of(), "",
                Instant.now(), Instant.now(), null, "aid", "rid", 1,
                delivery.agent.harness.AgentPolicy.disabled(), "", delivery.agent.harness.AgentProviderId.FAKE,
                fpA, AgentStageIndexStore.STAGE_CONVERT_PRE_PUBLISH, 3);
        runStore.saveTask(project, active);
        AgentStageIndexStore.IndexFile index = new AgentStageIndexStore.IndexFile();
        index.stages.put(AgentStageIndexStore.STAGE_CONVERT_PRE_PUBLISH, new AgentStageIndexStore.StageEntry());
        index.stages.get(AgentStageIndexStore.STAGE_CONVERT_PRE_PUBLISH).active =
                new AgentStageIndexStore.TaskRef(fpA, taskA);
        stageIndex.write(project, "job_stage", index);

        bridge.validateBeforePublish(project, "p", "job_stage", zipB, Duration.ofMinutes(1), () -> false);
        bridge.validateBeforePublish(project, "p", "job_stage", zipC, Duration.ofMinutes(1), () -> false);

        AgentStageIndexStore.IndexFile after = stageIndex.read(project, "job_stage");
        String deferredFp = after.stages.get(AgentStageIndexStore.STAGE_CONVERT_PRE_PUBLISH).deferred.inputFingerprint();
        Assert.assertEquals(deferredFp, fpC);
    }

    @Test
    public void interruptedTask_reconcilesWithoutNewIds() throws Exception {
        Path project = Files.createTempDirectory("p3-int");
        Path workspace = Files.createTempDirectory("ws-int");
        AgentHarnessProperties props = new AgentHarnessProperties();
        props.setEnabled(true);
        AgentHarnessCoordinator coordinator = AgentHarnessCoordinator.forTests(props);
        AgentRunStore store = new AgentRunStore();
        String taskId = "fp_reconcile_test";
        AgentTask interrupted = new AgentTask(
                taskId, "job_r", "p", delivery.agent.harness.AgentTaskType.FRAMEWORK_VALIDATION_V1,
                AgentTaskStatus.INTERRUPTED, "", java.util.List.of(), workspace.toString(),
                Instant.now(), Instant.now(), null, "fake-agent-" + taskId, "run-1", 1,
                delivery.agent.harness.AgentPolicy.disabled(), "", delivery.agent.harness.AgentProviderId.FAKE,
                "fp", AgentStageIndexStore.STAGE_CONVERT_PRE_PUBLISH, 3);
        store.saveTask(project, interrupted);
        AgentRunResult result = coordinator.execute(project, "job_r", taskId);
        Assert.assertEquals(result.terminalStatus(), AgentTaskStatus.COMPLETED);
        AgentTask done = store.loadTask(project, "job_r", taskId).orElseThrow();
        Assert.assertEquals(done.agentId(), "fake-agent-" + taskId);
        Assert.assertEquals(done.agentRunId(), "run-1");
    }

    private static Path writeZip(Path dir, String entry, String text) throws Exception {
        Path zip = dir.resolve("package.zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zip))) {
            zos.putNextEntry(new ZipEntry(entry));
            zos.write(text.getBytes());
            zos.closeEntry();
        }
        return zip;
    }

    private static Path writeZipWithContent(Path dir, String name, String text) throws Exception {
        Path zip = dir.resolve(name);
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zip))) {
            zos.putNextEntry(new ZipEntry("src/x.txt"));
            zos.write(text.getBytes());
            zos.closeEntry();
        }
        return zip;
    }
}
