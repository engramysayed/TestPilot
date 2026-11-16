package delivery.agent.validation.automate;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Timing and terminal reason for one repair attempt (ms unless noted). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FrameworkRepairOperationalTrace(
        FrameworkRepairTerminalReason bridgeTerminalReason,
        FrameworkRepairTerminalReason acceptanceTerminalReason,
        Long bridgeBudgetMs,
        Long bridgeElapsedMs,
        Double bridgeBudgetUtilizationPct,
        Long workspacePrepareMs,
        Long storeLockWaitMs,
        Long sidecarStartupMs,
        Long timeToRunStartedMs,
        Long cursorRunMs,
        Long resultRetrievalMs,
        Long reconciliationMs,
        Boolean agentRunStarted,
        String taskId,
        String agentId,
        String agentRunId,
        String taskStatus,
        String harnessExceptionMessage) {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private FrameworkRepairTerminalReason bridgeTerminalReason;
        private FrameworkRepairTerminalReason acceptanceTerminalReason;
        private Long bridgeBudgetMs;
        private Long bridgeElapsedMs;
        private Long workspacePrepareMs;
        private Long storeLockWaitMs;
        private Long sidecarStartupMs;
        private Long timeToRunStartedMs;
        private Long cursorRunMs;
        private Long resultRetrievalMs;
        private Long reconciliationMs;
        private Boolean agentRunStarted;
        private String taskId;
        private String agentId;
        private String agentRunId;
        private String taskStatus;
        private String harnessExceptionMessage;

        public Builder bridgeTerminalReason(FrameworkRepairTerminalReason v) {
            this.bridgeTerminalReason = v;
            return this;
        }

        public Builder acceptanceTerminalReason(FrameworkRepairTerminalReason v) {
            this.acceptanceTerminalReason = v;
            return this;
        }

        public Builder bridgeBudgetMs(long v) {
            this.bridgeBudgetMs = v;
            return this;
        }

        public Builder bridgeElapsedMs(long v) {
            this.bridgeElapsedMs = v;
            return this;
        }

        public Builder workspacePrepareMs(Long v) {
            this.workspacePrepareMs = v;
            return this;
        }

        public Builder storeLockWaitMs(Long v) {
            this.storeLockWaitMs = v;
            return this;
        }

        public Builder sidecarStartupMs(Long v) {
            this.sidecarStartupMs = v;
            return this;
        }

        public Builder timeToRunStartedMs(Long v) {
            this.timeToRunStartedMs = v;
            return this;
        }

        public Builder cursorRunMs(Long v) {
            this.cursorRunMs = v;
            return this;
        }

        public Builder resultRetrievalMs(Long v) {
            this.resultRetrievalMs = v;
            return this;
        }

        public Builder reconciliationMs(Long v) {
            this.reconciliationMs = v;
            return this;
        }

        public Builder agentRunStarted(Boolean v) {
            this.agentRunStarted = v;
            return this;
        }

        public Builder taskId(String v) {
            this.taskId = v;
            return this;
        }

        public Builder agentId(String v) {
            this.agentId = v;
            return this;
        }

        public Builder agentRunId(String v) {
            this.agentRunId = v;
            return this;
        }

        public Builder taskStatus(String v) {
            this.taskStatus = v;
            return this;
        }

        public Builder harnessExceptionMessage(String v) {
            this.harnessExceptionMessage = v;
            return this;
        }

        public FrameworkRepairOperationalTrace build() {
            Double util = null;
            if (bridgeBudgetMs != null && bridgeBudgetMs > 0 && bridgeElapsedMs != null) {
                util = Math.min(100.0, (bridgeElapsedMs * 100.0) / bridgeBudgetMs);
            }
            return new FrameworkRepairOperationalTrace(
                    bridgeTerminalReason,
                    acceptanceTerminalReason,
                    bridgeBudgetMs,
                    bridgeElapsedMs,
                    util,
                    workspacePrepareMs,
                    storeLockWaitMs,
                    sidecarStartupMs,
                    timeToRunStartedMs,
                    cursorRunMs,
                    resultRetrievalMs,
                    reconciliationMs,
                    agentRunStarted,
                    taskId,
                    agentId,
                    agentRunId,
                    taskStatus,
                    harnessExceptionMessage);
        }
    }

    public FrameworkRepairOperationalTrace withAcceptanceTerminalReason(FrameworkRepairTerminalReason reason) {
        return new FrameworkRepairOperationalTrace(
                bridgeTerminalReason,
                reason,
                bridgeBudgetMs,
                bridgeElapsedMs,
                bridgeBudgetUtilizationPct,
                workspacePrepareMs,
                storeLockWaitMs,
                sidecarStartupMs,
                timeToRunStartedMs,
                cursorRunMs,
                resultRetrievalMs,
                reconciliationMs,
                agentRunStarted,
                taskId,
                agentId,
                agentRunId,
                taskStatus,
                harnessExceptionMessage);
    }

    public FrameworkRepairOperationalTrace withReconciliationMs(long ms) {
        return new FrameworkRepairOperationalTrace(
                bridgeTerminalReason,
                acceptanceTerminalReason,
                bridgeBudgetMs,
                bridgeElapsedMs,
                bridgeBudgetUtilizationPct,
                workspacePrepareMs,
                storeLockWaitMs,
                sidecarStartupMs,
                timeToRunStartedMs,
                cursorRunMs,
                resultRetrievalMs,
                ms,
                agentRunStarted,
                taskId,
                agentId,
                agentRunId,
                taskStatus,
                harnessExceptionMessage);
    }
}