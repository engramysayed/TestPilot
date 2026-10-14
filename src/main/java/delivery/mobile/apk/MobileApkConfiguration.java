package delivery.mobile.apk;

import delivery.mobile.MobileProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MobileApkConfiguration {
    @Bean
    @ConditionalOnMissingBean(ApkAnalysisBackend.class)
    public ApkAnalysisBackend apkAnalysisBackend(MobileProperties props) {
        return new ProcessApkAnalysisBackend(props);
    }
}
