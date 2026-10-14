package delivery.authoring;

import org.testng.annotations.Test;
import java.util.List;
import static org.testng.Assert.*;

public class PrecisionAbsenceTargetTest {
    @Test public void selectedOtpInputRetainsAbsenceSemanticsAndRealLocator() {
        var candidates = DomCandidateExtractor.extract("<input id='otp-input' aria-label='OTP'>");
        var chosen = candidates.stream().filter(c -> c.value().equals("otp-input")).findFirst().orElseThrow();
        var service = new AuthoringService(new LocalLlmClient("", ""), new LocatorValidator());
        var result = service.stepsPreferringCandidate("TC", new StepIntentBinder.IntentLine(
                StepIntentBinder.IntentKind.ASSERT_VISIBLE, "Confirm the OTP field is not visible"),
                candidates, chosen.id(), false);
        assertEquals(result.size(), 1);
        assertTrue(result.get(0).validated());
        assertEquals(result.get(0).locatorValue(), "otp-input");
        assertEquals(result.get(0).assertionType(), "notVisible");
        var direct = StepIntentBinder.bindSingle(new StepIntentBinder.IntentLine(
                StepIntentBinder.IntentKind.ASSERT_VISIBLE, "Confirm the OTP field is not visible"),
                "TC", List.of(chosen), List.of(chosen.id()));
        assertTrue(direct.ok(), direct.rejectReason());
        assertEquals(direct.steps().get(0).locatorValue(), "otp-input");
        assertEquals(direct.steps().get(0).assertionType(), "notVisible");
    }
}
