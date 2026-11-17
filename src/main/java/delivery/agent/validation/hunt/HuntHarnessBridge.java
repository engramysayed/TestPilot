package delivery.agent.validation.hunt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import delivery.agent.harness.AgentHarnessCoordinator;
import delivery.agent.harness.AgentHarnessException;
import delivery.agent.harness.AgentRunStore;
import delivery.agent.harness.AgentTaskType;
import delivery.agent.harness.NewAgentTaskRequest;
import delivery.portal.AgentHarnessProperties;
import delivery.prompts.PromptCatalog;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Optional Cursor deep-dive after a Bug Hunter run completes ({@link AgentTaskType#BUG_INVESTIGATION_V1}).
 * The deterministic hunt already produces a candidate list; this bridge asks Cursor to triage —
 * call out the most likely real bugs vs. the false positives. No-op when harness flags are off.
 */
public final class HuntHarnessBridge {
    private static final Logger log = LogManager.getLogger(HuntHarnessBridge.class);
    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private static final String CONTEXT_FILENAME = "hunt-triage-context.json";
    private static final String CONTEXT_CONTRACT_ID = "keel.hunt-triage-context/v1";

    private final AgentHarnessProperties properties;
    private final AgentHarnessCoordinator coordinator;
    private final AgentRunStore store = new AgentRunStore();

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
            writeContextFile(projectRoot, created.parentJobId(), created.taskId(),
                    bugCount, scenarioCount, huntMessage);
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
        String summaryBlock = huntMessage == null || huntMessage.isBlank()
                ? ""
                : "\nHunt summary: " + huntMessage.trim();
        return PromptCatalog.format("agent-validation/hunt", Map.of(
                "bugCount", String.valueOf(bugCount),
                "scenarioCount", String.valueOf(scenarioCount),
                "huntSummaryBlock", summaryBlock));
    }

    private void writeContextFile(Path projectRoot, String parentJobId, String taskId,
                                  int bugCount, int scenarioCount, String huntMessage) {
        try {
            Path taskDir = store.taskDir(projectRoot, parentJobId, taskId);
            Files.createDirectories(taskDir);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("schemaVersion", 1);
            body.put("contractId", CONTEXT_CONTRACT_ID);
            body.put("parentJobId", parentJobId);
            body.put("bugCount", bugCount);
            body.put("scenarioCount", scenarioCount);
            // Hunt writes under projectRoot/hunt-runs/<parentJobId>/; paths are relative so Cursor
            // resolves them against the workspace root (projectRoot).
            Path huntRun = projectRoot.resolve("hunt-runs").resolve(parentJobId);
            body.put("huntRunDir", projectRoot.relativize(huntRun).toString().replace('\\', '/'));
            body.put("bugsPath", projectRoot.relativize(huntRun.resolve("bugs.json")).toString().replace('\\', '/'));
            body.put("scenariosPath", projectRoot.relativize(huntRun.resolve("scenarios.json")).toString().replace('\\', '/'));
            if (huntMessage != null && !huntMessage.isBlank()) {
                body.put("huntSummary", huntMessage.trim());
            }
            JSON.writeValue(taskDir.resolve(CONTEXT_FILENAME).toFile(), body);
        } catch (Exception e) {
            // Non-fatal — the agent can still read the projectRoot directly.
            log.warn("hunt-triage-context write failed for {}: {}", taskId, e.getMessage());
        }
    }
}
