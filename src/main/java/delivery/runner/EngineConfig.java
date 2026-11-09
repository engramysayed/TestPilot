package delivery.runner;

import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * B07 engine configuration ({@code keel.engine.*}). Sources, highest first: {@code --key=value} args,
 * environment ({@code KEEL_PORTAL_URL}, {@code KEEL_RUNNER_TOKEN}, {@code KEEL_ENGINE_HOME}, {@code KEEL_RUNNER_DRY_RUN}),
 * defaults. Startup guard (SPLIT_DESIGN rev 10): the engine must never be pointed at portal storage, so any
 * value or key mentioning {@code delivery.store-root}, {@code jdbc:} or {@code delivery-store} is refused.
 */
public record EngineConfig(URI portalUrl, String token, Path workDir, Path templateRoot, boolean dryRun, long pollMillis, EngineProxy proxy) {
    static final List<String> FORBIDDEN = List.of("delivery.store-root", "jdbc:", "delivery-store", "spring.datasource");

    public static EngineConfig from(String[] args, Map<String, String> env) {
        Map<String, String> kv = new LinkedHashMap<>();
        List<String> problems = new ArrayList<>();
        for (String a : args == null ? new String[0] : args) {
            if (a.startsWith("--") && a.contains("=")) {
                kv.put(a.substring(2, a.indexOf('=')), a.substring(a.indexOf('=') + 1));
            }
            guard(a, problems);
        }
        for (var e : env.entrySet()) {
            if (e.getKey().startsWith("KEEL_")) {
                guard(e.getKey() + "=" + e.getValue(), problems);
            }
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException("ENGINE_CONFIG_REFUSED: engine must not reference portal storage: "
                    + String.join(", ", problems));
        }
        // B10: host-specific, so no local-only default; start-engine.cmd supplies the 0a value (http://localhost:8080).
        String portal = pick(kv, "keel.engine.portal-url", env, "KEEL_PORTAL_URL", "");
        String token = pick(kv, "keel.engine.token", env, "KEEL_RUNNER_TOKEN", "");
        String home = pick(kv, "keel.engine.home", env, "KEEL_ENGINE_HOME", "./engine-home");
        String work = pick(kv, "keel.engine.work-dir", env, "KEEL_ENGINE_WORK", Path.of(home, "work").toString());
        // B09: the framework template ships with the engine bundle (code, not portal data); relative to the engine install
        String template = pick(kv, "keel.engine.template-root", env, "KEEL_ENGINE_TEMPLATE_ROOT", "./customer-framework-template");
        boolean dry = Boolean.parseBoolean(pick(kv, "keel.engine.dry-run", env, "KEEL_RUNNER_DRY_RUN", "true"));
        long poll = Long.parseLong(pick(kv, "keel.engine.poll-ms", env, "KEEL_ENGINE_POLL_MS", "2000"));
        if (token.isBlank() || !token.startsWith("tp_run_")) {
            throw new IllegalStateException("ENGINE_CONFIG_REFUSED: KEEL_RUNNER_TOKEN (tp_run_...) is required");
        }
        if (portal.isBlank()) {
            throw new IllegalStateException("ENGINE_CONFIG_REFUSED: keel.engine.portal-url (KEEL_PORTAL_URL) is required");
        }
        URI uri = URI.create(portal.endsWith("/") ? portal.substring(0, portal.length() - 1) : portal);
        if (!"http".equals(uri.getScheme()) && !"https".equals(uri.getScheme())) {
            throw new IllegalStateException("ENGINE_CONFIG_REFUSED: keel.engine.portal-url must be http(s)");
        }
        // Plain HTTP to a non-loopback portal would send the tp_run_ bearer in clear text.
        // Loopback stays legal (same-machine 0a install over http://127.0.0.1:8080).
        if ("http".equals(uri.getScheme())) {
            String host = uri.getHost() == null ? "" : uri.getHost();
            boolean loopback = switch (host) {
                case "127.0.0.1", "localhost", "::1", "[::1]" -> true;
                default -> false;
            };
            if (!loopback) {
                throw new IllegalStateException(
                        "ENGINE_CONFIG_REFUSED: keel.engine.portal-url points off-host (" + host
                                + ") over plain http; use https to avoid leaking the runner bearer");
            }
        }
        // B11: proxy wiring, off unless keel.engine.proxy.enabled=true (KEEL_ENGINE_PROXY_ENABLED)
        EngineProxy proxy = EngineProxy.from(k -> pick(kv, k, env,
                "KEEL_ENGINE_PROXY_" + k.substring(EngineProxy.PREFIX.length()).replace('-', '_').toUpperCase(Locale.ROOT),
                System.getProperty(k, "")), uri);
        return new EngineConfig(uri, token, Path.of(work), Path.of(template), dry, poll, proxy);
    }

    private static void guard(String s, List<String> problems) {
        String l = s.toLowerCase(Locale.ROOT);
        for (String f : FORBIDDEN) {
            if (l.contains(f)) {
                problems.add(f);
            }
        }
    }

    private static String pick(Map<String, String> kv, String key, Map<String, String> env, String envKey, String def) {
        String v = kv.get(key);
        if (v == null || v.isBlank()) {
            v = env.get(envKey);
        }
        return v == null || v.isBlank() ? def : v.trim();
    }

    @Override
    public String toString() {
        return "EngineConfig[portal=" + portalUrl + ", workDir=" + workDir + ", dryRun=" + dryRun + ", proxy=" + (proxy.enabled() ? proxy.host() + ":" + proxy.port() : "off") + ", token=***]";
    }
}
