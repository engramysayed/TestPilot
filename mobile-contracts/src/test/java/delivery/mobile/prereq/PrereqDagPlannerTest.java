package delivery.mobile.prereq;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

public class PrereqDagPlannerTest {

    private CaseNode node(
            String id, List<String> before, String auth, String start, boolean neg, boolean sel) {
        return new CaseNode(id, before, auth, start, neg, sel);
    }

    @Test
    public void singleSelectedLeaf_onlyLeafAndAuth() {
        List<CaseNode> catalog = List.of(
                node("TC-LEAF", List.of(), "auth-1", "", false, true));
        LeafSetupPlan plan = PrereqDagPlanner.planLeaf(catalog, "TC-LEAF", "APP-BASE");
        Assert.assertTrue(plan.accepted(), plan.message());
        Assert.assertEquals(plan.steps().get(0).phase(), SetupPhaseKind.APP_BASELINE);
        Assert.assertEquals(plan.steps().get(1).phase(), SetupPhaseKind.AUTH_POLICY);
        Assert.assertEquals(plan.steps().get(plan.steps().size() - 1).phase(), SetupPhaseKind.LEAF);
        Assert.assertEquals(plan.steps().get(plan.steps().size() - 1).tcId(), "TC-LEAF");
        Assert.assertEquals(plan.steps().stream().filter(s -> s.phase() == SetupPhaseKind.PREREQUISITE).count(), 0);
    }

    @Test
    public void transitiveSharedPrerequisites_oncePerLeaf() {
        List<CaseNode> catalog = List.of(
                node("TC-A", List.of(), "auth-1", "", false, false),
                node("TC-B", List.of("TC-A"), "auth-1", "", false, false),
                node("TC-C", List.of("TC-A"), "auth-1", "", false, false),
                node("TC-LEAF", List.of("TC-B", "TC-C"), "auth-1", "SS-1", false, true));
        LeafSetupPlan plan = PrereqDagPlanner.planLeaf(catalog, "TC-LEAF", "");
        Assert.assertTrue(plan.accepted(), plan.message());
        List<String> prereqs = plan.steps().stream()
                .filter(s -> s.phase() == SetupPhaseKind.PREREQUISITE)
                .map(SetupPlanStep::tcId)
                .toList();
        Assert.assertEquals(prereqs.stream().filter(id -> id.equals("TC-A")).count(), 1L);
        Assert.assertTrue(prereqs.contains("TC-B"));
        Assert.assertTrue(prereqs.contains("TC-C"));
        Assert.assertTrue(prereqs.indexOf("TC-A") < prereqs.indexOf("TC-B"));
        Assert.assertTrue(plan.steps().stream().anyMatch(s ->
                s.phase() == SetupPhaseKind.START_STATE && s.tcId().equals("SS-1")));
    }

    @Test
    public void cycle_rejected() {
        List<CaseNode> catalog = List.of(
                node("TC-A", List.of("TC-B"), "auth-1", "", false, false),
                node("TC-B", List.of("TC-A"), "auth-1", "", false, false),
                node("TC-LEAF", List.of("TC-A"), "auth-1", "", false, true));
        LeafSetupPlan plan = PrereqDagPlanner.planLeaf(catalog, "TC-LEAF", "");
        Assert.assertFalse(plan.accepted());
        Assert.assertEquals(plan.failureCode(), PrereqDagPlanner.CALL_BEFORE_CYCLE);
    }

    @Test
    public void missingRefs_rejected() {
        List<CaseNode> catalog = List.of(
                node("TC-LEAF", List.of("TC-MISSING"), "auth-1", "", false, true));
        LeafSetupPlan plan = PrereqDagPlanner.planLeaf(catalog, "TC-LEAF", "");
        Assert.assertFalse(plan.accepted());
        Assert.assertEquals(plan.failureCode(), PrereqDagPlanner.CALL_BEFORE_MISSING);
        Assert.assertTrue(plan.message().contains("TC-MISSING"));
    }

    @Test
    public void conflictingAccounts_rejected() {
        List<CaseNode> catalog = List.of(
                node("TC-A", List.of(), "auth-alice", "", false, false),
                node("TC-LEAF", List.of("TC-A"), "auth-bob", "", false, true));
        LeafSetupPlan plan = PrereqDagPlanner.planLeaf(catalog, "TC-LEAF", "");
        Assert.assertFalse(plan.accepted());
        Assert.assertEquals(plan.failureCode(), PrereqDagPlanner.AUTH_PROFILE_CONFLICT);
    }

    @Test
    public void negativeLoginCannotBeSetup() {
        List<CaseNode> catalog = List.of(
                node("TC-BAD-LOGIN", List.of(), "auth-1", "", true, false),
                node("TC-LEAF", List.of("TC-BAD-LOGIN"), "auth-1", "", false, true));
        LeafSetupPlan plan = PrereqDagPlanner.planLeaf(catalog, "TC-LEAF", "");
        Assert.assertFalse(plan.accepted());
        Assert.assertEquals(plan.failureCode(), PrereqDagPlanner.NEGATIVE_LOGIN_SETUP);
    }

    @Test
    public void independentCases_separatePlansResetState() {
        List<CaseNode> catalog = new ArrayList<>();
        catalog.add(node("TC-SHARED", List.of(), "auth-1", "SS-RESET", false, false));
        catalog.add(node("TC-L1", List.of("TC-SHARED"), "auth-1", "", false, true));
        catalog.add(node("TC-L2", List.of("TC-SHARED"), "auth-1", "", false, true));
        List<LeafSetupPlan> plans = PrereqDagPlanner.planSelected(catalog, "APP");
        Assert.assertEquals(plans.size(), 2);
        Assert.assertTrue(plans.get(0).accepted());
        Assert.assertTrue(plans.get(1).accepted());
        // each leaf plan includes shared prereq independently (reset declared state per leaf)
        for (LeafSetupPlan p : plans) {
            long shared = p.steps().stream()
                    .filter(s -> s.phase() == SetupPhaseKind.PREREQUISITE && s.tcId().equals("TC-SHARED"))
                    .count();
            Assert.assertEquals(shared, 1L);
            Assert.assertTrue(p.steps().stream().anyMatch(s ->
                    s.phase() == SetupPhaseKind.START_STATE && s.tcId().equals("SS-RESET")));
        }
        Assert.assertNotEquals(plans.get(0).leafTcId(), plans.get(1).leafTcId());
    }
}
