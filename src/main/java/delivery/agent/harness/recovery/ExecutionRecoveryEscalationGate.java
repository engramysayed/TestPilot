package delivery.agent.harness.recovery;

import delivery.job.JobCancelledException;
import delivery.agent.harness.AgentHarnessProperties;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

/**
 * Decides whether to invoke Agent Harness investigation after the prove heal pipeline is exhausted.
 * Does not alter prove pass/fail semantics.
 */
public final class ExecutionRecoveryEscalationGate {
    private final AgentHarnessProperties properties;
    private final ExecutionRecoveryHarnessBridge bridge;
    private final BooleanSupplier jobCancelCheck;
    private final Set<String> invokedKeys = ConcurrentHashMap.newKeySet();

    public ExecutionRecoveryEscalationGate(
            AgentHarnessProperties properties,
            ExecutionRecoveryHarnessBridge bridge) {
        this(properties, bridge, null);
    }

    public ExecutionRecoveryEscalationGate(
            AgentHarnessProperties properties,
            ExecutionRecoveryHarnessBridge bridge,
            BooleanSupplier jobCancelCheck) {
        this.properties = properties == null ? new AgentHarnessProperties() : properties;
        this.bridge = bridge;
        this.jobCancelCheck = jobCancelCheck;
    }

    public boolean flagsEnabled() {
        return ExecutionRecoveryEligibility.harnessFlagsEnabled(properties);
    }

    /**
     * @return investigation outcome; original prove failure remains authoritative regardless.
     */
    public ExecutionInvestigationOutcome tryEscalate(ExecutionRecoveryEscalationRequest request) {
        if (!ExecutionRecoveryEligibility.harnessFlagsEnabled(properties)) {
            return ExecutionInvestigationOutcome.skipped("execution recovery harness flags disabled");
        }
        if (!ExecutionRecoveryEligibility.shouldInvestigate(request)) {
            return ExecutionInvestigationOutcome.skipped("ineligible for investigation");
        }
        if (jobCancelCheck != null && jobCancelCheck.getAsBoolean()) {
            return ExecutionInvestigationOutcome.skipped("JOB_CANCELLED");
        }
        String key = request.deduplicationKey();
        if (key == null || key.isBlank()) {
            return ExecutionInvestigationOutcome.skipped("missing deduplication key");
        }
        if (!invokedKeys.add(key)) {
            return ExecutionInvestigationOutcome.duplicate();
        }
        if (bridge == null) {
            return ExecutionInvestigationOutcome.harnessFailed("bridge unavailable");
        }
        try {
            return bridge.investigate(request);
        } catch (JobCancelledException e) {
            throw e;
        }
    }

    /** Test helper — reset dedup state between scenarios. */
    public void resetDedupState() {
        invokedKeys.clear();
    }
}