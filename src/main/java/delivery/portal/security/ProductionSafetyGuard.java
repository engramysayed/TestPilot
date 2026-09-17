package delivery.portal.security;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Fail-closed production configuration checks. Development keeps local defaults.
 */
public final class ProductionSafetyGuard {
    public static final String DEFAULT_ADMIN_PASSWORD = "ChangeMeAdmin1!";

    public record Config(
            boolean production,
            String adminPassword,
            String publicBaseUrl,
            boolean h2ConsoleEnabled,
            String providerAllowlist,
            String installMode,
            String datasourcePassword,
            boolean httpsPublicUrl,
            String privateCidrs
    ) {
    }

    private ProductionSafetyGuard() {
    }

    public static List<String> violations(Config config) {
        if (config == null || !config.production()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        String password = config.adminPassword() == null ? "" : config.adminPassword();
        if (password.isBlank() || DEFAULT_ADMIN_PASSWORD.equals(password)) {
            out.add("default admin password is not allowed in production");
        }
        String url = config.publicBaseUrl() == null ? "" : config.publicBaseUrl().trim();
        boolean https = config.httpsPublicUrl() || url.toLowerCase(Locale.ROOT).startsWith("https://");
        if (!https) {
            out.add("production public URL must use https");
        }
        if (config.h2ConsoleEnabled()) {
            out.add("H2 console must be disabled in production");
        }
        if (config.providerAllowlist() == null || config.providerAllowlist().isBlank()) {
            out.add("provider allowlist must be explicit in production");
        }
        if (config.datasourcePassword() == null || config.datasourcePassword().isBlank()) {
            out.add("production datasource password must not be blank");
        }
        String mode = config.installMode() == null ? "shared" : config.installMode().trim().toLowerCase(Locale.ROOT);
        String cidrs = config.privateCidrs() == null ? "" : config.privateCidrs().trim();
        if ("shared".equals(mode) && !cidrs.isBlank()) {
            out.add("delivery.install.private-cidrs must not be set in shared mode");
        }
        if (!"shared".equals(mode) && !"dedicated".equals(mode)) {
            out.add("install mode must be shared or dedicated");
        }
        return List.copyOf(out);
    }

    public static void requireSafe(Config config) {
        List<String> found = violations(config);
        if (!found.isEmpty()) {
            throw new IllegalStateException("INSECURE_PRODUCTION: " + String.join("; ", found));
        }
    }
}
