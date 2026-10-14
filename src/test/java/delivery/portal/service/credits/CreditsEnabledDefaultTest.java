package delivery.portal.service.credits;

import org.testng.Assert;
import org.testng.annotations.Test;

/** delivery.credits.enabled field default is false (soft launch). */
public class CreditsEnabledDefaultTest {
    @Test
    public void propertiesDefaultEnabledFalse() {
        DeliveryCreditsProperties p = new DeliveryCreditsProperties();
        Assert.assertFalse(p.isEnabled());
        Assert.assertFalse(new CreditCostTable(p).enabled());
    }
}
