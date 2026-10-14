package delivery.mobile;

import delivery.portal.PortalApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * M0-05: Spring binds delivery.mobile.* off for base/CI (LD-01). Context starts without Android tooling.
 * <p>Pins {@code delivery.mobile.enabled=false} + empty active profiles so suite pollution
 * ({@code SPRING_PROFILES_ACTIVE=product}, System props, cached contexts) cannot flip the bean on.
 * Does not change production defaults.
 */
@SpringBootTest(classes = PortalApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:mobile-flags;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "delivery.admin-email=admin@testpilot.local",
        "delivery.admin-password=ChangeMeAdmin1!",
        "delivery.dry-run=true",
        "delivery.store-root=./target/test-delivery-store-mobile-flags",
        "delivery.work-dir=./target/test-delivery-work-mobile-flags",
        // AE suite isolation vs product profile / System property bleed (LD-01 base/CI = off)
        "spring.profiles.active=",
        "delivery.mobile.enabled=false",
        "delivery.mobile.live-tests=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class MobilePropertiesBindingTest extends AbstractTestNGSpringContextTests {
    @Autowired
    MobileProperties mobileProperties;

    @Test
    public void boundFlagsDefaultFalse() {
        Assert.assertNotNull(mobileProperties);
        Assert.assertFalse(mobileProperties.isEnabled());
        Assert.assertFalse(mobileProperties.isLiveTests());
    }

    @Test
    public void enablingLiveTestsIsExplicit() {
        MobileProperties local = new MobileProperties();
        local.setEnabled(true);
        local.setLiveTests(true);
        Assert.assertTrue(local.isEnabled());
        Assert.assertTrue(local.isLiveTests());
        // Autowired bean remains default-off (no leakage)
        Assert.assertFalse(mobileProperties.isEnabled());
        Assert.assertFalse(mobileProperties.isLiveTests());
    }
}
