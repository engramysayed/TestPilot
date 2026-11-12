package delivery.agent.harness;

import java.nio.file.Files;
import java.nio.file.Path;

/** Pre-flight reconcile before a new {@code Agent.create}. */
public final class AgentHarnessReconcile {
    private final SdkTouchMarker marker;

    public AgentHarnessReconcile() {
        this(new SdkTouchMarker());
    }

    public AgentHarnessReconcile(SdkTouchMarker marker) {
        this.marker = marker == null ? new SdkTouchMarker() : marker;
    }

  /**
   * @return {@code true} if a new agent create is allowed for this attempt
   */
    public boolean mayCreateNewAgent(Path taskDir, AgentTask task) {
        if (task == null) {
            return false;
        }
        if (task.agentId() != null && !task.agentId().isBlank()
                && task.agentRunId() != null && !task.agentRunId().isBlank()) {
            return false;
        }
        if (task.taskStatus() == AgentTaskStatus.RUNNING || task.taskStatus() == AgentTaskStatus.STARTING) {
            return false;
        }
        SdkTouchMarker.State state = marker.readState(taskDir);
        if (state == SdkTouchMarker.State.DEFINITELY_SAFE_TO_CREATE) {
            return true;
        }
        // First launch: no sidecar marker yet — safe to start execute (not a crashed LAUNCHING retry).
        if (state == SdkTouchMarker.State.AMBIGUOUS
                && !Files.isRegularFile(SdkTouchMarker.markerPath(taskDir))
                && task.taskStatus() == AgentTaskStatus.PENDING) {
            return true;
        }
        return false;
    }

    public void assertMayCreateOrThrow(Path taskDir, AgentTask task) {
        if (!mayCreateNewAgent(taskDir, task)) {
            SdkTouchMarker.State state = marker.readState(taskDir);
            if (state == SdkTouchMarker.State.SDK_TOUCHED) {
                throw new AgentHarnessException("SDK_TOUCHED: cannot create another agent");
            }
            throw new AgentHarnessException("RECONCILE_AMBIGUOUS: task marked INTERRUPTED");
        }
    }

    public boolean shouldMarkInterrupted(Path taskDir, AgentTask task) {
        SdkTouchMarker.State state = marker.readState(taskDir);
        if (state == SdkTouchMarker.State.SDK_TOUCHED && (task.agentId() == null || task.agentId().isBlank())) {
            return true;
        }
        boolean markerPresent = Files.isRegularFile(SdkTouchMarker.markerPath(taskDir));
        if (!markerPresent) {
            // Missing marker on a never-started task is not an interruption.
            if (task.taskStatus() == AgentTaskStatus.PENDING) {
                return false;
            }
            return task.taskStatus() == AgentTaskStatus.STARTING
                    && (task.agentId() == null || task.agentId().isBlank());
        }
        return state == SdkTouchMarker.State.AMBIGUOUS
                && (task.taskStatus() == AgentTaskStatus.PENDING || task.taskStatus() == AgentTaskStatus.STARTING)
                && (task.agentId() == null || task.agentId().isBlank());
    }
}
