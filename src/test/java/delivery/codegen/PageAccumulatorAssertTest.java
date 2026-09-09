package delivery.codegen;

import org.testng.Assert;
import org.testng.annotations.Test;

public class PageAccumulatorAssertTest {
    @Test
    public void solidIdUsesLocatorPath() {
        ProvenStep step = new ProvenStep("TC", "Home", "assert", "assert",
                "id", "toast-msg", "", "textContains", "Created", true, "ok");
        Assert.assertFalse(PageAccumulator.isBodyTextAssertForTest(step));
    }

    @Test
    public void bodyXpathUsesBodyText() {
        ProvenStep step = new ProvenStep("TC", "Home", "assert", "assert",
                "xpath", "//body", "", "textContains", "Welcome", true, "ok");
        Assert.assertTrue(PageAccumulator.isBodyTextAssertForTest(step));
    }
}
