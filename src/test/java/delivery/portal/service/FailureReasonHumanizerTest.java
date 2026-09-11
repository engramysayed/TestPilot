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
        Assert.assertFalse(out.toLowerCase().contains("facebook"));
        Assert.assertTrue(out.toLowerCase().contains("app"));
    }

    @Test
    public void phoneHealMessageIsSiteAgnostic() {
        String out = FailureReasonHumanizer.forUser(
                "HEAL_EXHAUSTED: Cursor pick path exhausted; reason=phone field missing");
        Assert.assertFalse(out.toLowerCase().contains("facebook"));
        Assert.assertTrue(out.toLowerCase().contains("phone"));
    }

    @Test
    public void blankStaysBlank() {
        Assert.assertEquals(FailureReasonHumanizer.forUser(""), "");
        Assert.assertEquals(FailureReasonHumanizer.forUser(null), "");
    }

    @Test
    public void dnsNameNotResolvedIsPlainEnglish() {
        String out = FailureReasonHumanizer.forUser(
                "unknown error: net::ERR_NAME_NOT_RESOLVED\n  (Session info: chrome=153.0.8010.37)\n"
                        + "Command: [id, get {url=https://opssit.axispay.app/}]");
        Assert.assertTrue(out.toLowerCase().contains("dns") || out.toLowerCase().contains("host not found"), out);
        Assert.assertFalse(out.contains("Session info"));
        Assert.assertTrue(out.length() < 200, out);
    }

    @Test
    public void invalidSessionIsPlainEnglish() {
        String out = FailureReasonHumanizer.forUser("invalid session id\nBuild info: version: '4.49.0'");
        Assert.assertTrue(out.toLowerCase().contains("chrome") || out.toLowerCase().contains("browser"), out);
        Assert.assertFalse(out.contains("Build info"));
    }
}
