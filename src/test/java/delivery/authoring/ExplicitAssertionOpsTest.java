package delivery.authoring;

import delivery.codegen.ProvenStep;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

public class ExplicitAssertionOpsTest {

    @Test
    public void parseCaptureRecordsLocatorRuleAndVariable() {
        ProvenStep step = ExplicitAssertionOps.bind(
                "Capture from id=order-id as orderId using exact text", "TC_ORD_01");
        Assert.assertEquals(step.assertionType(), "captureText");
        Assert.assertEquals(step.locatorStrategy(), "id");
        Assert.assertEquals(step.locatorValue(), "order-id");
        Assert.assertEquals(step.value(), "orderId");
        Assert.assertEquals(step.assertionExpected(), "exactText");
        Assert.assertEquals(step.action(), "assert");
        Assert.assertTrue(step.rationale().startsWith("intent:CAPTURE"), step.rationale());
    }

    @Test
    public void parseCompareRecordsComparisonLocatorTypeAndVariable() {
        ProvenStep step = ExplicitAssertionOps.bind(
                "Compare captured orderId on css=#order-list li using exact text", "TC_ORD_01");
        Assert.assertEquals(step.assertionType(), "capturedEquals");
        Assert.assertEquals(step.locatorStrategy(), "css");
        Assert.assertEquals(step.locatorValue(), "#order-list li");
        Assert.assertEquals(step.value(), "orderId");
        Assert.assertEquals(step.assertionExpected(), "exactText");
        Assert.assertTrue(step.rationale().startsWith("intent:COMPARE_CAPTURED"), step.rationale());
    }

    @Test
    public void parseSignedOutUsesSuppliedLocatorAndExpected() {
        ProvenStep empty = ExplicitAssertionOps.bind(
                "Assert signed-out on id=session-email expected empty", "TC_SES_01");
        Assert.assertEquals(empty.assertionType(), "signedOut");
        Assert.assertEquals(empty.locatorStrategy(), "id");
        Assert.assertEquals(empty.locatorValue(), "session-email");
        Assert.assertEquals(empty.assertionExpected(), "empty");
        Assert.assertTrue(empty.rationale().startsWith("intent:SIGNED_OUT"), empty.rationale());

        ProvenStep text = ExplicitAssertionOps.bind(
                "Assert signed-out on id=account-email expected text Not signed in", "TC_SES_01");
        Assert.assertEquals(text.locatorValue(), "account-email");
        Assert.assertEquals(text.assertionExpected(), "text:Not signed in");
    }

    @Test
    public void malformedExplicitOperationsDoNotFallBackToOrdinaryBinding() {
        for (String line : List.of(
                "Capture from unsupported=order as orderId using exact text",
                "Capture from id=order as 123 using exact text",
                "Compare captured orderId on css=#order-list li",
                "Compare captured orderId on css=#order-list li using regex:.*",
                "Assert signed-out on tag=body expected empty")) {
            Assert.expectThrows(IllegalArgumentException.class, () -> ExplicitAssertionOps.parse(line));
        }
    }

    @Test
    public void signedOutSupportsCompoundCssLocator() {
        Assert.assertEquals(ExplicitAssertionOps.bind(
                "Assert signed-out on css=#header .identity expected empty", "TC_SES_01")
                .locatorValue(), "#header .identity");
    }

    @Test
    public void ordinaryConfirmTextIsNotAnExplicitCapture() {
        Assert.assertNull(ExplicitAssertionOps.parse("Confirm the text Order KLA- is visible"));
        Assert.assertNull(ExplicitAssertionOps.parse("Confirm the text Session expired — sign in again is visible"));
    }

    @Test
    public void binderCopiesSuppliedLocatorWithoutDomCandidates() {
        StepIntentBinder.IntentLine capture = new StepIntentBinder.IntentLine(
                StepIntentBinder.IntentKind.ASSERT_VISIBLE,
                "Capture from id=order-id as orderId using exact text");
        StepIntentBinder.BindResult boundCapture = StepIntentBinder.bindSingle(
                capture, "TC_ORD_01", List.of(), List.of());
        Assert.assertTrue(boundCapture.ok(), boundCapture.rejectReason());
        Assert.assertEquals(boundCapture.steps().get(0).locatorValue(), "order-id");
        Assert.assertEquals(boundCapture.steps().get(0).assertionType(), "captureText");

        StepIntentBinder.IntentLine compare = new StepIntentBinder.IntentLine(
                StepIntentBinder.IntentKind.ASSERT_VISIBLE,
                "Compare captured orderId on css=#order-list li using exact text");
        StepIntentBinder.BindResult boundCompare = StepIntentBinder.bindSingle(
                compare, "TC_ORD_01", List.of(), List.of());
        Assert.assertTrue(boundCompare.ok(), boundCompare.rejectReason());
        Assert.assertEquals(boundCompare.steps().get(0).locatorStrategy(), "css");
        Assert.assertEquals(boundCompare.steps().get(0).locatorValue(), "#order-list li");
        Assert.assertEquals(boundCompare.steps().get(0).assertionType(), "capturedEquals");
    }

    @Test
    public void parseIntentsKeepsOrdinaryConfirmSeparateFromExplicitOps() {
        delivery.excel.ManualTestCase tc = new delivery.excel.ManualTestCase(
                "TC_MIX", "mix", "",
                """
                        1. Capture from id=order-id as orderId using exact text
                        2. Confirm the text Order KLA- is visible
                        3. Compare captured orderId on css=#order-list li using exact text
                        """,
                "ok", "", "");
        java.util.List<StepIntentBinder.IntentLine> intents = StepIntentBinder.parseIntents(tc);
        Assert.assertEquals(intents.size(), 3);
        Assert.assertTrue(ExplicitAssertionOps.isExplicit(intents.get(0).text()));
        Assert.assertFalse(ExplicitAssertionOps.isExplicit(intents.get(1).text()));
        Assert.assertEquals(intents.get(1).kind(), StepIntentBinder.IntentKind.ASSERT_VISIBLE);
        Assert.assertTrue(ExplicitAssertionOps.isExplicit(intents.get(2).text()));
    }
}
