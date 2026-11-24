package delivery.mobile.run;

import delivery.agent.harness.AgentHarnessCoordinator;
import delivery.agent.harness.AgentHarnessException;
import delivery.agent.harness.AgentHarnessProperties;
import delivery.agent.harness.AgentTaskType;
import delivery.agent.harness.NewAgentTaskRequest;
import delivery.prompts.PromptCatalog;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Cursor passes for a phone. A failure here is a sentence on the run.
 * It does not block the zip or the phone result.
 */
@Service
public class MobileCursorPass {

    private final AgentHarnessProperties properties;

    public MobileCursorPass(AgentHarnessProperties properties) {
        this.properties = properties == null ? new AgentHarnessProperties() : properties;
    }

    public String reviewFramework(Path projectDir, String jobId, String projectId, String ruleNotes) {
        return run(projectDir, jobId, projectId, AgentTaskType.FRAMEWORK_VALIDATION_V1,
                "agent-validation/mobile-automate",
                Map.of("ruleNotes", ruleNotes == null ? "" : ruleNotes),
                "Cursor reviewed the Android screens.");
    }

    public String investigate(Path projectRoot, String runId, String projectId, String failureText) {
        return run(projectRoot, runId, projectId, AgentTaskType.EXECUTION_FAILURE_INVESTIGATION_V1,
                "agent-validation/mobile-execute",
                Map.of("failureText", failureText == null ? "" : failureText),
                "Cursor read the phone failure.");
    }

    public String triage(Path projectRoot, String jobId, String projectId, String huntMessage) {
        return run(projectRoot, jobId, projectId, AgentTaskType.BUG_INVESTIGATION_V1,
                "agent-validation/mobile-hunt",
                Map.of("huntMessage", huntMessage == null ? "" : huntMessage),
                "Cursor triaged the phone hunt.");
    }

    private String run(Path root, String jobId, String projectId, AgentTaskType type,
                       String promptId, Map<String, String> vars, String ok) {
        if (root == null || jobId == null || jobId.isBlank() || projectId == null || projectId.isBlank()) {
            return "";
        }
        if (!properties.isEnabled() || !properties.isCursorEnabled()) {
            return "Cursor pass skipped.";
        }
        try {
            String objective = PromptCatalog.format(promptId, vars);
            AgentHarnessCoordinator coordinator = AgentHarnessCoordinator.forProduction(properties);
            var created = coordinator.createTask(root, NewAgentTaskRequest.simple(
                    jobId, projectId, type, objective, List.of(), root.toString()));
            coordinator.execute(root, created.parentJobId(), created.taskId());
            return ok;
        } catch (Exception e) {
            return "Cursor pass did not finish. " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }
}
