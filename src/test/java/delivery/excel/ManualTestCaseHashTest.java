package delivery.excel;

import org.testng.Assert;
import org.testng.annotations.Test;

public class ManualTestCaseHashTest {

    @Test
    public void contentHashChanges_whenOnlyVisualAssertionChanges() {
        ManualTestCase a = new ManualTestCase(
                "TC_001", "t", "", "click login", "Welcome", "", "", "");
        ManualTestCase b = new ManualTestCase(
                "TC_001", "t", "", "click login", "Welcome", "", "",
                "Welcome heading is visible");
        Assert.assertNotEquals(a.contentHash(), b.contentHash());
    }

    @Test
    public void sevenArgConstructor_defaultsVisualAssertionEmpty() {
        ManualTestCase tc = new ManualTestCase(
                "TC_001", "t", "", "click login", "Welcome", "", "");
        Assert.assertEquals(tc.visualAssertion(), "");
        Assert.assertEquals(tc.testData(), "");
    }

    @Test
    public void contentHashChanges_whenOnlyTestDataChanges() {
        ManualTestCase a = new ManualTestCase(
                "TC_001", "t", "", "Enter in the First name field", "ok", "", "", "", "");
        ManualTestCase b = new ManualTestCase(
                "TC_001", "t", "", "Enter in the First name field", "ok", "", "", "", "Alice");
        Assert.assertNotEquals(a.contentHash(), b.contentHash());
    }
}
