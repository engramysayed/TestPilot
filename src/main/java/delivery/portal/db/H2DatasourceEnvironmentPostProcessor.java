package delivery.portal.db;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

import java.util.Arrays;

/**
 * Fails startup before any datasource is created when the prod profile (or
 * {@code delivery.install.production=true}) runs with an empty H2 password or AUTO_SERVER.
 * Development only logs warnings.
 */
public class H2DatasourceEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {
    private static final Logger LOG = LogManager.getLogger(H2DatasourceEnvironmentPostProcessor.class);

    @Override
    public int getOrder() {
        // after ConfigDataEnvironmentPostProcessor so profiles and application*.properties are resolved
        return Ordered.LOWEST_PRECEDENCE;
    }

    static boolean isProduction(ConfigurableEnvironment env) {
        return Boolean.parseBoolean(env.getProperty("delivery.install.production", "false"))
                || Arrays.stream(env.getActiveProfiles())
                .anyMatch(p -> "prod".equalsIgnoreCase(p) || "production".equalsIgnoreCase(p));
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication application) {
        String url = env.getProperty("spring.datasource.url");
        if (url == null) {
            return;
        }
        boolean production = isProduction(env);
        H2DatasourceSafety.Result r = H2DatasourceSafety.check(
                production, url, env.getProperty("spring.datasource.password", ""));
        r.warnings().forEach(w -> LOG.warn("H2 hardening (dev): {}", w));
        if (!r.ok()) {
            throw new IllegalStateException("INSECURE_DATASOURCE: " + String.join("; ", r.errors()));
        }
    }
}
