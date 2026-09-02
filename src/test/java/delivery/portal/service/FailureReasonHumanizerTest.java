package delivery.portal.service;

import org.testng.Assert;
import org.testng.annotations.Test;

public class FailureReasonHumanizerTest {

    @Test
    public void ambiguousPhoneFieldIsPlainEnglish() {
        String out = FailureReasonHumanizer.forUser(
                "HEAL_EXHAUSTED: Cursor solved nothing usable for Enter in the Phone field; reason=AMBIGUOUS:TYPE_FIELD:c2,c3");
        Assert.assertTrue(out.toLowerCase().contains("input field") || out.toLowerCase().contains("phone"));
        Assert.assertFalse(out.contains("HEAL_EXHAUSTED"));
        Assert.assertFalse(out.contains("c2,c3"));
    }

    @Test
    public void assertMissingCandidateIsPlainEnglish() {
        String out = FailureReasonHumanizer.forUser(
                "HEAL_EXHAUSTED: Cursor solved nothing usable for Confirm a clear validation/error state is shown; "
                        + "reason=No DOM candidate for intent ASSERT_VISIBLE: Confirm a clear validation/error state is shown");
        Assert.assertTrue(out.toLowerCase().contains("error") || out.toLowerCase().contains("message"));
        Assert.assertFalse(out.contains("ASSERT_VISIBLE"));
    }

    @Test
    public void blankStaysBlank() {
        Assert.assertEquals(FailureReasonHumanizer.forUser(""), "");
        Assert.assertEquals(FailureReasonHumanizer.forUser(null), "");
    }
}
