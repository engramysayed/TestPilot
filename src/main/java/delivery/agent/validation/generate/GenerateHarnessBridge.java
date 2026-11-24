package delivery.agent.validation.generate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import delivery.agent.harness.AgentHarnessCoordinator;
import delivery.agent.harness.AgentHarnessException;
import delivery.agent.harness.AgentRunStore;
import delivery.agent.harness.AgentTaskType;
import delivery.agent.validation.contract.ValidationReport;
import delivery.agent.validation.contract.ContractValidationReportWriter;
import delivery.agent.harness.AgentHarnessProperties;
import delivery.excel.ManualTestCase;
import delivery.agent.harness.NewAgentTaskRequest;
import delivery.prompts.PromptCatalog;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
/**
 * Optional Cursor escalation for unresolved TC contract violations ({@link AgentTaskType#GENERATED_TEST_REVIEW_V1}).
 * Does not modify {@code heal.mjs}. No-op when harness flags are off.
 */
public final class GenerateHarnessBridge {
    private static final Logger log = LogManager.getLogger(GenerateHarnessBridge.class);
    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private static final String CONTEXT_FILENAME = "generate-context.json";
    private static final String CONTEXT_CONTRACT_ID = "keel.generate-context/v1";

    private final AgentHarnessProperties properties;
    private final AgentHarnessCoordinator coordinator;
    private final AgentRunStore store = new AgentRunStore();

    public GenerateHarnessBridge(AgentHarnessProperties properties, AgentHarnessCoordinator coordinator) {
        this.properties = properties == null ? new AgentHarnessProperties() : properties;
        this.coordinator = coordinator;
    }

    public EscalationResult tryCursorReview(
            Path projectRoot,
            String parentJobId,
            String projectId,
            List<ManualTestCase> cases,
            ValidationReport failedReport,
            String userStoryText) {
        if (!properties.isEnabled()
                || !properties.isTcValidationEnabled()
                || !properties.isTcValidationCursorEscalationEnabled()
                || !properties.isCursorEnabled()) {
            return EscalationResult.skipped("harness flags disabled");
        }
        if (failedReport == null || failedReport.passed()) {
            return EscalationResult.skipped("nothing to escalate");
        }
        try {
            ContractValidationReportWriter.writeContractReport(projectRoot, parentJobId, failedReport);
            String objective = buildObjective(failedReport, userStoryText);
            var created = coordinator.createTask(
                    projectRoot,
                    NewAgentTaskRequest.simple(
                            parentJobId,
                            projectId,
                            AgentTaskType.GENERATED_TEST_REVIEW_V1,
                            objective,
                            List.of(),
                            projectRoot.toString()));
            writeContextFile(projectRoot, created.parentJobId(), created.taskId(),
                    cases, failedReport, userStoryText);
            coordinator.execute(projectRoot, created.parentJobId(), created.taskId());
            return EscalationResult.completed(created.taskId());
        } catch (AgentHarnessException e) {
            log.warn("TC validation Cursor escalation failed: {}", e.getMessage());
            return EscalationResult.failed(e.getMessage());
        } catch (Exception e) {
            log.warn("TC validation Cursor escalation error", e);
            return EscalationResult.failed(e.getMessage());
        }
    }

    private static String buildObjective(ValidationReport report, String userStoryText) {
        String storyBlock = "";
        if (userStoryText != null && !userStoryText.isBlank()) {
            String story = userStoryText.trim();
            storyBlock = "Story excerpt: " + (story.length() > 200 ? story.substring(0, 200) + "…" : story);
        }
        return PromptCatalog.format("agent-validation/generate", Map.of(
                "contractId", "keel.tc-contract/v1",
                "findingsCount", String.valueOf(report.findings().size()),
                "storyBlock", storyBlock));
    }

    private void writeContextFile(Path projectRoot, String parentJobId, String taskId,
                                  List<ManualTestCase> cases, ValidationReport report,
                                  String userStoryText) {
        try {
            Path taskDir = store.taskDir(projectRoot, parentJobId, taskId);
            Files.createDirectories(taskDir);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("schemaVersion", 1);
            body.put("contractId", CONTEXT_CONTRACT_ID);
            body.put("parentJobId", parentJobId);
            body.put("contractValidationReportPath",
                    projectRoot.relativize(
                            projectRoot.resolve("contract-reports")
                                    .resolve("contract-validation-report-" + parentJobId + ".json"))
                            .toString().replace('\\', '/'));
            body.put("generatedCaseCount", cases == null ? 0 : cases.size());
            body.put("testCases", cases == null ? List.of() : cases);
            body.put("findingsCount", report.findings().size());
            if (userStoryText != null && !userStoryText.isBlank()) {
                body.put("userStory", userStoryText.trim());
            }
            JSON.writeValue(taskDir.resolve(CONTEXT_FILENAME).toFile(), body);
        } catch (Exception e) {
            log.warn("generate-context write failed for {}: {}", taskId, e.getMessage());
        }
    }

    /** Cases Cursor returned. Empty when the result has no usable testCases array. */
    public List<ManualTestCase> takeRepairedCases(Path projectRoot, String parentJobId, String taskId) {
        if (projectRoot == null || parentJobId == null || taskId == null || taskId.isBlank()) {
            return List.of();
        }
        try {
            Path repairedFile = store.taskDir(projectRoot, parentJobId, taskId).resolve("repaired-cases.json");
            if (Files.isRegularFile(repairedFile)) {
                List<ManualTestCase> fromFile = casesFromAgentText(Files.readString(repairedFile), Map.of());
                if (!fromFile.isEmpty()) {
                    return fromFile;
                }
            }
            var result = store.loadResult(projectRoot, parentJobId, taskId);
            if (result.isEmpty()) {
                return List.of();
            }
            return casesFromAgentText(result.get().summary(), result.get().payload());
        } catch (Exception e) {
            log.warn("Could not read repaired cases for {}: {}", taskId, e.getMessage());
            return List.of();
        }
    }

    static List<ManualTestCase> casesFromAgentText(String summary, Map<String, Object> payload) {
        try {
            if (payload != null && (payload.containsKey("testCases") || payload.containsKey("cases"))) {
                return delivery.excel.GeneratedTcJsonParser.parse(JSON.writeValueAsString(payload)).cases();
            }
        } catch (Exception ignored) {
            // A bad payload leaves the original cases in place.
        }
        try {
            if (summary != null && summary.contains("testCases")) {
                return delivery.excel.GeneratedTcJsonParser.parse(summary).cases();
            }
        } catch (Exception ignored) {
            return List.of();
        }
        return List.of();
    }

    public record EscalationResult(String status, String detail, String taskId) {
        static EscalationResult skipped(String reason) {
            return new EscalationResult("SKIPPED", reason, "");
        }

        static EscalationResult completed(String taskId) {
            return new EscalationResult("COMPLETED", "", taskId == null ? "" : taskId);
        }

        static EscalationResult failed(String message) {
            return new EscalationResult("FAILED", message == null ? "" : message, "");
        }
    }
}