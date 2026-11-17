package delivery.agent.validation.hunt;

import delivery.agent.harness.AgentHarnessCoordinator;
import delivery.agent.harness.AgentHarnessException;
import delivery.agent.harness.AgentTaskType;
import delivery.agent.harness.NewAgentTaskRequest;
import delivery.portal.AgentHarnessProperties;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.util.List;

/**
 * Optional Cursor deep-dive after a Bug Hunter run completes ({@link AgentTaskType#BUG_INVESTIGATION_V1}).
 * The deterministic hunt already produces a candidate list; this bridge asks Cursor to triage —
 * call out the most likely real bugs vs. the false positives. No-op when harness flags are off.
 */
public final class HuntHarnessBridge {
    private static final Logger log = LogManager.getLogger(HuntHarnessBridge.class);

    private final AgentHarnessProperties properties;
    private final AgentHarnessCoordinator coordinator;

    public HuntHarnessBridge(AgentHarnessProperties properties, AgentHarnessCoordinator coordinator) {
        this.properties = properties == null ? new AgentHarnessProperties() : properties;
        this.coordinator = coordinator;
    }

    public record TriageResult(String status, String detail, String taskId) {
        public static TriageResult skipped(String why) { return new TriageResult("skipped", why, null); }
        public static TriageResult completed(String taskId) { return new TriageResult("completed", "ok", taskId); }
        public static TriageResult failed(String why) { return new TriageResult("failed", why, null); }
    }

    public TriageResult tryTriage(
            Path projectRoot,
            String parentJobId,
            String projectId,
            int bugCount,
            int scenarioCount,
            String huntMessage) {
        // Global flags — harness disabled anywhere short-circuits the whole path.
        if (!properties.isEnabled() || !properties.isCursorEnabled()) {
            return TriageResult.skipped("harness disabled");
        }
        if (coordinator == null) {
            return TriageResult.skipped("no coordinator");
        }
        // Nothing to triage.
        if (bugCount <= 0 && scenarioCount <= 0) {
            return TriageResult.skipped("no candidates to triage");
        }
        try {
            String objective = buildObjective(bugCount, scenarioCount, huntMessage);
            var created = coordinator.createTask(
                    projectRoot,
                    NewAgentTaskRequest.simple(
                            parentJobId,
                            projectId,
                            AgentTaskType.BUG_INVESTIGATION_V1,
                            objective,
                            List.of(),
                            projectRoot.toString()));
            coordinator.execute(projectRoot, created.parentJobId(), created.taskId());
            log.info("Hunt triage task {} dispatched for hunt {}", created.taskId(), parentJobId);
            return TriageResult.completed(created.taskId());
        } catch (AgentHarnessException e) {
            log.warn("Bug Hunter triage failed: {}", e.getMessage());
            return TriageResult.failed(e.getMessage());
        } catch (Exception e) {
            log.warn("Bug Hunter triage error", e);
            return TriageResult.failed(e.getMessage());
        }
    }

    private static String buildObjective(int bugCount, int scenarioCount, String huntMessage) {
        StringBuilder sb = new StringBuilder("Review the Bug Hunter output for this project and rank findings by likelihood of being real.");
        sb.append(" Candidates: ").append(bugCount).append(" bug(s), ").append(scenarioCount).append(" scenario(s).");
        if (huntMessage != null && !huntMessage.isBlank()) {
            sb.append(" Hunt summary: ").append(huntMessage.trim());
        }
        sb.append(" Mark each finding as LIKELY_REAL, UNCERTAIN, or FALSE_POSITIVE with a short reason.");
        return sb.toString();
    }
}
