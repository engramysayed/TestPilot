package delivery.job;

import delivery.codegen.ProvenStep;
import delivery.excel.ManualTestCase;
import delivery.ir.TcDraft;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

public class RecordedDraftsTest {

    @Test
    public void clickWithStableIdBecomesAProvenClick() {
        TcDraft draft = RecordedDrafts.draft(caseRow(false), List.of(
                new RecordedCapture("click", "Pay", "", false, "id", "pay-now", "https://shop.example/checkout")));
        Assert.assertEquals(draft.provenSteps().size(), 1);
        ProvenStep step = draft.provenSteps().get(0);
        Assert.assertEquals(step.action(), "click");
        Assert.assertEquals(step.locatorStrategy(), "id");
        Assert.assertEquals(step.locatorValue(), "pay-now");
        Assert.assertEquals(step.pageName(), "Checkout");
        Assert.assertFalse(step.secret());
    }

    @Test
    public void generatedIdIsLeftOut() {
        Assert.assertFalse(RecordedDrafts.canBuild(List.of(
                new RecordedCapture("click", "Pay", "", false, "id", "_r_15_", "https://shop.example/"))));
    }

    @Test
    public void checkThisBecomesTextAndAddressAssertions() {
        TcDraft draft = RecordedDrafts.draft(caseRow(false), List.of(
                new RecordedCapture("assert", "Order confirmed", "", false, "", "", "https://shop.example/orders/1"),
                new RecordedCapture("assert-url", "https://shop.example/orders/1", "https://shop.example/orders/1",
                        false, "", "", "https://shop.example/orders/1")));
        Assert.assertEquals(draft.provenSteps().size(), 2);
        Assert.assertEquals(draft.provenSteps().get(0).assertionType(), "textContains");
        Assert.assertEquals(draft.provenSteps().get(0).assertionExpected(), "Order confirmed");
        Assert.assertEquals(draft.provenSteps().get(1).assertionType(), "urlContains");
        Assert.assertEquals(draft.provenSteps().get(1).assertionExpected(), "/orders/1");
    }

    @Test
    public void passwordStepIsSecretAndNotStoredInTheBodyWhenSignInIsOn() {
        TcDraft draft = RecordedDrafts.draft(caseRow(true), List.of(
                new RecordedCapture("type", "Password", "s3cret", true, "name", "password", "https://shop.example/login")));
        Assert.assertTrue(draft.loginSteps().isEmpty());
        Assert.assertTrue(draft.provenSteps().isEmpty());
        Assert.assertTrue(draft.needsLoginBeforeMethod());
    }

    private static ManualTestCase caseRow(boolean signIn) {
        return new ManualTestCase(
                "TC_1",
                "Pay",
                signIn ? "Sign in with a project login account before these steps." : "",
                "1. Click \"Pay\"",
                "The order is placed",
                "P2",
                "recorded");
    }
}
