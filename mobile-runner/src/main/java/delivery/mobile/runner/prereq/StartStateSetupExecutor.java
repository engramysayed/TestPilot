package delivery.mobile.runner.prereq;

import delivery.mobile.prereq.LeafSetupPlan;
import delivery.mobile.prereq.PrereqDagPlanner;
import delivery.mobile.prereq.SetupPhaseKind;
import delivery.mobile.prereq.SetupPlanStep;
import delivery.mobile.result.CaseOutcomeView;
import delivery.mobile.result.SetupOutcome;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs a per-leaf setup closure; setup failure blocks the body (never PASS) (M3-03).
 * Independent leaves each get a fresh plan (declared state reset per leaf).
 */
public final class StartStateSetupExecutor {
    private final SetupStepRunner runner;

    public StartStateSetupExecutor(SetupStepRunner runner) {
        this.runner = runner;
    }

    public LeafExecutionResult execute(LeafSetupPlan plan, boolean leafBodySuccess) {
        if (plan == null || !plan.accepted()) {
            String code = plan == null ? PrereqDagPlanner.LEAF_MISSING : plan.failureCode();
            return new LeafExecutionResult(
                    plan == null ? "" : plan.leafTcId(),
                    false,
                    CaseOutcomeView.BLOCKED,
                    SetupOutcome.BLOCKED,
                    code,
                    plan,
                    List.of());
        }
        List<String> executed = new ArrayList<>();
        for (SetupPlanStep step : plan.steps()) {
            if (step.phase() == SetupPhaseKind.LEAF) {
                continue; // body separate
            }
            boolean ok = runner.run(step);
            executed.add(step.tcId());
            if (!ok) {
                return new LeafExecutionResult(
                        plan.leafTcId(),
                        false,
                        CaseOutcomeView.BLOCKED,
                        SetupOutcome.FAILED,
                        "CALL_BEFORE_BLOCKED",
                        plan,
                        executed);
            }
        }
        // body
        boolean bodyOk = leafBodySuccess;
        executed.add(plan.leafTcId());
        runner.run(new SetupPlanStep(SetupPhaseKind.LEAF, plan.leafTcId(), "", false));
        return new LeafExecutionResult(
                plan.leafTcId(),
                true,
                bodyOk ? CaseOutcomeView.PASSED : CaseOutcomeView.FAILED,
                SetupOutcome.PASSED,
                bodyOk ? "" : "LEAF_FAILED",
                plan,
                executed);
    }
}
