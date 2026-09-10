package delivery.job;

import delivery.excel.ManualTestCase;
import org.testng.Assert;
import org.testng.annotations.Test;
import java.util.List;

public class CallBeforeExpanderTest {
    private static ManualTestCase tc(String id, String callBefore) {
        return new ManualTestCase(id, id, "", "1. Go", "1. Ok", "P1", "", "", "", "AUTOMATE", callBefore);
    }

    @Test
    public void skipsLeafWhenCallBeforeDidNotPass() {
        Assert.assertTrue(ProvePhase.callBeforeBlocked(
                tc("TC_06", "TC_01"), java.util.Set.of("TC_01")));
        Assert.assertFalse(ProvePhase.callBeforeBlocked(
                tc("TC_06", "TC_01"), java.util.Set.of()));
        Assert.assertFalse(ProvePhase.callBeforeBlocked(
                tc("TC_01", ""), java.util.Set.of("TC_99")));
    }

    @Test
    public void expandsRecursiveAndRerunsCallBeforeBeforeEachLeaf() {
        // Selecting Login + dependent leaf: Login as its own case, then Login again before the leaf.
        List<ManualTestCase> all = List.of(
                tc("TC_00", ""),
                tc("TC_01", "TC_00"),
                tc("TC_05", "TC_01"),
                tc("TC_06", "TC_01"));
        List<ManualTestCase> out = CallBeforeExpander.expand(all, List.of("TC_05", "TC_06"));
        Assert.assertEquals(out.stream().map(ManualTestCase::tcId).toList(),
                List.of("TC_00", "TC_01", "TC_05", "TC_00", "TC_01", "TC_06"));
    }

    @Test
    public void selectedLoginThenDependentRerunsLoginInChain() {
        List<ManualTestCase> all = List.of(
                tc("TC_01", ""),
                tc("TC_06", "TC_01"));
        List<ManualTestCase> out = CallBeforeExpander.expand(all, List.of("TC_01", "TC_06"));
        Assert.assertEquals(out.stream().map(ManualTestCase::tcId).toList(),
                List.of("TC_01", "TC_01", "TC_06"));
        Assert.assertEquals(CallBeforeExpander.freshSessionAt(out),
                List.of(true, true, false));
    }

    @Test
    public void cycleFails() {
        List<ManualTestCase> all = List.of(tc("TC_A", "TC_B"), tc("TC_B", "TC_A"));
        try {
            CallBeforeExpander.expand(all, List.of("TC_A"));
            Assert.fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException ex) {
            Assert.assertTrue(
                    ex.getMessage().startsWith("CALL_BEFORE_CYCLE:"),
                    "message: " + ex.getMessage());
        }
    }

    @Test
    public void unknownLeafFails() {
        List<ManualTestCase> all = List.of(tc("TC_01", ""));
        try {
            CallBeforeExpander.expand(all, List.of("TC_99"));
            Assert.fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException ex) {
            Assert.assertTrue(
                    ex.getMessage().startsWith("UNKNOWN_TC:"),
                    "message: " + ex.getMessage());
        }
    }

    @Test
    public void unknownCallBeforeFails() {
        List<ManualTestCase> all = List.of(tc("TC_01", "TC_99"));
        try {
            CallBeforeExpander.expand(all, List.of("TC_01"));
            Assert.fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException ex) {
            Assert.assertTrue(
                    ex.getMessage().startsWith("UNKNOWN_CALL_BEFORE:"),
                    "message: " + ex.getMessage());
        }
    }

    @Test
    public void withinLeafDedupesOverlappingNest() {
        // TC_06 → TC_01,TC_00 and TC_01 → TC_00  ⇒ once TC_00 then TC_01 then TC_06
        List<ManualTestCase> all = List.of(
                tc("TC_00", ""),
                tc("TC_01", "TC_00"),
                tc("TC_06", "TC_01,TC_00"));
        List<ManualTestCase> out = CallBeforeExpander.expand(all, List.of("TC_06"));
        Assert.assertEquals(out.stream().map(ManualTestCase::tcId).toList(),
                List.of("TC_00", "TC_01", "TC_06"));
    }
}
