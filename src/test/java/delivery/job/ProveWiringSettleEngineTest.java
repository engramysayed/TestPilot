package delivery.job;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

public class ProveWiringSettleEngineTest {
    @AfterMethod
    public void clear() {
        System.clearProperty(ProveWiring.SETTLE_ENGINE);
        System.clearProperty(ProveWiring.EFFECT);
    }

    @Test
    public void autoFollowsEffectCheck() {
        System.clearProperty(ProveWiring.SETTLE_ENGINE);
        System.setProperty(ProveWiring.EFFECT, "off");
        assertFalse(ProveWiring.settleEngineActive());
        System.setProperty(ProveWiring.EFFECT, "log");
        assertTrue(ProveWiring.settleEngineActive());
        assertFalse(ProveWiring.settleEngineStrict());
    }

    @Test
    public void offWinsEvenWhenEffectOn() {
        System.setProperty(ProveWiring.EFFECT, "strict");
        System.setProperty(ProveWiring.SETTLE_ENGINE, "off");
        assertFalse(ProveWiring.settleEngineActive());
    }

    @Test
    public void onIndependentOfEffect() {
        System.setProperty(ProveWiring.EFFECT, "off");
        System.setProperty(ProveWiring.SETTLE_ENGINE, "on");
        assertTrue(ProveWiring.settleEngineActive());
        assertFalse(ProveWiring.settleEngineStrict());
    }

    @Test
    public void strictMode() {
        System.setProperty(ProveWiring.SETTLE_ENGINE, "strict");
        assertTrue(ProveWiring.settleEngineActive());
        assertTrue(ProveWiring.settleEngineStrict());
    }
}
