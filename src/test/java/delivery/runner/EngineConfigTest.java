package delivery.runner;

import org.testng.annotations.Test;

import java.nio.file.Path;
import java.util.Map;

import static org.testng.Assert.*;

/** B07: engine config defaults and the startup guard (no portal storage, token required). */
public class EngineConfigTest {
    static final Map<String, String> ENV = Map.of("KEEL_RUNNER_TOKEN", "tp_run_abc", "KEEL_PORTAL_URL", "http://localhost:8080");

    @Test
    public void portalUrlIsRequiredNoLocalDefault() {
        IllegalStateException e = org.testng.Assert.expectThrows(IllegalStateException.class,
                () -> EngineConfig.from(new String[0], Map.of("KEEL_RUNNER_TOKEN", "tp_run_abc")));
        assertTrue(e.getMessage().contains("keel.engine.portal-url"));
    }

    @Test
    public void defaultsToLocalhost8080AndMasksToken() {
        EngineConfig c = EngineConfig.from(new String[0], ENV);
        assertEquals(c.portalUrl().toString(), "http://localhost:8080");
        assertTrue(c.dryRun());
        assertEquals(c.workDir(), Path.of("./engine-home", "work"));
        assertFalse(c.toString().contains("tp_run_abc"));
    }

    @Test
    public void argsBeatEnv() {
        EngineConfig c = EngineConfig.from(new String[]{"--keel.engine.portal-url=http://localhost:8080/",
                "--keel.engine.home=/tmp/eh", "--keel.engine.dry-run=false"}, ENV);
        assertEquals(c.portalUrl().toString(), "http://localhost:8080");
        assertEquals(c.workDir(), Path.of("/tmp/eh", "work"));
        assertFalse(c.dryRun());
    }

    @Test
    public void refusesPortalStorageInArgsOrEnv() {
        for (String bad : new String[]{"--delivery.store-root=D:/keel", "--spring.datasource.url=jdbc:h2:file:x",
                "--keel.engine.work-dir=C:/Keel/delivery-store/work"}) {
            var e = expectThrows(IllegalStateException.class, () -> EngineConfig.from(new String[]{bad}, ENV));
            assertTrue(e.getMessage().startsWith("ENGINE_CONFIG_REFUSED"), bad);
        }
        expectThrows(IllegalStateException.class, () -> EngineConfig.from(new String[0],
                Map.of("KEEL_RUNNER_TOKEN", "tp_run_abc", "KEEL_ENGINE_HOME", "D:/delivery-store")));
    }

    @Test
    public void requiresRunnerTokenAndHttpUrl() {
        expectThrows(IllegalStateException.class, () -> EngineConfig.from(new String[0], Map.of()));
        expectThrows(IllegalStateException.class, () -> EngineConfig.from(new String[0], Map.of("KEEL_RUNNER_TOKEN", "svc_x")));
        expectThrows(IllegalStateException.class, () -> EngineConfig.from(
                new String[]{"--keel.engine.portal-url=file:///etc"}, ENV));
    }
}
