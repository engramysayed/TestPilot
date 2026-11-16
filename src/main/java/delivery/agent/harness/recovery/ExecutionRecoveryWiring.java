package delivery.agent.harness.recovery;

import delivery.agent.harness.AgentHarnessCoordinator;
import delivery.portal.AgentHarnessProperties;

import java.time.Duration;
import java.util.function.BooleanSupplier;

/**
 * Constructs optional execution-recovery harness wiring for Execute/Automate prove jobs.
 * Provider selection follows {@link AgentHarnessCoordinator#forProduction(AgentHarnessProperties)} —
 * Cursor is not enabled by execution-recovery flags alone.
 */
public final class ExecutionRecoveryWiring {
    private ExecutionRecoveryWiring() {
    }

    /**
     * @return gate when harness + execution-recovery escalation flags are on; otherwise {@code null}
     */
    public static ExecutionRecoveryEscalationGate optionalGate(AgentHarnessProperties properties) {
        return optionalGate(properties, (BooleanSupplier) null);
    }

    /**
     * Portal execute path: ties harness investigation to the same job cancel signal as {@link delivery.job.ProvePhase}.
     */
    public static ExecutionRecoveryEscalationGate optionalGate(
            AgentHarnessProperties properties,
            BooleanSupplier jobCancelCheck) {
        return optionalGate(properties, jobCancelCheck, null);
    }

    public static ExecutionRecoveryEscalationGate optionalGate(
            AgentHarnessProperties properties,
            BooleanSupplier jobCancelCheck,
            Duration investigationBudgetCap) {
        if (!ExecutionRecoveryEligibility.harnessFlagsEnabled(properties)) {
            return null;
        }
        AgentHarnessCoordinator coordinator = AgentHarnessCoordinator.forProduction(properties);
        return optionalGate(properties, coordinator, jobCancelCheck, investigationBudgetCap);
    }

    /** Same as {@link #optionalGate(AgentHarnessProperties)} but allows a test/runtime override coordinator. */
    public static ExecutionRecoveryEscalationGate optionalGate(
            AgentHarnessProperties properties,
            AgentHarnessCoordinator coordinator) {
        return optionalGate(properties, coordinator, null, null);
    }

    public static ExecutionRecoveryEscalationGate optionalGate(
            AgentHarnessProperties properties,
            AgentHarnessCoordinator coordinator,
            BooleanSupplier jobCancelCheck) {
        return optionalGate(properties, coordinator, jobCancelCheck, null);
    }

    public static ExecutionRecoveryEscalationGate optionalGate(
            AgentHarnessProperties properties,
            AgentHarnessCoordinator coordinator,
            BooleanSupplier jobCancelCheck,
            Duration investigationBudgetCap) {
        if (!ExecutionRecoveryEligibility.harnessFlagsEnabled(properties)) {
            return null;
        }
        var bridge = new ExecutionRecoveryHarnessBridge(
                properties, coordinator, jobCancelCheck, investigationBudgetCap);
        return new ExecutionRecoveryEscalationGate(properties, bridge, jobCancelCheck);
    }
}