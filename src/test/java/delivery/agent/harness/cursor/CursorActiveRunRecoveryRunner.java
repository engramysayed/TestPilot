package delivery.agent.harness.cursor;

import delivery.agent.harness.AgentHarnessCoordinator;
import delivery.agent.harness.AgentRunResult;
import delivery.agent.harness.AgentTaskStatus;
import delivery.portal.AgentHarnessProperties;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Separate JVM entrypoint for active-run recovery live test.
 * Args: projectRoot parentJobId taskId agentScriptPath resultOutPath
 */
public final class CursorActiveRunRecoveryRunner {
    private CursorActiveRunRecoveryRunner() {
    }

    public static void main(String[] args) throws Exception {
        Path project = Path.of(args[0]);
        String parentJobId = args[1];
        String taskId = args[2];
        Path agentScript = Path.of(args[3]);
        Path out = Path.of(args[4]);

        System.setProperty("delivery.provider.allowlist", "cursor");

        AgentHarnessProperties props = new AgentHarnessProperties();
        props.setEnabled(true);
        props.setCursorEnabled(true);
        props.setCursorCommand("node " + agentScript.toAbsolutePath().normalize());
        props.setMaxRuntimeMinutes(10);

        AgentHarnessCoordinator coordinator = AgentHarnessCoordinator.forTests(props);
        try {
            AgentRunResult result = coordinator.execute(project, parentJobId, taskId);
            JSONObject body = new JSONObject();
            body.put("terminalStatus", result.terminalStatus().name());
            body.put("summaryLen", result.summary() == null ? 0 : result.summary().length());
            Files.writeString(out, body.toString(2), StandardCharsets.UTF_8);
            if (result.terminalStatus() != AgentTaskStatus.COMPLETED
                    && result.terminalStatus() != AgentTaskStatus.CANCELLED) {
                System.exit(2);
            }
        } catch (Exception e) {
            JSONObject body = new JSONObject();
            body.put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
            Files.writeString(out, body.toString(2), StandardCharsets.UTF_8);
            throw e;
        }
    }
}
