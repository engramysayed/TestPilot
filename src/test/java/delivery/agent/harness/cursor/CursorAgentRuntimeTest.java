package delivery.agent.harness.cursor;

import delivery.agent.harness.AgentHarnessCoordinator;
import delivery.agent.harness.AgentProviderId;
import delivery.agent.harness.AgentRunResult;
import delivery.agent.harness.AgentTaskStatus;
import delivery.agent.harness.NewAgentTaskRequest;
import delivery.agent.harness.SdkTouchMarker;
import delivery.agent.harness.AgentHarnessProperties;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class CursorAgentRuntimeTest {

    @Test
    public void fakeSidecar_executeWithCursorProvider() throws Exception {
        Path project = Files.createTempDirectory("cursor-harness-");
        Path sourceWs = Files.createTempDirectory("source-ws-");
        Files.writeString(sourceWs.resolve("readme.txt"), "hello");

        Path fakeScript = Path.of("src/test/resources/cursor-agent/fake-sidecar.mjs").toAbsolutePath();
        Assert.assertTrue(Files.isRegularFile(fakeScript));

        AgentHarnessProperties props = new AgentHarnessProperties();
        props.setEnabled(true);
        props.setCursorEnabled(true);
        props.setCursorCommand("node " + fakeScript);

        String prevAllow = System.getProperty("delivery.provider.allowlist");
        String prevKey = System.getProperty("CURSOR_API_KEY");
        System.setProperty("delivery.provider.allowlist", "cursor");
        System.setProperty("CURSOR_API_KEY", "test-key-not-real");
        try {
            AgentHarnessCoordinator coordinator = AgentHarnessCoordinator.forTests(props);
            var task = coordinator.createTask(project, NewAgentTaskRequest.simple(
                    "job_c1", "p1", delivery.agent.harness.AgentTaskType.FRAMEWORK_VALIDATION_V1,
                    "validate", List.of(), sourceWs.toString()));
            Assert.assertEquals(task.providerId(), AgentProviderId.CURSOR);

            AgentRunResult result = coordinator.execute(project, task.parentJobId(), task.taskId());
            Assert.assertEquals(result.terminalStatus(), AgentTaskStatus.COMPLETED);

            Path taskDir = new delivery.agent.harness.AgentRunStore()
                    .taskDir(project, task.parentJobId(), task.taskId());
            Assert.assertEquals(new SdkTouchMarker().readState(taskDir), SdkTouchMarker.State.SDK_TOUCHED);
            Assert.assertTrue(Files.isDirectory(taskDir.resolve("workspace")));
            Assert.assertEquals(Files.readString(taskDir.resolve("workspace/readme.txt")), "hello");
        } finally {
            restore("delivery.provider.allowlist", prevAllow);
            restore("CURSOR_API_KEY", prevKey);
        }
    }

    @Test
    public void coordinatorRecoverRunningTask_usesReconcileNotCreate() throws Exception {
        Path project = Files.createTempDirectory("cursor-recover-");
        Path sourceWs = Files.createTempDirectory("source-ws-recover-");
        Files.writeString(sourceWs.resolve("readme.txt"), "hello");

        Path fakeScript = Path.of("src/test/resources/cursor-agent/fake-sidecar.mjs").toAbsolutePath();
        AgentHarnessProperties props = new AgentHarnessProperties();
        props.setEnabled(true);
        props.setCursorEnabled(true);
        props.setCursorCommand("node " + fakeScript);

        String prevAllow = System.getProperty("delivery.provider.allowlist");
        String prevKey = System.getProperty("CURSOR_API_KEY");
        System.setProperty("delivery.provider.allowlist", "cursor");
        System.setProperty("CURSOR_API_KEY", "test-key-not-real");
        try {
            AgentHarnessCoordinator coordinator = AgentHarnessCoordinator.forTests(props);
            var task = coordinator.createTask(project, NewAgentTaskRequest.simple(
                    "job_r1", "p1", delivery.agent.harness.AgentTaskType.FRAMEWORK_VALIDATION_V1,
                    "validate", List.of(), sourceWs.toString()));

            var store = new delivery.agent.harness.AgentRunStore();
            Path taskDir = store.taskDir(project, task.parentJobId(), task.taskId());
            Path iso = new delivery.agent.harness.AgentWorkspacePreparer()
                    .ensureIsolatedCopy(taskDir, sourceWs);
            var running = store.loadTask(project, task.parentJobId(), task.taskId()).orElseThrow();
            String isoPath = iso.toAbsolutePath().normalize().toString();
            var policy = new delivery.agent.harness.AgentPolicy(
                    running.budget().maxRuntime(),
                    running.budget().maxAttempts(),
                    running.budget().allowedTools(),
                    List.of(isoPath),
                    running.budget().networkAllowed(),
                    running.budget().requireHumanApproval());
            running = running
                    .withWorkspacePath(isoPath)
                    .withPolicy(policy)
                    .withStatus(AgentTaskStatus.RUNNING)
                    .withRun(new delivery.agent.harness.AgentRunHandle("fake-agent-r1", "fake-run-r1", java.time.Instant.now()));
            Files.writeString(taskDir.resolve("sdk-touch.marker"),
                    "{\"sdkTouched\":true,\"phase\":\"create_returned\",\"agentId\":\"fake-agent-r1\"}");
            store.saveTask(project, running);

            AgentRunResult result = coordinator.execute(project, task.parentJobId(), task.taskId());
            Assert.assertEquals(result.terminalStatus(), AgentTaskStatus.COMPLETED);
            Assert.assertTrue(result.summary().contains("fake reconcile"));
        } finally {
            restore("delivery.provider.allowlist", prevAllow);
            restore("CURSOR_API_KEY", prevKey);
        }
    }

    @Test
    public void reconcilePollInterrupt_withoutRunStarted_preservesIdsAndInterrupted() throws Exception {
        Path project = Files.createTempDirectory("cursor-policy-a-");
        Path sourceWs = Files.createTempDirectory("source-ws-policy-a-");
        Files.writeString(sourceWs.resolve("readme.txt"), "hello");

        Path fakeScript = Path.of("src/test/resources/cursor-agent/fake-sidecar.mjs").toAbsolutePath();
        AgentHarnessProperties props = new AgentHarnessProperties();
        props.setEnabled(true);
        props.setCursorEnabled(true);
        props.setCursorCommand("node " + fakeScript);

        String prevAllow = System.getProperty("delivery.provider.allowlist");
        String prevKey = System.getProperty("CURSOR_API_KEY");
        System.setProperty("delivery.provider.allowlist", "cursor");
        System.setProperty("CURSOR_API_KEY", "test-key-not-real");
        try {
            AgentHarnessCoordinator coordinator = AgentHarnessCoordinator.forTests(props);
            var created = coordinator.createTask(project, NewAgentTaskRequest.simple(
                    "job_pa", "p1", delivery.agent.harness.AgentTaskType.FRAMEWORK_VALIDATION_V1,
                    "__SIMULATE_RECONCILE_POLL_INTERRUPT__", List.of(), sourceWs.toString()));

            var store = new delivery.agent.harness.AgentRunStore();
            Path taskDir = store.taskDir(project, created.parentJobId(), created.taskId());
            Path iso = new delivery.agent.harness.AgentWorkspacePreparer()
                    .ensureIsolatedCopy(taskDir, sourceWs);
            String isoPath = iso.toAbsolutePath().normalize().toString();
            var running = store.loadTask(project, created.parentJobId(), created.taskId()).orElseThrow();
            var policy = new delivery.agent.harness.AgentPolicy(
                    running.budget().maxRuntime(),
                    running.budget().maxAttempts(),
                    running.budget().allowedTools(),
                    List.of(isoPath),
                    running.budget().networkAllowed(),
                    running.budget().requireHumanApproval());
            String agentId = "fake-agent-persist";
            String runId = "fake-run-persist";
            running = running
                    .withWorkspacePath(isoPath)
                    .withPolicy(policy)
                    .withStatus(AgentTaskStatus.RUNNING)
                    .withRun(new delivery.agent.harness.AgentRunHandle(agentId, runId, java.time.Instant.now()));
            Files.writeString(taskDir.resolve("sdk-touch.marker"),
                    "{\"sdkTouched\":true,\"phase\":\"create_returned\",\"agentId\":\"" + agentId + "\"}");
            store.saveTask(project, running);

            try {
                coordinator.execute(project, created.parentJobId(), created.taskId());
                Assert.fail("expected INTERRUPTED");
            } catch (delivery.agent.harness.AgentHarnessException e) {
                Assert.assertEquals(e.getMessage(), "INTERRUPTED");
            }

            var after = store.loadTask(project, created.parentJobId(), created.taskId()).orElseThrow();
            Assert.assertEquals(after.taskStatus(), AgentTaskStatus.INTERRUPTED);
            Assert.assertEquals(after.agentId(), agentId);
            Assert.assertEquals(after.agentRunId(), runId);
        } finally {
            restore("delivery.provider.allowlist", prevAllow);
            restore("CURSOR_API_KEY", prevKey);
        }
    }

    @Test(expectedExceptions = delivery.agent.harness.AgentHarnessException.class)
    public void reconcileIdMismatch_rejected() throws Exception {
        Path project = Files.createTempDirectory("cursor-id-mismatch-");
        Path sourceWs = Files.createTempDirectory("source-ws-mismatch-");
        Path fakeScript = Path.of("src/test/resources/cursor-agent/fake-sidecar.mjs").toAbsolutePath();
        AgentHarnessProperties props = new AgentHarnessProperties();
        props.setEnabled(true);
        props.setCursorEnabled(true);
        props.setCursorCommand("node " + fakeScript);

        System.setProperty("delivery.provider.allowlist", "cursor");
        System.setProperty("CURSOR_API_KEY", "test-key-not-real");
        try {
            AgentHarnessCoordinator coordinator = AgentHarnessCoordinator.forTests(props);
            var created = coordinator.createTask(project, NewAgentTaskRequest.simple(
                    "job_mm", "p1", delivery.agent.harness.AgentTaskType.FRAMEWORK_VALIDATION_V1,
                    "__SIMULATE_RECONCILE_ID_MISMATCH__", List.of(), sourceWs.toString()));
            var store = new delivery.agent.harness.AgentRunStore();
            Path taskDir = store.taskDir(project, created.parentJobId(), created.taskId());
            new delivery.agent.harness.AgentWorkspacePreparer().ensureIsolatedCopy(taskDir, sourceWs);
            var task = store.loadTask(project, created.parentJobId(), created.taskId()).orElseThrow();
            task = task.withStatus(AgentTaskStatus.RUNNING)
                    .withWorkspacePath(taskDir.resolve("workspace").toAbsolutePath().toString())
                    .withRun(new delivery.agent.harness.AgentRunHandle("expected-agent", "expected-run", java.time.Instant.now()));
            Files.writeString(taskDir.resolve("sdk-touch.marker"), "{\"sdkTouched\":true}");
            store.saveTask(project, task);
            coordinator.execute(project, created.parentJobId(), created.taskId());
        } finally {
            System.clearProperty("delivery.provider.allowlist");
            System.clearProperty("CURSOR_API_KEY");
        }
    }

    @Test(expectedExceptions = delivery.agent.harness.AgentHarnessException.class)
    public void executeWithoutRunStarted_rejected() throws Exception {
        Path project = Files.createTempDirectory("cursor-exec-nostart-");
        Path sourceWs = Files.createTempDirectory("source-ws-nostart-");
        Path fakeScript = Path.of("src/test/resources/cursor-agent/fake-sidecar.mjs").toAbsolutePath();
        AgentHarnessProperties props = new AgentHarnessProperties();
        props.setEnabled(true);
        props.setCursorEnabled(true);
        props.setCursorCommand("node " + fakeScript);

        System.setProperty("delivery.provider.allowlist", "cursor");
        System.setProperty("CURSOR_API_KEY", "test-key-not-real");
        try {
            AgentHarnessCoordinator coordinator = AgentHarnessCoordinator.forTests(props);
            var created = coordinator.createTask(project, NewAgentTaskRequest.simple(
                    "job_ns", "p1", delivery.agent.harness.AgentTaskType.FRAMEWORK_VALIDATION_V1,
                    "__EXECUTE_NO_RUN_STARTED__", List.of(), sourceWs.toString()));
            var store = new delivery.agent.harness.AgentRunStore();
            Path taskDir = store.taskDir(project, created.parentJobId(), created.taskId());
            new delivery.agent.harness.AgentWorkspacePreparer().ensureIsolatedCopy(taskDir, sourceWs);
            coordinator.execute(project, created.parentJobId(), created.taskId());
        } finally {
            System.clearProperty("delivery.provider.allowlist");
            System.clearProperty("CURSOR_API_KEY");
        }
    }

    @Test(expectedExceptions = delivery.agent.harness.AgentHarnessException.class)
    public void sdkTouchedMarker_blocksSecondCreate() throws Exception {
        Path project = Files.createTempDirectory("cursor-interrupted-");
        Path sourceWs = Files.createTempDirectory("source-ws2-");

        AgentHarnessProperties props = new AgentHarnessProperties();
        props.setEnabled(true);
        props.setCursorEnabled(true);
        props.setCursorCommand("node " + Path.of("src/test/resources/cursor-agent/fake-sidecar.mjs").toAbsolutePath());

        System.setProperty("delivery.provider.allowlist", "cursor");
        System.setProperty("CURSOR_API_KEY", "test-key-not-real");
        try {
            AgentHarnessCoordinator coordinator = AgentHarnessCoordinator.forTests(props);
            var task = coordinator.createTask(project, NewAgentTaskRequest.simple(
                    "job_i1", "p1", delivery.agent.harness.AgentTaskType.EXECUTION_FAILURE_INVESTIGATION_V1,
                    "x", List.of(), sourceWs.toString()));
            Path taskDir = new delivery.agent.harness.AgentRunStore()
                    .taskDir(project, task.parentJobId(), task.taskId());
            Files.createDirectories(taskDir);
            Files.writeString(taskDir.resolve("sdk-touch.marker"),
                    "{\"sdkTouched\":true,\"phase\":\"create_requested\"}");
            var reloaded = new delivery.agent.harness.AgentRunStore()
                    .loadTask(project, task.parentJobId(), task.taskId()).orElseThrow();
            reloaded = reloaded.withStatus(AgentTaskStatus.STARTING);
            new delivery.agent.harness.AgentRunStore().saveTask(project, reloaded);
            coordinator.execute(project, task.parentJobId(), task.taskId());
        } finally {
            System.clearProperty("delivery.provider.allowlist");
            System.clearProperty("CURSOR_API_KEY");
        }
    }

    private static void restore(String key, String prev) {
        if (prev == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, prev);
        }
    }
}
