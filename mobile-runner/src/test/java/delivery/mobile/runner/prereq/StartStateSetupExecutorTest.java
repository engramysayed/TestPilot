package delivery.mobile.runner.prereq;

import delivery.mobile.prereq.CaseNode;
import delivery.mobile.prereq.LeafSetupPlan;
import delivery.mobile.prereq.PrereqDagPlanner;
import delivery.mobile.prereq.SetupPhaseKind;
import delivery.mobile.result.CaseOutcomeView;
import delivery.mobile.result.SetupOutcome;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class StartStateSetupExecutorTest {

    @Test
    public void setupFailureBlocksBody_neverPass() {
        List<CaseNode> catalog = List.of(
                new CaseNode("TC-PRE", List.of(), "auth-1", "", false, false),
                new CaseNode("TC-LEAF", List.of("TC-PRE"), "auth-1", "", false, true));
        LeafSetupPlan plan = PrereqDagPlanner.planLeaf(catalog, "TC-LEAF", "");
        Assert.assertTrue(plan.accepted());

        AtomicBoolean bodyAttempted = new AtomicBoolean(false);
        SetupStepRunner runner = step -> {
            if (step.phase() == SetupPhaseKind.PREREQUISITE) {
                return false; // setup fails
            }
            if (step.phase() == SetupPhaseKind.LEAF) {
                bodyAttempted.set(true);
            }
            return true;
        };
        LeafExecutionResult r = new StartStateSetupExecutor(runner).execute(plan, true);
        Assert.assertFalse(r.bodyExecuted());
        Assert.assertEquals(r.outcome(), CaseOutcomeView.BLOCKED);
        Assert.assertEquals(r.setupOutcome(), SetupOutcome.FAILED);
        Assert.assertEquals(r.failureCode(), "CALL_BEFORE_BLOCKED");
        Assert.assertFalse(bodyAttempted.get(), "body must not run after setup failure");
    }

    @Test
    public void successfulSetupRunsBody() {
        List<CaseNode> catalog = List.of(
                new CaseNode("TC-PRE", List.of(), "auth-1", "", false, false),
                new CaseNode("TC-LEAF", List.of("TC-PRE"), "auth-1", "", false, true));
        LeafSetupPlan plan = PrereqDagPlanner.planLeaf(catalog, "TC-LEAF", "APP");
        List<String> order = new ArrayList<>();
        SetupStepRunner runner = step -> {
            order.add(step.phase() + ":" + step.tcId());
            return true;
        };
        LeafExecutionResult r = new StartStateSetupExecutor(runner).execute(plan, true);
        Assert.assertTrue(r.bodyExecuted());
        Assert.assertEquals(r.outcome(), CaseOutcomeView.PASSED);
        Assert.assertEquals(r.setupOutcome(), SetupOutcome.PASSED);
        Assert.assertTrue(order.get(0).startsWith("APP_BASELINE"));
        Assert.assertTrue(order.stream().anyMatch(s -> s.startsWith("PREREQUISITE:TC-PRE")));
        Assert.assertTrue(order.get(order.size() - 1).equals("LEAF:TC-LEAF"));
    }

    @Test
    public void planRejectBlocksWithoutRunner() {
        LeafSetupPlan rejected = LeafSetupPlan.reject("X", PrereqDagPlanner.CALL_BEFORE_CYCLE, "cycle");
        LeafExecutionResult r = new StartStateSetupExecutor(step -> true).execute(rejected, true);
        Assert.assertFalse(r.bodyExecuted());
        Assert.assertEquals(r.outcome(), CaseOutcomeView.BLOCKED);
        Assert.assertEquals(r.failureCode(), PrereqDagPlanner.CALL_BEFORE_CYCLE);
    }

    @Test
    public void independentLeavesResetDeclaredState() {
        List<CaseNode> catalog = List.of(
                new CaseNode("TC-SHARED", List.of(), "auth-1", "SS", false, false),
                new CaseNode("TC-L1", List.of("TC-SHARED"), "auth-1", "", false, true),
                new CaseNode("TC-L2", List.of("TC-SHARED"), "auth-1", "", false, true));
        List<String> runs = new ArrayList<>();
        SetupStepRunner runner = step -> {
            runs.add(step.tcId());
            return true;
        };
        StartStateSetupExecutor exec = new StartStateSetupExecutor(runner);
        for (LeafSetupPlan p : PrereqDagPlanner.planSelected(catalog, "")) {
            exec.execute(p, true);
        }
        // shared prereq + start-state appear once per leaf
        Assert.assertEquals(runs.stream().filter(id -> id.equals("TC-SHARED")).count(), 2L);
        Assert.assertEquals(runs.stream().filter(id -> id.equals("SS")).count(), 2L);
        Assert.assertTrue(runs.contains("TC-L1"));
        Assert.assertTrue(runs.contains("TC-L2"));
    }
}
