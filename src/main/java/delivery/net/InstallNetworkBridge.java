package delivery.net;

import org.springframework.core.env.Environment;

/**
 * Copies Spring {@code delivery.install.mode} / {@code private-cidrs} into the
 * system properties {@link TargetNetworkPolicy#forJob} reads. Those properties
 * are <strong>installation-wide and JVM-wide</strong>: they must not vary by
 * tenant or job. Shared and dedicated installs therefore belong in separate
 * processes.
 */
public final class InstallNetworkBridge {
    private static final String[] FORBIDDEN_SCOPED_KEYS = {
            "delivery.job.install.mode",
            "delivery.job.install.private-cidrs",
            "delivery.tenant.install.mode",
            "delivery.tenant.install.private-cidrs"
    };

    private InstallNetworkBridge() {
    }

    public static void apply(Environment env) {
        rejectScopedOverrides(env);
        if (env == null) {
            apply("shared", "");
            return;
        }
        apply(env.getProperty("delivery.install.mode", "shared"),
                env.getProperty("delivery.install.private-cidrs", ""));
    }

    public static void apply(String mode, String privateCidrs) {
        String resolved = mode == null || mode.isBlank() ? "shared" : mode.trim();
        System.setProperty("delivery.install.mode", resolved);
        if (privateCidrs == null || privateCidrs.isBlank()) {
            System.clearProperty("delivery.install.private-cidrs");
        } else {
            System.setProperty("delivery.install.private-cidrs", privateCidrs.trim());
        }
    }

    static void rejectScopedOverrides(Environment env) {
        if (env == null) {
            return;
        }
        for (String key : FORBIDDEN_SCOPED_KEYS) {
            String value = env.getProperty(key);
            if (value != null && !value.isBlank()) {
                throw new IllegalStateException(
                        key + " is not allowed; install network policy is JVM-wide");
            }
        }
    }
}
