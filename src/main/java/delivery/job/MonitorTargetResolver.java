package delivery.job;

import java.util.Locale;
import java.util.Map;

/**
 * Phase 3 AE: when suite trigger=monitor, select env/base URL from {@code monitor.target}
 * ({@code prod}|{@code staging}). Exact env map is AE-owned; missing mapping keeps defaultBaseUrl.
 */
public final class MonitorTargetResolver {
    public static final String PROD = "prod";
    public static final String STAGING = "staging";

    private MonitorTargetResolver() {
    }

    /**
     * @param target monitor.target (prod|staging)
     * @param defaultBaseUrl job/project base URL
     * @param envTargets optional map prod/staging → base URL (may be null)
     */
    public static String resolveBaseUrl(String target, String defaultBaseUrl, Map<String, String> envTargets) {
        if (target == null || target.isBlank()) {
            return defaultBaseUrl;
        }
        String key = target.trim().toLowerCase(Locale.ROOT);
        if (envTargets != null) {
            String mapped = envTargets.get(key);
            if (mapped != null && !mapped.isBlank()) {
                return mapped.trim();
            }
            // also accept KEEL-style keys
            mapped = envTargets.get("keel.monitor." + key + ".base-url");
            if (mapped != null && !mapped.isBlank()) {
                return mapped.trim();
            }
        }
        String prop = System.getProperty("keel.monitor." + key + ".base-url", "");
        if (!prop.isBlank()) {
            return prop.trim();
        }
        String env = System.getenv("KEEL_MONITOR_" + key.toUpperCase(Locale.ROOT) + "_BASE_URL");
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        return defaultBaseUrl;
    }

    public static String resolveForActiveContext(String defaultBaseUrl, Map<String, String> envTargets) {
        SuiteRunContext ctx = SuiteRunContext.current();
        if (ctx == null || !ctx.isMonitor()) {
            return defaultBaseUrl;
        }
        return resolveBaseUrl(ctx.monitorTarget(), defaultBaseUrl, envTargets);
    }
}
