package delivery.agent.harness.recovery;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import delivery.agent.harness.AgentHarnessCoordinator;
import delivery.agent.harness.AgentHarnessException;
import delivery.agent.harness.AgentRunResult;
import delivery.agent.harness.AgentRunStore;
import delivery.agent.harness.AgentTaskType;
import delivery.agent.harness.NewAgentTaskRequest;
import delivery.job.JobCancelSupport;
import delivery.job.JobCancelledException;
import delivery.portal.AgentHarnessProperties;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;

/** Runs EXECUTION_FAILURE_INVESTIGATION_V1 via coordinator (Fake or Cursor per properties). */
public final class ExecutionRecoveryHarnessBridge {
    private static final Logger log = LogManager.getLogger(ExecutionRecoveryHarnessBridge.class);
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private final AgentHarnessProperties properties;
    private final AgentHarnessCoordinator coordinator;
    private final BooleanSupplier jobCancelCheck;
    private final Duration investigationBudgetCap;
    private final AgentRunStore store = new AgentRunStore();

    public ExecutionRecoveryHarnessBridge(
            AgentHarnessProperties properties,
            AgentHarnessCoordinator coordinator) {
        this(properties, coordinator, null, null);
    }

    public ExecutionRecoveryHarnessBridge(
            AgentHarnessProperties properties,
            AgentHarnessCoordinator coordinator,
            BooleanSupplier jobCancelCheck) {
        this(properties, coordinator, jobCancelCheck, null);
    }

    public ExecutionRecoveryHarnessBridge(
            AgentHarnessProperties properties,
            AgentHarnessCoordinator coordinator,
            BooleanSupplier jobCancelCheck,
            Duration investigationBudgetCap) {
        this.properties = properties == null ? new AgentHarnessProperties() : properties;
        this.coordinator = coordinator;
        this.jobCancelCheck = jobCancelCheck;
        this.investigationBudgetCap = investigationBudgetCap;
    }

    public ExecutionInvestigationOutcome investigate(ExecutionRecoveryEscalationRequest request) {
        if (coordinator == null) {
            return ExecutionInvestigationOutcome.harnessFailed("coordinator unavailable");
        }
        if (jobCancelCheck != null && jobCancelCheck.getAsBoolean()) {
            return ExecutionInvestigationOutcome.skipped("JOB_CANCELLED");
        }
        ConversionJobRequestCheck.require(request);
        try {
            Path projectRoot = request.jobRequest().storeRoot().resolve(request.jobRequest().projectId());
            Files.createDirectories(projectRoot);
            String parentJobId = request.jobRequest().jobId();
            String projectId = request.jobRequest().projectId();
            Path workDir = request.jobRequest().workDir();

            ExecutionInvestigationEvidenceV1 evidence = ExecutionInvestigationEvidenceBuilder.build(request);
            String objective = "Investigate execution failure for " + evidence.testCaseId()
                    + " step " + evidence.intentIndex()
                    + " (" + evidence.failureClass() + "). Read-only analysis; do not weaken expected results.";

            var created = coordinator.createTask(
                    projectRoot,
                    NewAgentTaskRequest.simple(
                            parentJobId,
                            projectId,
                            AgentTaskType.EXECUTION_FAILURE_INVESTIGATION_V1,
                            objective,
                            List.of(),
                            workDir == null ? projectRoot.toString() : workDir.toString()));

            Path taskDir = new AgentRunStore().taskDir(projectRoot, parentJobId, created.taskId());
            Files.createDirectories(taskDir);
            JSON.writeValue(taskDir.resolve("execution-investigation-evidence.json").toFile(), evidence.toMap());

            if (jobCancelCheck != null && jobCancelCheck.getAsBoolean()) {
                return ExecutionInvestigationOutcome.skipped("JOB_CANCELLED");
            }

            AgentRunResult result = executeWithJobCancel(projectRoot, parentJobId, created.taskId());
            log.info("Execution investigation task {} terminal {}", created.taskId(), result.terminalStatus());
            return ExecutionInvestigationOutcome.investigated(
                    created.taskId(),
                    result.terminalStatus(),
                    result.summary());
        } catch (JobCancelledException e) {
            throw e;
        } catch (AgentHarnessException e) {
            if ("INTERRUPTED".equals(e.getMessage())) {
                return ExecutionInvestigationOutcome.investigated(
                        "",
                        delivery.agent.harness.AgentTaskStatus.INTERRUPTED,
                        "INTERRUPTED");
            }
            return ExecutionInvestigationOutcome.harnessFailed(e.getMessage());
        } catch (Exception e) {
            return ExecutionInvestigationOutcome.harnessFailed(e.getMessage());
        }
    }

    private AgentRunResult executeWithJobCancel(Path projectRoot, String parentJobId, String taskId)
            throws Exception {
        long budgetMs = ExecutionRecoveryHarnessBudget.waitBudgetMs(properties, investigationBudgetCap);
        try (ExecutorService exec = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<AgentRunResult> future = exec.submit(
                    () -> coordinator.execute(projectRoot, parentJobId, taskId));
            try {
                return JobCancelSupport.awaitOrCancel(future, budgetMs, jobCancelCheck);
            } catch (JobCancelledException e) {
                future.cancel(true);
                abortInvestigation(projectRoot, parentJobId, taskId);
                throw e;
            } catch (TimeoutException e) {
                future.cancel(true);
                abortInvestigation(projectRoot, parentJobId, taskId);
                throw new AgentHarnessException("SIDECAR_TIMEOUT");
            } catch (ExecutionException e) {
                Throwable cause = e.getCause() == null ? e : e.getCause();
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

    private void abortInvestigation(Path projectRoot, String parentJobId, String taskId) {
        try {
            store.requestCancel(projectRoot, parentJobId, taskId);
            coordinator.cancel(projectRoot, parentJobId, taskId);
        } catch (Exception e) {
            log.warn("execution investigation cancel (best-effort) failed for {}: {}", taskId, e.getMessage());
        }
    }

    private static final class ConversionJobRequestCheck {
        static void require(ExecutionRecoveryEscalationRequest request) {
            if (request.jobRequest() == null
                    || request.jobRequest().storeRoot() == null
                    || request.jobRequest().projectId() == null
                    || request.jobRequest().jobId() == null) {
                throw new IllegalArgumentException("job request paths required");
            }
        }
    }
}