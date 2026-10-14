package delivery.mobile.runner.prereq;

import delivery.mobile.prereq.LeafSetupPlan;
import delivery.mobile.result.CaseOutcomeView;
import delivery.mobile.result.SetupOutcome;

import java.util.List;

public record LeafExecutionResult(
        String leafTcId,
        boolean bodyExecuted,
        CaseOutcomeView outcome,
        SetupOutcome setupOutcome,
        String failureCode,
        LeafSetupPlan plan,
        List<String> executedTcIds
) {
    public LeafExecutionResult {
        leafTcId = leafTcId == null ? "" : leafTcId;
        failureCode = failureCode == null ? "" : failureCode;
        executedTcIds = executedTcIds == null ? List.of() : List.copyOf(executedTcIds);
        outcome = outcome == null ? CaseOutcomeView.TODO : outcome;
        setupOutcome = setupOutcome == null ? SetupOutcome.UNKNOWN : setupOutcome;
    }
}
