package delivery.agent.validation.automate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import delivery.agent.harness.AgentHarnessCoordinator;
import delivery.agent.harness.AgentHarnessException;
import delivery.agent.harness.AgentProviderId;
import delivery.agent.harness.AgentRunResult;
import delivery.agent.harness.AgentTask;
import delivery.agent.harness.AgentRunStore;
import delivery.agent.harness.AgentTaskStatus;
import delivery.agent.harness.AgentTaskType;
import delivery.agent.harness.AgentWorkspacePaths;
import delivery.agent.harness.AgentWorkspacePreparer;
import delivery.agent.harness.NewAgentTaskRequest;
import delivery.agent.validation.contract.ContractValidationReportWriter;
import delivery.agent.validation.contract.ValidationReport;
import delivery.job.JobCancelSupport;
import delivery.job.JobCancelledException;
import delivery.agent.harness.AgentHarnessProperties;
import delivery.prompts.PromptCatalog;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;

/** Isolated FRAMEWORK_ADAPTATION_V1 — never writes the original candidate tree. */
public final class AutomateFrameworkRepairBridge {
    private static final Logger log = LogManager.getLogger(AutomateFrameworkRepairBridge.class);
    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private final AgentHarnessProperties properties;
    private final AgentHarnessCoordinator coordinator;
    private final BooleanSupplier jobCancelCheck;
    private final Duration waitBudget;
    private final AgentRunStore store = new AgentRunStore();
    private final AgentWorkspacePreparer preparer = new AgentWorkspacePreparer();
    /** Optional: resolves the workspace's locator settings for repair context (nullable; unset in tests). */
    private Function<String, LocatorPreference> locatorPreferenceLookup;
    /** Optional: customer-framework-template path for repair context (nullable; unset in tests). */
    private String templateRootPath;

    public record LocatorPreference(String locatorMode, String preferredHooks) {
    }

    /** Wire the project's saved locator ranking + preferred hooks into repair context. */
    public AutomateFrameworkRepairBridge withLocatorPreferenceLookup(
            Function<String, LocatorPreference> lookup) {
        this.locatorPreferenceLookup = lookup;
        return this;
    }

    /** Wire the customer-framework-template path (file reference, not hardcoded in prompt). */
    public AutomateFrameworkRepairBridge withTemplateRootPath(String path) {
        this.templateRootPath = path;
        return this;
    }

    public AutomateFrameworkRepairBridge(
            AgentHarnessProperties properties,
            AgentHarnessCoordinator coordinator,
            BooleanSupplier jobCancelCheck,
            Duration waitBudget) {
        this.properties = properties == null ? new AgentHarnessProperties() : properties;
        this.coordinator = coordinator;
        this.jobCancelCheck = jobCancelCheck;
        this.waitBudget = waitBudget;
    }

    public record RepairResult(
            boolean succeeded,
            String taskId,
            AgentTaskStatus terminalStatus,
            String summary,
            Path agentWorkspace,
            AgentProviderId providerId,
            Path repairCandidateRoot,
            Path projectRoot,
            String parentJobId,
            FrameworkRepairOperationalTrace operationalTrace) {
    }

