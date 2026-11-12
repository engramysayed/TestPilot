package delivery.agent.harness;

import delivery.portal.AgentHarnessProperties;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Orchestrates harness lifecycle: persist task, build context, delegate to {@link AgentRuntime}, store results.
 * No Keel job worker wiring in Phase 1.
 */
public final class AgentHarnessCoordinator {
    private final AgentRunStore store;
    private final AgentTaskRouter router;
    private final AgentContextBuilder contextBuilder;
    private final AgentHarnessProperties properties;
    private final Map<AgentProviderId, AgentRuntime> runtimes;

    public AgentHarnessCoordinator(
            AgentRunStore store,
            AgentTaskRouter router,
            AgentContextBuilder contextBuilder,
            AgentHarnessProperties properties,
            Map<AgentProviderId, AgentRuntime> runtimes) {
        this.store = store == null ? new AgentRunStore() : store;
        this.router = router == null ? new AgentTaskRouter(properties) : router;
        this.contextBuilder = contextBuilder == null ? new AgentContextBuilder() : contextBuilder;
        this.properties = properties == null ? new AgentHarnessProperties() : properties;
        this.runtimes = runtimes == null ? new EnumMap<>(AgentProviderId.class) : new EnumMap<>(runtimes);
        if (!this.runtimes.containsKey(AgentProviderId.FAKE)) {
            this.runtimes.put(AgentProviderId.FAKE, new FakeAgentRuntime());
        }
    }

    public static AgentHarnessCoordinator forTests(AgentHarnessProperties properties) {
        Map<AgentProviderId, AgentRuntime> map = new EnumMap<>(AgentProviderId.class);
        map.put(AgentProviderId.FAKE, new FakeAgentRuntime());
        return new AgentHarnessCoordinator(
                new AgentRunStore(),
                new AgentTaskRouter(properties),
                new AgentContextBuilder(),
                properties,
                map);
    }

    public AgentTask createTask(Path projectRoot, NewAgentTaskRequest request) throws IOException {
        if (projectRoot == null || !Files.isDirectory(projectRoot)) {
            throw new IllegalArgumentException("projectRoot must be a directory");
        }
        Path workspace = request.workspacePath().isBlank()
                ? null
                : Path.of(request.workspacePath());
        AgentTaskPlan plan = router.plan(request.taskType(), workspace);
        if (workspace != null && !plan.policy().isWorkspaceAllowed(workspace)) {
            throw new AgentHarnessException("workspace outside allowed roots: " + workspace);
        }
        String taskId = UUID.randomUUID().toString().replace("-", "");
        AgentTask task = new AgentTask(
                taskId,
                request.parentJobId(),
                request.projectId(),
                request.taskType(),
                AgentTaskStatus.PENDING,
                request.objective(),
                request.contextReferences(),
                request.workspacePath(),
                Instant.now(),
                null,
                null,
                null,
                null,
                1,
                plan.policy(),
                "",
                plan.providerId());
        store.saveTask(projectRoot, task);
        store.appendEvent(projectRoot, task.parentJobId(), task.taskId(),
                new AgentEvent(Instant.now(), "TASK_CREATED", "Agent task persisted", Map.of(
                        "taskType", task.taskType().name(),
                        "provider", task.providerId().name())));
        return task;
    }

