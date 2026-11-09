package delivery.portal.security;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Locale;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ProductionSafetyRunner implements ApplicationRunner {
    private final Environment env;

    public ProductionSafetyRunner(Environment env) {
        this.env = env;
    }

    @Override
    public void run(ApplicationArguments args) {
        // 'product' is the real-deployment profile start-portal.cmd uses by default. Treat it as
        // production for safety-guard purposes so a blank DB password or default admin password
        // refuses to boot on an operator's machine, not only in a distant 'production' profile
        // that nobody actually uses.
        boolean production = Boolean.parseBoolean(env.getProperty("delivery.install.production", "false"))
                || Arrays.stream(env.getActiveProfiles())
                .anyMatch(p -> "prod".equalsIgnoreCase(p)
                        || "production".equalsIgnoreCase(p)
                        || "product".equalsIgnoreCase(p));
        ProductionSafetyGuard.requireSafe(new ProductionSafetyGuard.Config(
                production,
                env.getProperty("delivery.admin-password", ""),
                env.getProperty("delivery.public-base-url", ""),
                Boolean.parseBoolean(env.getProperty("spring.h2.console.enabled", "false")),
                env.getProperty("delivery.provider.allowlist", ""),
                env.getProperty("delivery.install.mode", "shared"),
                env.getProperty("spring.datasource.password", ""),
                env.getProperty("delivery.public-base-url", "").toLowerCase(Locale.ROOT).startsWith("https://"),
                env.getProperty("delivery.install.private-cidrs", "")
        ));
        ProductionSafetyGuard.requireSafeRunnerTransport(new ProductionSafetyGuard.RunnerTransport(
                production,
                env.getProperty("keel.topology", "single"),
                Boolean.parseBoolean(env.getProperty("keel.runner.mtls.required", "false"))));
    }
}
