package delivery.job;

import delivery.excel.ManualTestCase;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

/** Fresh browser at each leaf-chain start; Call-before → leaf keep the same session. */
public class ProvePhaseSessionPolicyTest {

    private static ManualTestCase tc(String id, String callBefore) {
        return new ManualTestCase(id, id, "", "1. Go", "1. Ok", "P1", "", "", "", "EXECUTE", callBefore);
    }

    @Test
    public void selectedLoginThenDependentRestartsBetweenChainsOnly() {
        List<ManualTestCase> run = List.of(
                tc("TC_01", ""),
                tc("TC_01", ""),
                tc("TC_06", "TC_01"));
        // 1-based prove indices
        Assert.assertTrue(ProvePhase.wipeSessionBeforeTc(1, run));
        Assert.assertTrue(ProvePhase.wipeSessionBeforeTc(2, run));
        Assert.assertFalse(ProvePhase.wipeSessionBeforeTc(3, run));
    }

    @Test
    public void onlyCallBeforeLeafKeepsOneSession() {
        List<ManualTestCase> run = List.of(
                tc("TC_01", ""),
                tc("TC_06", "TC_01"));
        Assert.assertTrue(ProvePhase.wipeSessionBeforeTc(1, run));
        Assert.assertFalse(ProvePhase.wipeSessionBeforeTc(2, run));
    }

    @Test
    public void authOpenPathsAreDetected() {
        Assert.assertTrue(ProvePhase.isAuthOpenPath("/login"));
        Assert.assertTrue(ProvePhase.isAuthOpenPath("/signin"));
        Assert.assertFalse(ProvePhase.isAuthOpenPath("/users"));
        Assert.assertFalse(ProvePhase.isAuthOpenPath(null));
    }
}
