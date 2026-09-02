package delivery.portal;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Maps {@code delivery.browser.*} into Owner {@link utils.Config.AppConfig} system properties
 * before any WebDriver factory reads them.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
public class BrowserConfigEnvironmentPostProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Boolean headless = environment.getProperty("delivery.browser.headless", Boolean.class);
        if (Boolean.TRUE.equals(headless)) {
            System.setProperty("BROWSER_HEADLESS", "true");
            System.setProperty("EXECUTION_TYPE", "HEADLESS");
        } else if (Boolean.FALSE.equals(headless)) {
            System.setProperty("BROWSER_HEADLESS", "false");
        }
        String browserType = environment.getProperty("delivery.browser.type");
        if (browserType != null && !browserType.isBlank()) {
            System.setProperty("BROWSER_TYPE", browserType.trim());
        }
    }
}
