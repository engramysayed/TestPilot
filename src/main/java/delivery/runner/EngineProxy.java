package delivery.runner;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;

/**
 * B11 (OPS §12.5 Part A item 4, SPLIT_DESIGN rev 10): engine outbound through a forward proxy (Squid in 0b).
 * <b>Off by default</b> ({@code keel.engine.proxy.enabled=false}) and not exercised in 0a; only unit-tested.
 *
 * <p>Keys ({@code --key=value} arg, then env, then JVM system property): {@code keel.engine.proxy.enabled}
 * ({@code KEEL_ENGINE_PROXY_ENABLED}), {@code keel.engine.proxy.host}, {@code keel.engine.proxy.port},
 * {@code keel.engine.proxy.non-proxy-hosts} ({@code |}-separated). The portal host from {@code keel.engine.portal-url}
 * is always added to the bypass list, so runner API traffic never goes through the proxy. No host has a default.
 *
 * <ul>
 *   <li>Java: {@code http(s).proxyHost/proxyPort} + {@code http.nonProxyHosts}.</li>
 *   <li>Node (heal.mjs): {@code NODE_USE_ENV_PROXY=1} (Node 24+) + {@code HTTPS_PROXY/HTTP_PROXY/NO_PROXY}
 *       (an undici {@code ProxyAgent} reads the same variables).</li>
 *   <li>Chrome: {@code --proxy-server=http://host:port} + {@code --proxy-bypass-list=<-loopback>} (pages get no
 *       implicit loopback bypass). A per-job worker PAC ({@code --proxy-pac-url}) takes precedence.</li>
 * </ul>
 */
public record EngineProxy(boolean enabled, String host, int port, List<String> nonProxyHosts) {
    public static final String PREFIX = "keel.engine.proxy.";
    public static final EngineProxy OFF = new EngineProxy(false, "", 0, List.of());

    public EngineProxy {
        nonProxyHosts = List.copyOf(nonProxyHosts);
        if (enabled && (host == null || host.isBlank() || port <= 0 || port > 65535)) {
            throw new IllegalStateException("ENGINE_CONFIG_REFUSED: keel.engine.proxy.enabled=true needs "
                    + "keel.engine.proxy.host and keel.engine.proxy.port");
        }
    }

    /** @param lookup key -> value (already merged from args/env/system properties) */
    public static EngineProxy from(Function<String, String> lookup, URI portalUrl) {
        if (!Boolean.parseBoolean(trim(lookup.apply(PREFIX + "enabled")))) {
            return OFF;
        }
        String host = trim(lookup.apply(PREFIX + "host"));
        String portRaw = trim(lookup.apply(PREFIX + "port"));
        int port;
        try {
            port = portRaw.isEmpty() ? 0 : Integer.parseInt(portRaw);
        } catch (NumberFormatException e) {
            port = 0;
        }
        Set<String> bypass = new LinkedHashSet<>();
        for (String h : trim(lookup.apply(PREFIX + "non-proxy-hosts")).split("[|,]")) {
            if (!h.isBlank()) bypass.add(h.trim());
        }
        if (portalUrl != null && portalUrl.getHost() != null) {
            bypass.add(portalUrl.getHost());
        }
        return new EngineProxy(true, host, port, new ArrayList<>(bypass));
    }

    /** The engine process publishes its proxy as system properties so Chrome/heal launchers in the same JVM see it. */
    public static EngineProxy fromSystem() {
        String portal = System.getProperty("keel.engine.portal-url", "");
        return from(System::getProperty, portal.isBlank() ? null : URI.create(portal));
    }

    public Map<String, String> javaProperties() {
        if (!enabled) return Map.of();
        Map<String, String> p = new LinkedHashMap<>();
        p.put("http.proxyHost", host);
        p.put("http.proxyPort", String.valueOf(port));
        p.put("https.proxyHost", host);
        p.put("https.proxyPort", String.valueOf(port));
        if (!nonProxyHosts.isEmpty()) p.put("http.nonProxyHosts", String.join("|", nonProxyHosts));
        return p;
    }

    public Map<String, String> nodeEnv() {
        if (!enabled) return Map.of();
        Map<String, String> e = new LinkedHashMap<>();
        String url = "http://" + host + ":" + port;
        e.put("NODE_USE_ENV_PROXY", "1");
        e.put("HTTPS_PROXY", url);
        e.put("HTTP_PROXY", url);
        if (!nonProxyHosts.isEmpty()) e.put("NO_PROXY", String.join(",", nonProxyHosts));
        return e;
    }

    public List<String> chromeArgs(List<String> existingArgs) {
        if (!enabled) return List.of();
        if (existingArgs != null && existingArgs.stream().anyMatch(a -> a.startsWith("--proxy-pac-url=")
                || a.startsWith("--proxy-server="))) {
            return List.of();
        }
        return List.of("--proxy-server=http://" + host + ":" + port, "--proxy-bypass-list=<-loopback>");
    }

    /** Engine startup: Java proxy properties + the keys themselves (for {@link #fromSystem()}). */
    public void publish(Properties sys, URI portalUrl) {
        javaProperties().forEach(sys::setProperty);
        if (enabled) {
            sys.setProperty(PREFIX + "enabled", "true");
            sys.setProperty(PREFIX + "host", host);
            sys.setProperty(PREFIX + "port", String.valueOf(port));
            sys.setProperty(PREFIX + "non-proxy-hosts", String.join("|", nonProxyHosts));
            if (portalUrl != null) sys.setProperty("keel.engine.portal-url", portalUrl.toString());
        }
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }
}
