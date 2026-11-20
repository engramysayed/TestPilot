package delivery.agent.harness.cursor;

import delivery.agent.harness.AgentHarnessException;
import delivery.agent.harness.AgentProviderId;
import delivery.agent.harness.AgentRunHandle;
import delivery.agent.harness.AgentRunResult;
import delivery.agent.harness.AgentRuntime;
import delivery.agent.harness.AgentTask;
import delivery.agent.harness.AgentHarnessProperties;

import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/**
 * Cursor SDK provider via {@code tools/cursor-agent/agent.mjs}.
 */
public final class CursorAgentRuntime implements AgentRuntime {
    private final CursorSidecarClient client;
    private final Path projectRoot;
    private final Path taskDir;
    private final BooleanSupplier cancel;
    private CursorSidecarSession session;
    private AgentRunHandle handle;

    public CursorAgentRuntime(
            AgentHarnessProperties properties,
            Path projectRoot,
            Path taskDir,
            BooleanSupplier cancel) {
        this.client = new CursorSidecarClient(properties);
        this.projectRoot = projectRoot;
        this.taskDir = taskDir;
        this.cancel = cancel == null ? () -> false : cancel;
    }

    @Override
    public AgentProviderId providerId() {
        return AgentProviderId.CURSOR;
    }

    @Override
    public AgentRunHandle start(AgentTask task) {
        try {
            String op = task.agentId() != null && !task.agentId().isBlank() ? "reconcile" : "execute";
            session = client.execute(
                    task,
                    taskDir,
                    CursorSidecarClient.resolveRepoRoot(null),
                    op,
                    cancel);
            while (!session.isTerminal() && session.agentId() == null) {
                Thread.sleep(20);
            }
            String sidecarAgentId = session.agentId();
            String sidecarRunId = session.runId();
            boolean reconcile = isReconcileOp(task);
            if (sidecarAgentId != null && sidecarRunId != null) {
                if (reconcile) {
                    verifyReconcileIds(task, sidecarAgentId, sidecarRunId);
                }
                handle = new AgentRunHandle(sidecarAgentId, sidecarRunId, Instant.now());
                return handle;
            }
            if (reconcile && session.isTerminal()) {
                handle = new AgentRunHandle(task.agentId(), task.agentRunId(), Instant.now());
                return handle;
            }
            if (session.isTerminal()) {
                session.resultOrThrow();
            }
            throw new AgentHarnessException("sidecar did not emit run.started");
        } catch (AgentHarnessException e) {
            throw e;
        } catch (Exception e) {
            throw new AgentHarnessException("cursor start failed: " + e.getMessage(), e);
        }
    }

    @Override
    public AgentRunHandle resume(String agentId, String runId) {
        return new AgentRunHandle(agentId, runId, Instant.now());
    }

    @Override
    public void cancel(String agentId, String runId) {
        AtomicBoolean done = new AtomicBoolean();
        try (CursorSidecarSession s = client.execute(
                stubTask(agentId, runId),
                taskDir,
                CursorSidecarClient.resolveRepoRoot(null),
                "cancel",
                () -> done.get())) {
            s.resultOrThrow();
        } catch (Exception e) {
            throw new AgentHarnessException("cancel failed: " + e.getMessage(), e);
        }
    }

    @Override
    public AgentRunResult getResult(String agentId, String runId) {
        if (session != null) {
            return session.resultOrThrow();
        }
        throw new AgentHarnessException("no active sidecar session");
    }

    private static boolean isReconcileOp(AgentTask task) {
        return task.agentId() != null && !task.agentId().isBlank()
                && task.agentRunId() != null && !task.agentRunId().isBlank();
    }

    private static void verifyReconcileIds(AgentTask task, String agentId, String runId) {
        if (!task.agentId().equals(agentId) || !task.agentRunId().equals(runId)) {
            throw new AgentHarnessException(
                    "reconcile id mismatch: expected agentId="
                            + task.agentId()
                            + " runId="
                            + task.agentRunId()
                            + " got agentId="
                            + agentId
                            + " runId="
                            + runId);
        }
    }

    private static AgentTask stubTask(String agentId, String runId) {
        return new AgentTask(
                "cancel", "cancel", "cancel", delivery.agent.harness.AgentTaskType.BUG_INVESTIGATION_V1,
                delivery.agent.harness.AgentTaskStatus.RUNNING, "", java.util.List.of(), "",
                Instant.now(), null, null, agentId, runId, 1,
                delivery.agent.harness.AgentPolicy.disabled(), "", AgentProviderId.CURSOR, "", "", 3);
    }
}
