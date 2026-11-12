package delivery.agent.harness;

import delivery.portal.AgentHarnessProperties;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class AgentHarnessCoordinatorTest {

    @Test
    public void fakeRuntime_completesTaskAndPersistsArtifacts() throws Exception {
        Path project = Files.createTempDirectory("agent-harness-");
        Path workspace = Files.createTempDirectory("ws-");

        AgentHarnessProperties props = new AgentHarnessProperties();
        props.setEnabled(true);
        props.setMaxConcurrentRuns(2);

        AgentHarnessCoordinator coordinator = AgentHarnessCoordinator.forTests(props);
        NewAgentTaskRequest req = new NewAgentTaskRequest(
                "job_convert_1",
                "proj1",
                AgentTaskType.FRAMEWORK_VALIDATION_V1,
                "Validate generated framework",
                List.of(new AgentContextReference("emit", "generated tree", "versions/latest")),
                workspace.toString());

        AgentTask created = coordinator.createTask(project, req);
        Assert.assertEquals(created.taskStatus(), AgentTaskStatus.PENDING);
        Assert.assertEquals(created.providerId(), AgentProviderId.FAKE);

        AgentRunResult result = coordinator.execute(project, created.parentJobId(), created.taskId());
        Assert.assertEquals(result.terminalStatus(), AgentTaskStatus.COMPLETED);
        Assert.assertEquals(result.payload().get("provider"), "FAKE");

        AgentRunStore store = new AgentRunStore();
        Path dir = store.taskDir(project, created.parentJobId(), created.taskId());
        Assert.assertTrue(Files.isRegularFile(dir.resolve("task.json")));
        Assert.assertTrue(Files.isRegularFile(dir.resolve("context-manifest.json")));
        Assert.assertTrue(Files.isRegularFile(dir.resolve("events.jsonl")));
        Assert.assertTrue(Files.isRegularFile(dir.resolve("result.json")));

        AgentTask loaded = store.loadTask(project, created.parentJobId(), created.taskId()).orElseThrow();
        Assert.assertEquals(loaded.taskStatus(), AgentTaskStatus.COMPLETED);

        AgentRunResult again = coordinator.execute(project, created.parentJobId(), created.taskId());
        Assert.assertEquals(again.summary(), result.summary());
    }

    @Test(expectedExceptions = AgentHarnessException.class)
    public void execute_whenHarnessDisabled_throws() throws Exception {
        Path project = Files.createTempDirectory("agent-harness-off-");
        AgentHarnessProperties props = new AgentHarnessProperties();
        props.setEnabled(false);
        AgentHarnessCoordinator coordinator = AgentHarnessCoordinator.forTests(props);
        AgentTask task = coordinator.createTask(project, new NewAgentTaskRequest(
                "job1", "p1", AgentTaskType.BUG_INVESTIGATION_V1, "x", List.of(), ""));
        coordinator.execute(project, task.parentJobId(), task.taskId());
    }

    @Test(expectedExceptions = AgentHarnessException.class)
    public void execute_rejectsWorkspaceOutsideStoredPolicy() throws Exception {
        Path project = Files.createTempDirectory("agent-harness-ws-");
        Path workspace = Files.createTempDirectory("allowed-ws");
        Path outsider = Files.createTempDirectory("outsider-ws");
        AgentHarnessProperties props = new AgentHarnessProperties();
        props.setEnabled(true);
        AgentHarnessCoordinator coordinator = AgentHarnessCoordinator.forTests(props);
        AgentTask task = coordinator.createTask(project, new NewAgentTaskRequest(
                "job1", "p1", AgentTaskType.EXECUTION_FAILURE_INVESTIGATION_V1, "investigate",
                List.of(), workspace.toString()));
        AgentRunStore store = new AgentRunStore();
        AgentTask tampered = new AgentTask(
                task.taskId(), task.parentJobId(), task.projectId(), task.taskType(), task.taskStatus(),
                task.objective(), task.contextReferences(), outsider.toString(), task.createdAt(), task.startedAt(),
                task.completedAt(), task.agentId(), task.agentRunId(), task.attempt(), task.budget(),
                task.resultReference(), task.providerId());
        store.saveTask(project, tampered);
        coordinator.execute(project, task.parentJobId(), task.taskId());
    }

    @Test
    public void resumeIdempotent_afterRunningState() throws Exception {
        Path project = Files.createTempDirectory("agent-harness-resume-");
        Path workspace = Files.createTempDirectory("ws-resume-");
        AgentHarnessProperties props = new AgentHarnessProperties();
        props.setEnabled(true);
        AgentHarnessCoordinator coordinator = AgentHarnessCoordinator.forTests(props);
        AgentTask task = coordinator.createTask(project, new NewAgentTaskRequest(
                "job_resume", "p1", AgentTaskType.GENERATED_TEST_REVIEW_V1, "review", List.of(), workspace.toString()));

        AgentRunStore store = new AgentRunStore();
        AgentTask running = task.withStatus(AgentTaskStatus.RUNNING)
                .withRun(new AgentRunHandle("fake-agent-" + task.taskId(), "run_existing", java.time.Instant.now()));
        store.saveTask(project, running);

        AgentRunResult result = coordinator.execute(project, task.parentJobId(), task.taskId());
        Assert.assertEquals(result.terminalStatus(), AgentTaskStatus.COMPLETED);
    }
}
