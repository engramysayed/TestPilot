package delivery.it;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** B09: the parts of EngineIsolationIT that must hold on every machine (the IT itself runs in CI only). */
public class EngineIsolationGuardTest {
    @Test
    public void itIsGatedOnCiTrue() {
        Assert.assertTrue(EngineIsolationIT.ciEnabled(Map.of("CI", "true")));
        Assert.assertFalse(EngineIsolationIT.ciEnabled(Map.of()));
        Assert.assertFalse(EngineIsolationIT.ciEnabled(Map.of("CI", "false")));
    }

    @Test
    public void engineCommandCarriesOnlyUrlTokenAndOwnRoot() {
        List<String> cmd = EngineIsolationIT.engineCommand("http://portal.example:8080", Path.of("engine-root"));
        Assert.assertEquals(EngineIsolationIT.configViolations(cmd, Map.of("KEEL_RUNNER_TOKEN", "tp_run_x")), List.of());
        Assert.assertTrue(cmd.contains("--keel.engine.portal-url=http://portal.example:8080"));
    }

    @Test
    public void configGrepTripsOnPortalStorage() {
        List<String> cmd = EngineIsolationIT.engineCommand("http://portal.example:8080", Path.of("delivery-store"));
        Assert.assertFalse(EngineIsolationIT.configViolations(cmd, Map.of()).isEmpty());
        Assert.assertFalse(EngineIsolationIT.configViolations(
                EngineIsolationIT.engineCommand("u", Path.of("e")), Map.of("KEEL_X", "jdbc:h2:file:x")).isEmpty());
    }
}
