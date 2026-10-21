package delivery.codegen;

import delivery.job.TcOutcome;
import delivery.job.TcStatus;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

public class PageUnderTestTest {
    @Test
    public void bodyPrefersLastNonLoginPage() {
        List<ProvenStep> steps = List.of(
                step("LoginPage", "click"),
                step("LoginPage", "type"),
                step("Home", "click"),
                step("Home", "assert"));
        TcOutcome o = new TcOutcome(
                "TC_1", "", TcStatus.PASSED, steps, "", null, true,
                List.of(step("LoginPage", "type")), List.of());
        Assert.assertEquals(PageUnderTest.primaryPageName(o), "Home");
    }

    @Test
    public void loginOnlyBodyUsesLogin() {
        List<ProvenStep> steps = List.of(
                step("LoginPage", "type"),
                step("LoginPage", "click"));
        TcOutcome o = new TcOutcome(
                "TC_1", TcStatus.PASSED, steps, "", null);
        Assert.assertEquals(PageUnderTest.primaryPageName(o), "LoginPage");
    }

    private static ProvenStep step(String page, String action) {
        return new ProvenStep("TC_1", page, "elementAction", action,
                "id", "x", "", "", "", true, "intent");
    }
}