    public RepairResult attemptRepair(
            Path projectRoot,
            Path repairCandidateRoot,
            ConversionJobContext context,
            ValidationReport failedReport) throws Exception {
        String parentJobId = context.jobId();
        if (coordinator == null) {
            return failedResult(repairCandidateRoot, projectRoot, parentJobId, "coordinator unavailable");
        }
        if (jobCancelCheck != null && jobCancelCheck.getAsBoolean()) {
            return failedResult(repairCandidateRoot, projectRoot, parentJobId, "JOB_CANCELLED");
        }
        String projectId = context.projectId();

        var created = coordinator.createTask(
                projectRoot,
                NewAgentTaskRequest.simple(
                        parentJobId,
                        projectId,
                        AgentTaskType.FRAMEWORK_ADAPTATION_V1,
                        buildObjective(failedReport),
                        List.of(),
                        repairCandidateRoot.toString()));

        Path taskDir = store.taskDir(projectRoot, parentJobId, created.taskId());
        preparer.ensureIsolatedCopy(taskDir, repairCandidateRoot);
        Files.createDirectories(taskDir);
        Map<String, Object> contextMap = new LinkedHashMap<>(failedReport.toApiMap());
        if (locatorPreferenceLookup != null) {
            LocatorPreference pref = locatorPreferenceLookup.apply(projectId);
            if (pref != null) {
                Map<String, Object> locatorBlock = new LinkedHashMap<>();
                if (pref.locatorMode() != null) locatorBlock.put("locatorMode", pref.locatorMode());
                if (pref.preferredHooks() != null) locatorBlock.put("preferredHooks", pref.preferredHooks());
                if (!locatorBlock.isEmpty()) {
                    contextMap.put("locatorPreference", locatorBlock);
                }
            }
        }
        if (templateRootPath != null && !templateRootPath.isBlank()) {
            contextMap.put("customerFrameworkTemplatePath", templateRootPath);
        }
        JSON.writeValue(taskDir.resolve("framework-repair-context.json").toFile(), contextMap);
        ContractValidationReportWriter.writeContractReport(projectRoot, parentJobId + "/framework-repair", failedReport);

        AgentRunResult result = executeWithJobCancel(projectRoot, parentJobId, created.taskId());
        FrameworkRepairOperationalTrace repairTrace = lastOperationalTrace;
        boolean ok = result.terminalStatus() == AgentTaskStatus.COMPLETED;
        AgentTask task = store.loadTask(projectRoot, parentJobId, created.taskId()).orElseThrow();
        Path agentWorkspace = AgentWorkspacePaths.isolatedWorkspace(taskDir);
        if (task.providerId() == AgentProviderId.CURSOR
                && task.workspacePath() != null
                && !task.workspacePath().isBlank()) {
            agentWorkspace = Path.of(task.workspacePath());
        }
        if (repairTrace == null) {
            repairTrace = lastOperationalTrace;
        }
        return new RepairResult(
                ok,
                created.taskId(),
                result.terminalStatus(),
                result.summary(),
                agentWorkspace,
                task.providerId(),
                repairCandidateRoot,
                projectRoot,
                parentJobId,
                repairTrace);
    }

    private FrameworkRepairOperationalTrace lastOperationalTrace;
    private FrameworkRepairOperationalTrace.Builder lastOperationalTraceBuilder;

    private static RepairResult failedResult(
            Path repairCandidateRoot, Path projectRoot, String parentJobId, String summary) {
        return new RepairResult(
                false,
                "",
                null,
                summary,
                null,
                null,
                repairCandidateRoot,
                projectRoot,
                parentJobId == null ? "" : parentJobId,
                null);
    }

