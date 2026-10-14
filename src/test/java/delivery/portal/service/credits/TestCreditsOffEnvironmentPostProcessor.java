package delivery.portal.service.credits;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * LD-02 IT seed: default portal ITs must not hit 402 INSUFFICIENT_CREDITS.
 * <p>
 * Product {@code src/main/resources/application.properties} keeps
 * {@code delivery.credits.enabled=true} (balance 0; admin grants — no auto-grant).
 * Test classpath {@code application.properties} sets the flag false, but
 * {@code utils.PropertyReader} can copy main props into {@link System} properties
 * mid-suite (e.g. via {@code CursorHealClient}), which would re-enable credits for
 * later {@code @SpringBootTest} contexts in the same JVM.
 * <p>
 * This processor runs on the test classpath only and forces the system property off
 * before each context binds. Intentional credits suites still win with
 * {@code @SpringBootTest(properties={"delivery.credits.enabled=true", ...})} /
 * {@code @TestPropertySource}, which outrank system properties.
 */
public final class TestCreditsOffEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    static final String CREDITS_ENABLED_PROPERTY = "delivery.credits.enabled";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        System.setProperty(CREDITS_ENABLED_PROPERTY, "false");
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
