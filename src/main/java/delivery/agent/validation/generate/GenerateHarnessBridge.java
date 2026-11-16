package delivery.agent.validation.generate;

import delivery.agent.harness.AgentHarnessCoordinator;
import delivery.agent.harness.AgentHarnessException;
import delivery.agent.harness.AgentTaskType;
import delivery.agent.validation.contract.ValidationReport;
import delivery.agent.validation.contract.ContractValidationReportWriter;
import delivery.portal.AgentHarnessProperties;
import delivery.excel.ManualTestCase;
import delivery.agent.harness.NewAgentTaskRequest;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.util.List;
/**
 * Optional Cursor escalation for unresolved TC contract violations ({@link AgentTaskType#GENERATED_TEST_REVIEW_V1}).
 * Does not modify {@code heal.mjs}. No-op when harness flags are off.
 */
public final class GenerateHarnessBridge {
    private static final Logger log = LogManager.getLogger(GenerateHarnessBridge.class);

    private final AgentHarnessProperties properties;
    private final AgentHarnessCoordinator coordinator;

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
        StringBuilder sb = new StringBuilder();
        sb.append("Review generated test cases against keel.tc-contract/v1. ");
        sb.append("Fix violations without inventing requirements or weakening mandatory rules. ");
        sb.append("Preserve user story intent. Findings: ");
        sb.append(report.findings().size());
        if (userStoryText != null && !userStoryText.isBlank()) {
            sb.append(". Story excerpt: ");
            String story = userStoryText.trim();
            sb.append(story.length() > 200 ? story.substring(0, 200) + "…" : story);
        }
        return sb.toString();
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