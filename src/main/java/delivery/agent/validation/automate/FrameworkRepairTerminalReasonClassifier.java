package delivery.agent.validation.automate;

import delivery.agent.harness.AgentTaskStatus;

/** Maps harness/gate outcomes to operator terminal reasons without changing statuses. */
final class FrameworkRepairTerminalReasonClassifier {

    private FrameworkRepairTerminalReasonClassifier() {
    }

    static FrameworkRepairTerminalReason fromBridgeException(Throwable cause, boolean bridgeWaitTimedOut) {
        if (bridgeWaitTimedOut) {
            return FrameworkRepairTerminalReason.BRIDGE_WAIT_TIMEOUT;
        }
        if (cause == null) {
            return FrameworkRepairTerminalReason.UNSPECIFIED;
        }
        String msg = cause.getMessage() == null ? "" : cause.getMessage();
        if (cause instanceof delivery.job.JobCancelledException) {
            return FrameworkRepairTerminalReason.EXPLICIT_JOB_CANCEL;
        }
        if ("SIDECAR_TIMEOUT".equals(msg)) {
            return FrameworkRepairTerminalReason.SIDECAR_START_TIMEOUT;
        }
        if ("INTERRUPTED".equals(msg)) {
            return FrameworkRepairTerminalReason.REPAIR_INTERRUPTED;
        }
        if (msg.contains("reconcile poll budget")) {
            return FrameworkRepairTerminalReason.RECONCILE_POLL_EXHAUSTED;
        }
        if (msg.startsWith("SIDECAR_DIED") || msg.contains("cursor start failed")) {
            return FrameworkRepairTerminalReason.CURSOR_RUN_FAILURE;
        }
        return FrameworkRepairTerminalReason.REPAIR_FAILED;
    }

    static FrameworkRepairTerminalReason fromRepairTerminal(
            AgentTaskStatus status, String summary, boolean bridgeWaitTimedOut) {
        if (bridgeWaitTimedOut) {
            return FrameworkRepairTerminalReason.BRIDGE_WAIT_TIMEOUT;
        }
        if (status == null) {
            return FrameworkRepairTerminalReason.UNSPECIFIED;
        }
        if (status == AgentTaskStatus.COMPLETED) {
            return FrameworkRepairTerminalReason.UNSPECIFIED;
        }
        if (status == AgentTaskStatus.INTERRUPTED) {
            if (summary != null && summary.toLowerCase().contains("reconcile poll budget")) {
                return FrameworkRepairTerminalReason.RECONCILE_POLL_EXHAUSTED;
            }
            return FrameworkRepairTerminalReason.REPAIR_INTERRUPTED;
        }
        if (status == AgentTaskStatus.TIMED_OUT) {
            return FrameworkRepairTerminalReason.SIDECAR_START_TIMEOUT;
        }
        if (status == AgentTaskStatus.CANCELLED) {
            return FrameworkRepairTerminalReason.EXPLICIT_JOB_CANCEL;
        }
        return FrameworkRepairTerminalReason.CURSOR_RUN_FAILURE;
    }

    static FrameworkRepairTerminalReason fromAcceptanceStatus(AutomateFrameworkAcceptanceStatus status) {
        if (status == null) {
            return FrameworkRepairTerminalReason.UNSPECIFIED;
        }
        return switch (status) {
            case ACCEPTED, CONTRACT_PASSED -> FrameworkRepairTerminalReason.SUCCESS;
            case RECONCILIATION_FAILED -> FrameworkRepairTerminalReason.RECONCILIATION_FAILURE;
            case COMPILE_FAILED -> FrameworkRepairTerminalReason.COMPILE_FAILURE;
            case REVALIDATION_FAILED -> FrameworkRepairTerminalReason.REVALIDATION_FAILURE;
            case REPAIR_INTERRUPTED -> FrameworkRepairTerminalReason.REPAIR_INTERRUPTED;
            case REPAIR_FAILED -> FrameworkRepairTerminalReason.REPAIR_FAILED;
            case REPAIR_NOT_ENABLED -> FrameworkRepairTerminalReason.REPAIR_NOT_ENABLED;
            case REPAIR_UNAVAILABLE -> FrameworkRepairTerminalReason.REPAIR_UNAVAILABLE;
            default -> FrameworkRepairTerminalReason.UNSPECIFIED;
        };
    }
}