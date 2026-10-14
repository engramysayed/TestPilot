package delivery.mobile.lease;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class MobileLeaseConfiguration {
    @Bean
    @ConditionalOnMissingBean(Clock.class)
    public Clock mobileLeaseClock() {
        return Clock.systemUTC();
    }
}
