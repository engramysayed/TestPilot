package delivery.portal.service.credits;

import org.testng.Assert;
import org.testng.annotations.Test;

public class CreditCostTableTest {
    @Test
    public void defaultsMatchDesign() {
        DeliveryCreditsProperties props = new DeliveryCreditsProperties();
        CreditCostTable table = new CreditCostTable(props);
        Assert.assertEquals(table.cost(CreditsBillingKind.GENERATE), 10L);
        Assert.assertEquals(table.cost(CreditsBillingKind.EXECUTE), 5L);
        Assert.assertEquals(table.cost(CreditsBillingKind.AUTOMATE), 25L);
        Assert.assertEquals(table.cost(CreditsBillingKind.HUNT), 15L);
        Assert.assertFalse(table.enabled());
        Assert.assertEquals(table.asCostsDto().get("generate"), 10L);
        Assert.assertEquals(table.asCostsDto().get("automate"), 25L);
    }

    @Test
    public void tunableViaProperties() {
        DeliveryCreditsProperties props = new DeliveryCreditsProperties();
        props.getCost().setGenerate(99L);
        Assert.assertEquals(new CreditCostTable(props).cost(CreditsBillingKind.GENERATE), 99L);
    }

    @Test
    public void billingKindMapsJobKinds() {
        Assert.assertEquals(CreditsBillingKind.fromJobKind(delivery.portal.model.JobRecord.JobKind.CONVERT),
                CreditsBillingKind.AUTOMATE);
        Assert.assertEquals(CreditsBillingKind.fromJobKind(delivery.portal.model.JobRecord.JobKind.EXECUTE),
                CreditsBillingKind.EXECUTE);
        Assert.assertEquals(CreditsBillingKind.fromJobKind(delivery.portal.model.JobRecord.JobKind.GENERATE_BATCH),
                CreditsBillingKind.GENERATE);
        Assert.assertEquals(CreditsBillingKind.fromJobKind(delivery.portal.model.JobRecord.JobKind.GENERATE_COMPARE),
                CreditsBillingKind.GENERATE);
        Assert.assertEquals(CreditsBillingKind.fromJobKind(delivery.portal.model.JobRecord.JobKind.HUNT),
                CreditsBillingKind.HUNT);
    }
}
