package delivery.portal;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

/**
 * Bridges {@code delivery.authoring.precision.cursor-model} (Spring config) into the
 * {@code delivery.authoring.precision.cursor-model} system property so the non-Spring
 * {@link delivery.heal.CursorHealClient} picks it up without a direct dependency on
 * Spring. An existing system property or env var wins (admins pin a model via -D or
 * {@code KEEL_CURSOR_MODEL}); "auto" or blank is a no-op.
 */
@Component
public class PrecisionModelPropertyBinder {

    static final String PROPERTY = "delivery.authoring.precision.cursor-model";

    private final DeliveryPortalProperties portalProperties;

    public PrecisionModelPropertyBinder(DeliveryPortalProperties portalProperties) {
        this.portalProperties = portalProperties;
    }

    @PostConstruct
    public void publish() {
        String configured = portalProperties.getPrecisionCursorModel();
        if (configured == null || configured.isBlank() || "auto".equalsIgnoreCase(configured.trim())) {
            return;
        }
        String existing = System.getProperty(PROPERTY);
        if (existing != null && !existing.isBlank()) {
            return;
        }
        String env = System.getenv("KEEL_CURSOR_MODEL");
        if (env != null && !env.isBlank()) {
            return;
        }
        System.setProperty(PROPERTY, configured.trim());
    }
}
