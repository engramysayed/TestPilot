package delivery.codegen;

import delivery.settle.BackgroundNoise;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * r3: every SettleVisualEffectLiveTest case again with BackgroundNoise running (company-machine symptom:
 * effect=EFFECT effectSignal=network where no effect is expected). Defaults (scope=page, network effect
 * corroborated) must give the same verdicts as a quiet page.
 */
public class SettleVisualEffectNoiseLiveTest extends SettleVisualEffectLiveTest {
    @Override protected void noise() {
        BackgroundNoise.inject(driver);
    }

    @Test
    public void legacyNetworkAloneReproducesTheSymptom() {
        System.setProperty("keel.noeffect", "warn");
        System.setProperty("keel.settle.network.scope", "all");
        System.setProperty("keel.settle.network.effect", "alone");
        String r = click("focusy", true, null);
        Assert.assertTrue(r.contains("effect=EFFECT, effectSignal=network"), "legacy settings + noise = false effect: " + r);
    }

    @Test
    public void networkAloneOptInStillIgnoresBackgroundTraffic() {
        System.setProperty("keel.noeffect", "warn");
        System.setProperty("keel.settle.network.effect", "alone");   // scope stays page
        String r = click("focusy", true, null);
        Assert.assertTrue(r.contains("effect=NO_EFFECT"), "page scope filters the noise: " + r);
    }
}
