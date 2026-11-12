package delivery.agent.harness;

import java.nio.file.Path;

/** Well-known paths under a task directory. */
public final class AgentWorkspacePaths {
    public static final String WORKSPACE_DIR = "workspace";
    public static final String SDK_STATE_DIR = "cursor-sdk-state";
    public static final String CONTROL_DIR = "control";
    public static final String CANCEL_REQUESTED = "cancel.requested";

    private AgentWorkspacePaths() {
    }

    public static Path isolatedWorkspace(Path taskDir) {
        return taskDir.resolve(WORKSPACE_DIR);
    }

    public static Path sdkStateDir(Path taskDir) {
        return taskDir.resolve(SDK_STATE_DIR);
    }

    public static Path cancelRequestedFile(Path taskDir) {
        return taskDir.resolve(CONTROL_DIR).resolve(CANCEL_REQUESTED);
    }
}
