package delivery.excel;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

public class StructuredStoryIntakeTest {
    @Test
    public void toPromptBlock_includesAcceptanceCriteria() {
        StructuredStoryIntake.Intake intake = new StructuredStoryIntake.Intake(
                "Login",
                List.of("Valid credentials open dashboard", "Wrong password shows exact error"),
                "user",
                "web",
                "",
                "shopper",
                "Access account",
                "No OTP",
                List.of("negative", "boundary"),
                List.of("cred-profile-1")
        );
        String block = StructuredStoryIntake.toPromptBlock(intake);
        Assert.assertTrue(block.contains("Acceptance criteria:"));
        Assert.assertTrue(block.contains("Wrong password"));
        Assert.assertTrue(block.contains("Requested techniques:"));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void structuredMode_rejectsShallowFreeText() {
        StructuredStoryIntake.Intake intake = StructuredStoryIntake.fromMap(Map.of(
                "freeText", "do login stuff"
        ));
        StructuredStoryIntake.validate(intake, true);
    }

    @Test
    public void fromMap_acceptsNewlineCriteria() {
        StructuredStoryIntake.Intake intake = StructuredStoryIntake.fromMap(Map.of(
                "title", "Cart",
                "acceptanceCriteria", "Add item\nSee badge"
        ));
        Assert.assertEquals(intake.acceptanceCriteria().size(), 2);
    }
}
