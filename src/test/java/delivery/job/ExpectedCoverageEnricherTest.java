package delivery.job;

import delivery.codegen.ProvenStep;
import delivery.excel.ManualTestCase;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

public class ExpectedCoverageEnricherTest {
    @Test
    public void addsTotalAndRemainingItemWhenOverviewExpected() {
        ManualTestCase tc = new ManualTestCase(
                "TC1",
                "Checkout",
                "",
                "1. Add Sauce Labs Backpack\n2. Add Sauce Labs Bike Light\n3. Remove Sauce Labs Bike Light\n"
                        + "4. Confirm the text Checkout: Overview is visible",
                "Checkout Overview shows remaining items and a total",
                "",
                "");
        List<ProvenStep> proven = List.of(
                new ProvenStep("TC1", "CheckoutStepTwo", "elementAction", "assert",
                        "xpath", "//body", "", "textContains", "Checkout: Overview", true, "x"));
        List<ProvenStep> out = ExpectedCoverageEnricher.enrich(tc, proven);
        Assert.assertTrue(out.stream().anyMatch(s -> "Total".equals(s.assertionExpected())),
                String.valueOf(out));
        Assert.assertTrue(out.stream().anyMatch(s ->
                        s.assertionExpected() != null
                                && s.assertionExpected().contains("Backpack")),
                String.valueOf(out));
        Assert.assertFalse(out.stream().anyMatch(s ->
                        s.assertionExpected() != null
                                && s.assertionExpected().contains("Bike Light")
                                && !"Checkout: Overview".equals(s.assertionExpected())),
                String.valueOf(out));
    }
}
