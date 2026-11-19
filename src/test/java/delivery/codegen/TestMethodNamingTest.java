package delivery.codegen;

import org.testng.Assert;
import org.testng.annotations.Test;

public class TestMethodNamingTest {
    @Test
    public void usesFullTitleUpToLengthCap() {
        Assert.assertEquals(
                TestMethodNaming.deterministic("Confirm the operation user is created successfully", "TC_06"),
                "Confirm_the_operation_user_is_created_successfully");
    }

    @Test
    public void fallsBackWhenTitleBlank() {
        Assert.assertEquals(
                TestMethodNaming.deterministic("", "TC_01"),
                "case_TC_01");
    }

    @Test
    public void shouldFromTitle_trimsAtWordBoundaryNotMidWord() {
        String longTitle = "verify the customer enters a very descriptive and lengthy test case title that exceeds limit";
        String name = TestMethodNaming.shouldFromTitle(longTitle, "Checkout", "TC_LONG");
        Assert.assertTrue(name.length() <= 100, "name length " + name.length());
        Assert.assertFalse(name.endsWith("_"), "no dangling underscore: " + name);
        Assert.assertTrue(name.startsWith("should_"), name);
        for (String part : name.split("_")) {
            if (part.isBlank() || part.equals("should")) continue;
            Assert.assertTrue(longTitle.toLowerCase().contains(part),
                    "every word in the trimmed name must be a full word from the title; got '" + part + "'");
        }
    }
}
