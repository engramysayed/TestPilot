package delivery.agent.validation.automate;

import delivery.agent.harness.AgentTaskStatus;
import delivery.job.JobCancelledException;
import org.testng.Assert;
import org.testng.annotations.Test;

public class FrameworkRepairTerminalReasonClassifierTest {

    @Test
    public void bridgeWaitTimeout_flagWins() {
        Assert.assertEquals(
                FrameworkRepairTerminalReasonClassifier.fromBridgeException(new RuntimeException(), true),
                FrameworkRepairTerminalReason.BRIDGE_WAIT_TIMEOUT);
    }

    @Test
    public void explicitJobCancel_fromException() {
        Assert.assertEquals(
                FrameworkRepairTerminalReasonClassifier.fromBridgeException(new JobCancelledException(), false),
                FrameworkRepairTerminalReason.EXPLICIT_JOB_CANCEL);
    }

    @Test
    public void reconcilePoll_exhaustedSummary() {
        Assert.assertEquals(
                FrameworkRepairTerminalReasonClassifier.fromRepairTerminal(
                        AgentTaskStatus.INTERRUPTED, "reconcile poll budget exhausted for task", false),
                FrameworkRepairTerminalReason.RECONCILE_POLL_EXHAUSTED);
    }

    @Test
    public void acceptanceStatuses_mapToOperatorReasons() {
        Assert.assertEquals(
                FrameworkRepairTerminalReasonClassifier.fromAcceptanceStatus(
                        AutomateFrameworkAcceptanceStatus.ACCEPTED),
                FrameworkRepairTerminalReason.SUCCESS);
        Assert.assertEquals(
                FrameworkRepairTerminalReasonClassifier.fromAcceptanceStatus(
                        AutomateFrameworkAcceptanceStatus.COMPILE_FAILED),
                FrameworkRepairTerminalReason.COMPILE_FAILURE);
        Assert.assertEquals(
                FrameworkRepairTerminalReasonClassifier.fromAcceptanceStatus(
                        AutomateFrameworkAcceptanceStatus.REVALIDATION_FAILED),
                FrameworkRepairTerminalReason.REVALIDATION_FAILURE);
        Assert.assertEquals(
                FrameworkRepairTerminalReasonClassifier.fromAcceptanceStatus(
                        AutomateFrameworkAcceptanceStatus.RECONCILIATION_FAILED),
                FrameworkRepairTerminalReason.RECONCILIATION_FAILURE);
    }
}