    private AgentRunResult executeWithJobCancel(Path projectRoot, String parentJobId, String taskId) throws Exception {
        long budgetMs = delivery.agent.harness.recovery.ExecutionRecoveryHarnessBudget
                .waitBudgetMs(properties, waitBudget);
        long bridgeStart = System.nanoTime();
        FrameworkRepairOperationalTrace.Builder trace =
                FrameworkRepairOperationalTrace.builder().bridgeBudgetMs(budgetMs).taskId(taskId);
        lastOperationalTraceBuilder = trace;
        boolean bridgeTimedOut = false;
        try (ExecutorService exec = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<AgentRunResult> future = exec.submit(() -> coordinator.execute(projectRoot, parentJobId, taskId));
            try {
                AgentRunResult result = JobCancelSupport.awaitOrCancel(future, budgetMs, jobCancelCheck);
                long elapsed = (System.nanoTime() - bridgeStart) / 1_000_000L;
                trace.bridgeElapsedMs(elapsed);
                if (result.terminalStatus() != AgentTaskStatus.COMPLETED) {
                    trace.bridgeTerminalReason(FrameworkRepairTerminalReasonClassifier.fromRepairTerminal(
                            result.terminalStatus(), result.summary(), false));
                }
                FrameworkRepairTaskTelemetry.enrich(trace, projectRoot, parentJobId, taskId);
                lastOperationalTrace = trace.build();
                return result;
            } catch (JobCancelledException e) {
                future.cancel(true);
                bridgeTimedOut = false;
                trace.bridgeElapsedMs((System.nanoTime() - bridgeStart) / 1_000_000L);
                trace.bridgeTerminalReason(FrameworkRepairTerminalReasonClassifier.fromBridgeException(e, false));
                trace.harnessExceptionMessage(e.getMessage());
                FrameworkRepairTaskTelemetry.enrich(trace, projectRoot, parentJobId, taskId);
                lastOperationalTrace = trace.build();
                abort(projectRoot, parentJobId, taskId);
                throw e;
            } catch (TimeoutException e) {
                future.cancel(true);
                bridgeTimedOut = true;
                trace.bridgeElapsedMs((System.nanoTime() - bridgeStart) / 1_000_000L);
                trace.bridgeTerminalReason(FrameworkRepairTerminalReason.BRIDGE_WAIT_TIMEOUT);
                trace.harnessExceptionMessage("bridge wait budget exhausted");
                FrameworkRepairTaskTelemetry.enrich(trace, projectRoot, parentJobId, taskId);
                lastOperationalTrace = trace.build();
                abort(projectRoot, parentJobId, taskId);
                throw new AgentHarnessException("SIDECAR_TIMEOUT");
            } catch (ExecutionException e) {
                Throwable cause = e.getCause() == null ? e : e.getCause();
                trace.bridgeElapsedMs((System.nanoTime() - bridgeStart) / 1_000_000L);
                if (cause != null) {
                    trace.harnessExceptionMessage(cause.getMessage());
                }
                FrameworkRepairTaskTelemetry.enrich(trace, projectRoot, parentJobId, taskId);
                FrameworkRepairOperationalTrace built = trace.build();
                if (built.bridgeTerminalReason() == null) {
                    trace.bridgeTerminalReason(
                            FrameworkRepairTerminalReasonClassifier.fromBridgeException(cause, bridgeTimedOut));
                    built = trace.build();
                }
                lastOperationalTrace = built;
                if (cause instanceof AgentHarnessException ahe) {
                    throw ahe;
                }
                if (cause instanceof JobCancelledException jce) {
                    throw jce;
                }
                if (cause instanceof Exception ex) {
                    throw ex;
                }
                throw e;
            }
        }
    }

    private void abort(Path projectRoot, String parentJobId, String taskId) {
        try {
            store.requestCancel(projectRoot, parentJobId, taskId);
            coordinator.cancel(projectRoot, parentJobId, taskId);
        } catch (Exception e) {
            log.warn("framework repair cancel failed for {}: {}", taskId, e.getMessage());
        }
    }

    private static String buildObjective(ValidationReport failedReport) {
        var ruleIds = failedReport.findings().stream()
                .filter(f -> f.severity() == delivery.agent.validation.contract.ValidationSeverity.MANDATORY)
                .map(delivery.agent.validation.contract.ValidationFinding::ruleId)
                .distinct()
                .sorted()
                .toList();
        return PromptCatalog.format("agent-validation/automate", Map.of(
                "ruleIds", String.join(", ", ruleIds)));
    }

    /** Job context for harness tasks without pulling full ConversionJobRequest into harness layer. */
    /** Last operational trace from the most recent {@link #attemptRepair} / execute call (for gate reporting). */
    public FrameworkRepairOperationalTrace lastRepairOperationalTrace() {
        return lastOperationalTrace;
    }

    public record ConversionJobContext(String jobId, String projectId) {
    }
}