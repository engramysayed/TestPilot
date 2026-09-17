package delivery.net;

import org.springframework.core.env.Environment;

/**
 * Copies Spring {@code delivery.install.mode} / {@code private-cidrs} into the
 * system properties {@link TargetNetworkPolicy#forJob} reads. Without this, a
 * dedicated portal JVM still ran the shared policy.
 */
public final class InstallNetworkBridge {
    private InstallNetworkBridge() {
    }

    public static void apply(Environment env) {
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
}
