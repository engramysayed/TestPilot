package delivery.agent.harness.recovery;

import delivery.agent.harness.AgentHarnessProperties;

import java.time.Duration;

public final class ExecutionRecoveryHarnessBudget {
    private ExecutionRecoveryHarnessBudget() {
    }

    public static long waitBudgetMs(AgentHarnessProperties properties, Duration leaseCap) {
        long propsMs = Math.max(1, properties == null ? 15 : properties.getMaxRuntimeMinutes()) * 60_000L;
        if (leaseCap == null || leaseCap.isZero() || leaseCap.isNegative()) {
            return propsMs;
        }
        long capMs = leaseCap.toMillis();
        if (capMs <= 0) {
            return propsMs;
        }
        return Math.min(propsMs, capMs);
    }
}