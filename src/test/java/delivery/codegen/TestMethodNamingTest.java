package delivery.codegen;

import org.testng.Assert;
import org.testng.annotations.Test;

public class TestMethodNamingTest {
    @Test
    public void trimsToFiveWords() {
        Assert.assertEquals(
                TestMethodNaming.deterministic("Confirm the operation user is created successfully", "TC_06"),
                "Confirm_the_operation_user_is");
    }

    @Test
    public void fallsBackWhenTitleBlank() {
        Assert.assertEquals(
                TestMethodNaming.deterministic("", "TC_01"),
                "case_TC_01");
    }

    @Test
    public void acceptsValidOllamaIdentifier() {
        Assert.assertEquals(
                TestMethodNaming.parseOllamaResponse("successful_login_test"),
                "Successful_login_test");
    }

    @Test
    public void rejectsTooManyWords() {
        Assert.assertNull(TestMethodNaming.parseOllamaResponse("one_two_three_four_five_six"));
    }
}
