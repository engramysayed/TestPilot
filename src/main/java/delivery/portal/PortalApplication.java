package delivery.portal;

import delivery.mobile.MobileProperties;
import delivery.portal.service.credits.DeliveryCreditsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication(scanBasePackages = {"delivery.portal", "delivery.mobile"})
@EntityScan(basePackages = {"delivery.portal.persistence", "delivery.mobile.persistence"})
@EnableJpaRepositories(basePackages = {"delivery.portal.persistence", "delivery.mobile.persistence"})
@EnableConfigurationProperties({DeliveryPortalProperties.class, MobileProperties.class, DeliveryCreditsProperties.class})
@EnableAsync
public class PortalApplication {
    private static final Logger log = LoggerFactory.getLogger(PortalApplication.class);

    public static void main(String[] args) {
        // Convert jobs share this JVM — prove a fresh process loaded the fixed gate.
        log.info("SemanticPassGate: intent/action checks only (no Excel cart/URL page-name rejects)");
        SpringApplication.run(PortalApplication.class, args);
    }
}
