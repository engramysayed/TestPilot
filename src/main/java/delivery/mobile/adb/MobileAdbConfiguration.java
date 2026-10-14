package delivery.mobile.adb;

import delivery.mobile.MobileProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MobileAdbConfiguration {
    @Bean
    @ConditionalOnMissingBean(AdbCommandBackend.class)
    public AdbCommandBackend adbCommandBackend(MobileProperties props) {
        return new ProcessAdbCommandBackend(props);
    }
}
