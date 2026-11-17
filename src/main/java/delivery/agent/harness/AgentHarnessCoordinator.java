package delivery.agent.harness;

import delivery.agent.harness.cursor.CursorAgentRuntime;
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
 */
public final class AgentHarnessCoordinator {
    private final AgentRunStore store;
    private final AgentTaskRouter router;
    private final AgentContextBuilder contextBuilder;
    private final AgentHarnessProperties properties;
    private final Map<AgentProviderId, AgentRuntime> runtimes;
    private final AgentWorkspacePreparer workspacePreparer;
    private final AgentHarnessReconcile reconcile;

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
        this.workspacePreparer = new AgentWorkspacePreparer();
        this.reconcile = new AgentHarnessReconcile();
        if (!this.runtimes.containsKey(AgentProviderId.FAKE)) {
            this.runtimes.put(AgentProviderId.FAKE, new FakeAgentRuntime());
        }
    }

    public static AgentHarnessCoordinator forProduction(AgentHarnessProperties properties) {
        return forTests(properties);
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
        String taskId = request.explicitTaskId() != null && !request.explicitTaskId().isBlank()
                ? request.explicitTaskId().trim()
                : UUID.randomUUID().toString().replace("-", "");
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
                plan.providerId(),
                request.inputFingerprint(),
                request.stageKey(),
                3);
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
        if (task.taskStatus() == AgentTaskStatus.INTERRUPTED) {
            boolean hasIds = task.agentId() != null && !task.agentId().isBlank()
                    && task.agentRunId() != null && !task.agentRunId().isBlank();
            if (!hasIds) {
                throw new AgentHarnessException("task interrupted — reconcile manually");
            }
        }
        if (task.taskStatus() == AgentTaskStatus.FAILED
                || task.taskStatus() == AgentTaskStatus.TIMED_OUT) {
            throw new AgentHarnessException("task terminal: " + task.taskStatus());
        }
        enforceConcurrency(projectRoot, parentJobId, taskId);
        // B6: process-wide fair-share — bounds the total concurrent agent runs across every parent
        // job on this host, independent of per-job maxConcurrentRuns. Prevents 4 concurrent
        // conversions × 2 agents each saturating the sidecar / CPU.
        boolean globalPermit = acquireGlobalSlot();
        try {

        Path taskDir = store.taskDir(projectRoot, parentJobId, taskId);
        if (task.providerId() == AgentProviderId.CURSOR) {
            task = prepareCursorWorkspace(task, taskDir);
            store.saveTask(projectRoot, task);
            if (reconcile.shouldMarkInterrupted(taskDir, task)) {
                markInterrupted(projectRoot, parentJobId, taskId, task);
            }
            if (task.taskStatus() != AgentTaskStatus.RUNNING
                    || task.agentId() == null
                    || task.agentRunId() == null) {
                reconcile.assertMayCreateOrThrow(taskDir, task);
            }
        }

        AgentRuntime runtime = resolveRuntime(task, projectRoot, taskDir);
        Path workspace = task.workspacePath().isBlank() ? null : Path.of(task.workspacePath());
        if (workspace != null && !task.budget().isWorkspaceAllowed(workspace)) {
            throw new AgentHarnessException("workspace forbidden by policy");
        }

        AgentContextManifest manifest = contextBuilder.build(task, List.of(), Map.of());
        store.saveContextManifest(projectRoot, parentJobId, taskId, manifest);

        store.saveLaunchState(projectRoot, parentJobId, taskId, new AgentLaunchState(
                "LAUNCHING", task.agentId(), task.agentRunId(), null, null,
                task.parentJobId() + ":" + taskId + ":a" + task.attempt(), task.attempt()));

        AgentRunHandle handle;
        boolean recovering =
                (task.taskStatus() == AgentTaskStatus.RUNNING
                        || task.taskStatus() == AgentTaskStatus.INTERRUPTED)
                        && task.agentId() != null
                        && !task.agentId().isBlank()
                        && task.agentRunId() != null
                        && !task.agentRunId().isBlank();
        if (recovering) {
            String eventType = task.taskStatus() == AgentTaskStatus.INTERRUPTED ? "RECONCILE_BEGIN" : "RECOVER";
            store.appendEvent(projectRoot, parentJobId, taskId,
                    new AgentEvent(Instant.now(), eventType, "Reconcile sidecar for existing run", Map.of(
                            "agentId", task.agentId(),
                            "runId", task.agentRunId())));
            if (task.taskStatus() == AgentTaskStatus.INTERRUPTED) {
                task = task.withStatus(AgentTaskStatus.RUNNING);
                store.saveTask(projectRoot, task);
            }
            handle = runtime.start(task);
        } else {
            task = task.withStatus(AgentTaskStatus.STARTING);
            store.saveTask(projectRoot, task);
            handle = runtime.start(task);
            task = task.withRun(handle).withStatus(AgentTaskStatus.RUNNING);
            store.saveTask(projectRoot, task);
            store.saveLaunchState(projectRoot, parentJobId, taskId, new AgentLaunchState(
                    "RUNNING", handle.agentId(), handle.runId(), null, null,
                    task.parentJobId() + ":" + taskId + ":a" + task.attempt(), task.attempt()));
            store.appendEvent(projectRoot, parentJobId, taskId,
                    new AgentEvent(Instant.now(), "START", "Provider run started", Map.of(
                            "agentId", handle.agentId(),
                            "runId", handle.runId())));
        }

        AgentRunResult result;
        try {
            result = runtime.getResult(handle.agentId(), handle.runId());
        } catch (AgentHarnessException e) {
            if ("SIDECAR_TIMEOUT".equals(e.getMessage()) || e.getMessage().startsWith("CANCEL")) {
                markTimedOutOrInterrupted(projectRoot, parentJobId, taskId, task, e);
            }
            throw e;
        }
        AgentTaskStatus terminal = result.terminalStatus();
        if (terminal == AgentTaskStatus.INTERRUPTED) {
            AgentTask interrupted = task.withStatus(AgentTaskStatus.INTERRUPTED);
            store.saveTask(projectRoot, interrupted);
            store.appendEvent(projectRoot, parentJobId, taskId,
                    new AgentEvent(Instant.now(), "TERMINAL", "INTERRUPTED", Map.of("summary", result.summary())));
            throw new AgentHarnessException("INTERRUPTED");
        }
        if (terminal == AgentTaskStatus.RUNNING) {
            terminal = AgentTaskStatus.COMPLETED;
        }
        String resultRef = "result.json";
        task = task.withStatus(terminal).withCompleted(Instant.now(), resultRef);
        store.saveTask(projectRoot, task);
        store.saveResult(projectRoot, parentJobId, taskId, result);
        store.saveLaunchState(projectRoot, parentJobId, taskId, new AgentLaunchState(
                terminal.name(), handle.agentId(), handle.runId(), null, null,
                task.parentJobId() + ":" + taskId + ":a" + task.attempt(), task.attempt()));
        store.clearCancelRequest(taskDir);
        store.appendEvent(projectRoot, parentJobId, taskId,
                new AgentEvent(Instant.now(), "TERMINAL", terminal.name(), Map.of("summary", result.summary())));
        return result;
        } finally {
            if (globalPermit) releaseGlobalSlot();
        }
    }

    // B6: process-wide fair-share across all agent runs on this host. Lazy init based on
    // AgentHarnessProperties.globalMaxConcurrentAgents so tests + per-run reconfig both work.
    private static volatile java.util.concurrent.Semaphore GLOBAL_PERMITS;
    private static volatile int GLOBAL_CAP;

    private boolean acquireGlobalSlot() {
        int cap = Math.max(1, properties.getGlobalMaxConcurrentAgents());
        java.util.concurrent.Semaphore sem = resolveGlobalSemaphore(cap);
        try {
            sem.acquire();
            return true;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void releaseGlobalSlot() {
        java.util.concurrent.Semaphore sem = GLOBAL_PERMITS;
        if (sem != null) sem.release();
    }

    private static synchronized java.util.concurrent.Semaphore resolveGlobalSemaphore(int cap) {
        if (GLOBAL_PERMITS == null || cap != GLOBAL_CAP) {
            GLOBAL_PERMITS = new java.util.concurrent.Semaphore(cap, true);
            GLOBAL_CAP = cap;
        }
        return GLOBAL_PERMITS;
    }

    public void cancel(Path projectRoot, String parentJobId, String taskId) throws IOException {
        AgentTask task = store.loadTask(projectRoot, parentJobId, taskId)
                .orElseThrow(() -> new AgentHarnessException("task not found: " + taskId));
        Path taskDir = store.taskDir(projectRoot, parentJobId, taskId);
        store.requestCancel(projectRoot, parentJobId, taskId);
        store.saveLaunchState(projectRoot, parentJobId, taskId, new AgentLaunchState(
                "CANCEL_REQUESTED", task.agentId(), task.agentRunId(), null, Instant.now(),
                task.parentJobId() + ":" + taskId + ":a" + task.attempt(), task.attempt()));

        boolean confirmed = false;
        if (task.providerId() == AgentProviderId.CURSOR
                && task.agentId() != null
                && task.agentRunId() != null) {
            try {
                AgentRuntime runtime = resolveRuntime(task, projectRoot, taskDir);
                runtime.cancel(task.agentId(), task.agentRunId());
                confirmed = true;
            } catch (AgentHarnessException e) {
                store.appendEvent(projectRoot, parentJobId, taskId,
                        new AgentEvent(Instant.now(), "CANCEL", "unconfirmed", Map.of("error", e.getMessage())));
            }
        } else if (task.agentId() != null && task.agentRunId() != null) {
            AgentRuntime runtime = runtimes.get(task.providerId());
            if (runtime != null) {
                runtime.cancel(task.agentId(), task.agentRunId());
                confirmed = true;
            }
        }

        AgentTaskStatus terminal = confirmed ? AgentTaskStatus.CANCELLED : AgentTaskStatus.INTERRUPTED;
        AgentTask done = task.withStatus(terminal).withCompleted(Instant.now(), "");
        store.saveTask(projectRoot, done);
        store.appendEvent(projectRoot, parentJobId, taskId,
                new AgentEvent(Instant.now(), "CANCEL", terminal.name(), Map.of()));
    }

    private AgentTask prepareCursorWorkspace(AgentTask task, Path taskDir) throws IOException {
        if (task.workspacePath() == null || task.workspacePath().isBlank()) {
            throw new AgentHarnessException("cursor task requires workspacePath");
        }
        Path source = Path.of(task.workspacePath());
        Path isolated = AgentWorkspacePaths.isolatedWorkspace(taskDir);
        if (source.toAbsolutePath().normalize().startsWith(isolated.toAbsolutePath().normalize())
                && Files.isDirectory(isolated)) {
            return task;
        }
        Path copied = workspacePreparer.ensureIsolatedCopy(taskDir, source);
        AgentPolicy policy = new AgentPolicy(
                task.budget().maxRuntime(),
                task.budget().maxAttempts(),
                task.budget().allowedTools(),
                List.of(copied.toAbsolutePath().normalize().toString()),
                task.budget().networkAllowed(),
                task.budget().requireHumanApproval());
        return task.withWorkspacePath(copied.toAbsolutePath().normalize().toString()).withPolicy(policy);
    }

    private AgentRuntime resolveRuntime(AgentTask task, Path projectRoot, Path taskDir) {
        if (task.providerId() == AgentProviderId.CURSOR) {
            AgentRuntime override = runtimes.get(AgentProviderId.CURSOR);
            if (override != null) {
                return override;
            }
            Path cancelFlag = AgentWorkspacePaths.cancelRequestedFile(taskDir);
            return new CursorAgentRuntime(properties, projectRoot, taskDir, () -> Files.exists(cancelFlag));
        }
        AgentRuntime rt = runtimes.get(task.providerId());
        if (rt == null) {
            throw new AgentHarnessException("no runtime for provider " + task.providerId());
        }
        return rt;
    }

    private void markInterrupted(Path projectRoot, String parentJobId, String taskId, AgentTask task)
            throws IOException {
        AgentTask interrupted = task.withStatus(AgentTaskStatus.INTERRUPTED).withCompleted(Instant.now(), "");
        store.saveTask(projectRoot, interrupted);
        store.appendEvent(projectRoot, parentJobId, taskId,
                new AgentEvent(Instant.now(), "TERMINAL", "INTERRUPTED", Map.of()));
        throw new AgentHarnessException("INTERRUPTED");
    }

    private void markTimedOutOrInterrupted(
            Path projectRoot, String parentJobId, String taskId, AgentTask task, AgentHarnessException e)
            throws IOException {
        AgentTaskStatus terminal = "SIDECAR_TIMEOUT".equals(e.getMessage())
                ? AgentTaskStatus.TIMED_OUT
                : AgentTaskStatus.INTERRUPTED;
        AgentTask done = task.withStatus(terminal).withCompleted(Instant.now(), "");
        store.saveTask(projectRoot, done);
        store.appendEvent(projectRoot, parentJobId, taskId,
                new AgentEvent(Instant.now(), "TERMINAL", terminal.name(), Map.of("reason", e.getMessage())));
        throw e;
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