    public AgentRunResult execute(Path projectRoot, String parentJobId, String taskId) throws IOException {
        if (!properties.isEnabled()) {
            throw new AgentHarnessException("delivery.agent-harness.enabled=false");
        }
        AgentTask task = store.loadTask(projectRoot, parentJobId, taskId)
                .orElseThrow(() -> new AgentHarnessException("task not found: " + taskId));
        if (task.taskStatus() == AgentTaskStatus.COMPLETED) {
            return store.loadResult(projectRoot, parentJobId, taskId)
                    .orElseThrow(() -> new AgentHarnessException("completed task missing result.json"));
        }
        if (task.taskStatus() == AgentTaskStatus.CANCELLED) {
            throw new AgentHarnessException("task already cancelled");
        }
        enforceConcurrency(projectRoot, parentJobId, taskId);

        AgentRuntime runtime = runtimes.get(task.providerId());
        if (runtime == null) {
            throw new AgentHarnessException("no runtime for provider " + task.providerId());
        }

        Path workspace = task.workspacePath().isBlank() ? null : Path.of(task.workspacePath());
        if (workspace != null && !task.budget().isWorkspaceAllowed(workspace)) {
            throw new AgentHarnessException("workspace forbidden by policy");
        }

        AgentContextManifest manifest = contextBuilder.build(task, List.of(), Map.of());
        store.saveContextManifest(projectRoot, parentJobId, taskId, manifest);

        AgentRunHandle handle;
        if (task.taskStatus() == AgentTaskStatus.RUNNING
                && task.agentId() != null
                && task.agentRunId() != null) {
            handle = runtime.resume(task.agentId(), task.agentRunId());
            store.appendEvent(projectRoot, parentJobId, taskId,
                    new AgentEvent(Instant.now(), "RESUME", "Resumed provider run", Map.of()));
        } else {
            task = task.withStatus(AgentTaskStatus.STARTING);
            store.saveTask(projectRoot, task);
            handle = runtime.start(task);
            task = task.withRun(handle).withStatus(AgentTaskStatus.RUNNING);
            store.saveTask(projectRoot, task);
            store.appendEvent(projectRoot, parentJobId, taskId,
                    new AgentEvent(Instant.now(), "START", "Provider run started", Map.of(
                            "agentId", handle.agentId(),
                            "runId", handle.runId())));
        }

        AgentRunResult result = runtime.getResult(handle.agentId(), handle.runId());
        AgentTaskStatus terminal = result.terminalStatus();
        if (terminal == AgentTaskStatus.RUNNING) {
            terminal = AgentTaskStatus.COMPLETED;
        }
        String resultRef = "result.json";
        task = task.withStatus(terminal).withCompleted(Instant.now(), resultRef);
        store.saveTask(projectRoot, task);
        store.saveResult(projectRoot, parentJobId, taskId, result);
        store.appendEvent(projectRoot, parentJobId, taskId,
                new AgentEvent(Instant.now(), "TERMINAL", terminal.name(), Map.of("summary", result.summary())));
        return result;
    }

    public void cancel(Path projectRoot, String parentJobId, String taskId) throws IOException {
        AgentTask task = store.loadTask(projectRoot, parentJobId, taskId)
                .orElseThrow(() -> new AgentHarnessException("task not found: " + taskId));
        if (task.agentId() != null && task.agentRunId() != null) {
            AgentRuntime runtime = runtimes.get(task.providerId());
            if (runtime != null) {
                runtime.cancel(task.agentId(), task.agentRunId());
            }
        }
        AgentTask cancelled = task.withStatus(AgentTaskStatus.CANCELLED).withCompleted(Instant.now(), "");
        store.saveTask(projectRoot, cancelled);
        store.appendEvent(projectRoot, parentJobId, taskId,
                new AgentEvent(Instant.now(), "CANCEL", "Task cancelled", Map.of()));
    }

    private void enforceConcurrency(Path projectRoot, String parentJobId, String currentTaskId) throws IOException {
        int max = properties.getMaxConcurrentRuns();
        Path runsRoot = projectRoot.resolve(AgentRunStore.ROOT_DIR).resolve(parentJobId);
        if (!Files.isDirectory(runsRoot)) {
            return;
        }
        int running = 0;
        try (var stream = Files.list(runsRoot)) {
            for (Path dir : stream.filter(Files::isDirectory).toList()) {
                String tid = dir.getFileName().toString();
                if (tid.equals(currentTaskId)) {
                    continue;
                }
                AgentTaskStatus status = store.loadTask(projectRoot, parentJobId, tid)
                        .map(AgentTask::taskStatus)
                        .orElse(null);
                if (status == AgentTaskStatus.RUNNING || status == AgentTaskStatus.STARTING) {
                    running++;
                }
            }
        }
        if (running >= max) {
            throw new AgentHarnessException("max concurrent agent runs exceeded (" + max + ")");
        }
    }
}
