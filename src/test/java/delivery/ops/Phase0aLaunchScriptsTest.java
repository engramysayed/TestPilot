package delivery.ops;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/** B17: Phase 0a manual launch scripts stay within the company-machine constraint (SPLIT_DESIGN §10.0). */
public class Phase0aLaunchScriptsTest {

    private static String read(String name) throws Exception {
        return Files.readString(Path.of(name));
    }

    @Test
    public void scriptsExistAndUseOnlyTheExistingPort() throws Exception {
        String portal = read("start-portal.cmd");
        String engine = read("start-engine.cmd");
        String keel = read("start-keel.cmd");
        Assert.assertTrue(portal.contains("KEEL_PORT=8080"));
        Assert.assertTrue(engine.contains("KEEL_PORTAL_URL=http://localhost:8080"));
        Assert.assertTrue(keel.contains("start \"Keel portal :8080\"") && keel.contains("start-portal.cmd"));
        Assert.assertTrue(keel.contains("start \"Keel engine\"") && keel.contains("start-engine.cmd"));
        Assert.assertFalse(engine.toLowerCase(Locale.ROOT).contains("--token"), "token must come from the env, not argv");
        // B07: the v2 EngineAgent is the default; the v1 agent only behind KEEL_ENGINE_LEGACY=1
        Assert.assertTrue(engine.contains("-Dexec.mainClass=delivery.runner.EngineAgent"));
        Assert.assertTrue(engine.contains("KEEL_ENGINE_LEGACY") && engine.contains("delivery.runner.PrivateRunnerAgent"));
    }

    @Test
    public void noInfraOnTheCompanyMachine() throws Exception {
        List<String> banned = List.of("sc create", "new-service", "winsw", "netsh", "new-netfirewallrule",
                "certutil", "import-certificate", "keytool", "squid", "--proxy-server", "https.proxyhost",
                "tailscale", "wireguard", "forticlient", "8443", "icacls", "schtasks");
        for (String f : List.of("start-portal.cmd", "start-engine.cmd", "start-keel.cmd")) {
            String text = read(f).toLowerCase(Locale.ROOT);
            for (String b : banned) {
                Assert.assertFalse(text.contains(b), f + " must not contain '" + b + "' (Phase 0a: no infra)");
            }
        }
    }

    @Test
    public void portalScriptOnlyStopsAPreviousKeelJava() throws Exception {
        String portal = read("start-portal.cmd");
        Assert.assertTrue(portal.contains("IMAGENAME eq java.exe"), "must not kill non-java listeners on 8080");
    }
}
