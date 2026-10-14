package delivery.runner;

import org.openqa.selenium.chrome.ChromeOptions;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/** B11: proxy wiring is off by default and produces the right Java / Node / Chrome settings when enabled (not exercised). */
public class EngineProxyTest {
    static final Map<String, String> TOKEN = Map.of("KEEL_RUNNER_TOKEN", "tp_run_abc", "KEEL_PORTAL_URL", "http://portal.example:8080");

    private static EngineProxy on(String nonProxy) {
        Map<String, String> m = new java.util.HashMap<>(Map.of("keel.engine.proxy.enabled", "true",
                "keel.engine.proxy.host", "squid.example", "keel.engine.proxy.port", "3128"));
        if (nonProxy != null) m.put("keel.engine.proxy.non-proxy-hosts", nonProxy);
        return EngineProxy.from(m::get, URI.create("http://portal.example:8080"));
    }

    @Test
    public void offByDefaultEverywhere() {
        EngineConfig c = EngineConfig.from(new String[0], TOKEN);
        Assert.assertFalse(c.proxy().enabled());
        Assert.assertEquals(c.proxy().javaProperties(), Map.of());
        Assert.assertEquals(c.proxy().nodeEnv(), Map.of());
        Assert.assertEquals(c.proxy().chromeArgs(List.of()), List.of());
        Properties sys = new Properties();
        c.proxy().publish(sys, c.portalUrl());
        Assert.assertTrue(sys.isEmpty(), "disabled proxy must not touch JVM properties");
        Assert.assertTrue(c.toString().contains("proxy=off"));
    }

    @Test
    public void javaPropertiesIncludeNonProxyHostsAndPortalHost() {
        EngineProxy p = on("internal.example|10.0.0.5");
        Map<String, String> j = p.javaProperties();
        Assert.assertEquals(j.get("https.proxyHost"), "squid.example");
        Assert.assertEquals(j.get("https.proxyPort"), "3128");
        Assert.assertEquals(j.get("http.proxyHost"), "squid.example");
        Assert.assertEquals(j.get("http.nonProxyHosts"), "internal.example|10.0.0.5|portal.example");
        Properties sys = new Properties();
        p.publish(sys, URI.create("http://portal.example:8080"));
        Assert.assertEquals(sys.getProperty("https.proxyHost"), "squid.example");
        Assert.assertEquals(sys.getProperty("keel.engine.proxy.enabled"), "true");
    }

    @Test
    public void nodeEnvUsesEnvProxy() {
        Map<String, String> e = on(null).nodeEnv();
        Assert.assertEquals(e.get("NODE_USE_ENV_PROXY"), "1");
        Assert.assertEquals(e.get("HTTPS_PROXY"), "http://squid.example:3128");
        Assert.assertEquals(e.get("HTTP_PROXY"), "http://squid.example:3128");
        Assert.assertEquals(e.get("NO_PROXY"), "portal.example");
    }

    @Test
    public void chromeGetsProxyServerWithoutLoopbackBypassUnlessPacPresent() {
        EngineProxy p = on(null);
        Assert.assertEquals(p.chromeArgs(List.of("--headless=new")),
                List.of("--proxy-server=http://squid.example:3128", "--proxy-bypass-list=<-loopback>"));
        Assert.assertEquals(p.chromeArgs(List.of("--proxy-pac-url=file:///x.pac")), List.of(), "worker PAC wins");
    }

    @Test
    public void chromeFactoryAppliesProxyOnlyWhenPublished() {
        String[] keys = {"keel.engine.proxy.enabled", "keel.engine.proxy.host", "keel.engine.proxy.port",
                "keel.engine.proxy.non-proxy-hosts", "keel.engine.portal-url"};
        Map<String, String> saved = new java.util.HashMap<>();
        for (String k : keys) if (System.getProperty(k) != null) saved.put(k, System.getProperty(k));
        try {
            for (String k : keys) System.clearProperty(k);
            Assert.assertTrue(drivers.ChromeFactoryProxyProbe.args().stream().noneMatch(a -> a.startsWith("--proxy-server")));
            System.setProperty("keel.engine.proxy.enabled", "true");
            System.setProperty("keel.engine.proxy.host", "squid.example");
            System.setProperty("keel.engine.proxy.port", "3128");
            Assert.assertTrue(drivers.ChromeFactoryProxyProbe.args().contains("--proxy-server=http://squid.example:3128"));
        } finally {
            for (String k : keys) System.clearProperty(k);
            saved.forEach(System::setProperty);
        }
    }

    @Test
    public void configArgsAndEnvEnableItAndBadSettingsRefuseStartup() {
        EngineConfig c = EngineConfig.from(new String[]{"--keel.engine.proxy.enabled=true",
                "--keel.engine.proxy.host=squid.example"}, Map.of("KEEL_RUNNER_TOKEN", "tp_run_abc",
                "KEEL_PORTAL_URL", "http://portal.example:8080", "KEEL_ENGINE_PROXY_PORT", "3128"));
        Assert.assertTrue(c.proxy().enabled());
        Assert.assertEquals(c.proxy().port(), 3128);
        Assert.assertTrue(c.proxy().nonProxyHosts().contains("portal.example"));
        IllegalStateException e = Assert.expectThrows(IllegalStateException.class, () -> EngineConfig.from(
                new String[]{"--keel.engine.proxy.enabled=true"}, TOKEN));
        Assert.assertTrue(e.getMessage().contains("keel.engine.proxy.host"));
    }

    @Test
    public void chromeOptionsProbeReadsArgs() {
        ChromeOptions o = new ChromeOptions();
        o.addArguments("--a", "--b");
        Assert.assertEquals(drivers.ChromeFactoryProxyProbe.argsOf(o), List.of("--a", "--b"));
    }
}
