package delivery.mobile.appium;

import delivery.mobile.MobileProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * M1-06 wiring: process supervisor / port ownership / session bookkeeping only.
 * Default Appium backend is the CLI process supervisor (no java-client on portal).
 * {@link MobilePreflight}, {@link SupervisedAppiumLifecycle}, and
 * {@link DeviceLeaseGuardLifecycleGate} are {@code @Component}s.
 */
@Configuration
public class MobileAppiumConfiguration {

    @Bean
    @ConditionalOnMissingBean(OwnedLoopbackPortAllocator.class)
    public OwnedLoopbackPortAllocator ownedLoopbackPortAllocator(MobileProperties props) {
        return new OwnedLoopbackPortAllocator(props);
    }

    @Bean
    @ConditionalOnMissingBean(AppiumProcessBackend.class)
    public AppiumProcessBackend appiumProcessBackend(MobileProperties props) {
        return new ProcessAppiumBackend(props);
    }

    @Bean
    @ConditionalOnMissingBean(AppiumSessionFactory.class)
    public AppiumSessionFactory appiumSessionFactory() {
        return new AppiumSessionFactory();
    }
}
