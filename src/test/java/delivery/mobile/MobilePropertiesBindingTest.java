package delivery.mobile;

import delivery.portal.PortalApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * M0-05: Spring binds delivery.mobile.* with defaults false; context starts without Android tooling.
 */
@SpringBootTest(classes = PortalApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:mobile-flags;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "delivery.admin-email=admin@testpilot.local",
        "delivery.admin-password=ChangeMeAdmin1!",
        "delivery.dry-run=true",
        "delivery.store-root=./target/test-delivery-store-mobile-flags",
        "delivery.work-dir=./target/test-delivery-work-mobile-flags"
        // deliberately omit delivery.mobile.* so application.properties / class defaults apply
})
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
