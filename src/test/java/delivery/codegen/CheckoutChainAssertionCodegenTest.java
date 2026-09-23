package delivery.codegen;

import delivery.job.TcOutcome;
import delivery.job.TcStatus;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Explicit IR capture/compare/signed-out survive emit. Ordinary repeated textContains stays unchanged.
 */
public class CheckoutChainAssertionCodegenTest {
    private static final Path TEMPLATES = Path.of("customer-framework-template/templates");

    @Test
    public void repeatedPrefixTextContainsIsNotRewritten() throws Exception {
        Path dir = Files.createTempDirectory("no-prefix-rewrite");
        ProvenStep confirmation = bodyText("TC_ORD_01", "Order", "Order KLA-");
        ProvenStep onAccount = bodyText("TC_ORD_01", "Account", "Order KLA-");
        TcOutcome outcome = new TcOutcome(
                "TC_ORD_01", "Order on account", TcStatus.PASSED, List.of(onAccount),
                "", null, false, List.of())
                .withSetup(List.of(confirmation));
        new CodeWriter(TEMPLATES).write(dir, List.of(outcome));
        String test = Files.readString(dir.resolve("src/test/java/project/tests/generated/TC_ORD_01.java"));
        Assert.assertTrue(test.contains("assert_Body_Text_Contains(\"Order KLA-\")"), test);
        Assert.assertFalse(test.contains("_Capture()"), test);
        Assert.assertFalse(test.contains("Equals_Captured"), test);
    }

    @Test
    public void overlayTextDoesNotInferSignedOut() throws Exception {
        Path dir = Files.createTempDirectory("no-signed-out-infer");
        ProvenStep expire = new ProvenStep("TC_SES_01", "Account", "elementAction", "click",
                "id", "expire-session", "", "", "", true, "intent:CLICK");
        ProvenStep overlay = bodyText("TC_SES_01", "Account", "Session expired — sign in again");
        new CodeWriter(TEMPLATES).write(dir, List.of(new TcOutcome(
                "TC_SES_01", "Session expiry overlay", TcStatus.PASSED,
                List.of(expire, overlay), "", null, false, List.of())));
        String test = Files.readString(dir.resolve("src/test/java/project/tests/generated/TC_SES_01.java"));
        Assert.assertTrue(test.contains("Session expired — sign in again"), test);
        Assert.assertFalse(test.contains("Signed_Out"), test);
        Assert.assertFalse(test.contains("authenticatedIdentityGone"), test);
    }

    @Test
    public void explicitCaptureAndCompareEmitLocatorsAndExactCompare() throws Exception {
        Path dir = Files.createTempDirectory("explicit-capture-emit");
        ProvenStep capture = new ProvenStep("TC_ORD_01", "Order", "elementAction", "assert",
                "id", "order-id", "orderId", "captureText", "exactText", true, "intent:CAPTURE");
        ProvenStep click = new ProvenStep("TC_ORD_01", "Order", "elementAction", "click",
                "css", "a[href='/account.html']", "", "", "", true, "intent:CLICK");
        ProvenStep compare = new ProvenStep("TC_ORD_01", "Account", "elementAction", "assert",
                "css", "#order-list li", "orderId", "capturedEquals", "exactText", true,
                "intent:COMPARE_CAPTURED");
        new CodeWriter(TEMPLATES).write(dir, List.of(new TcOutcome(
                "TC_ORD_01", "Order on account", TcStatus.PASSED,
                List.of(capture, click, compare), "", null, false, List.of())));
        String test = Files.readString(dir.resolve("src/test/java/project/tests/generated/TC_ORD_01.java"));
        String order = Files.readString(dir.resolve("src/main/java/project/pages/Order_Actions.java"));
        String account = Files.readString(dir.resolve("src/main/java/project/pages/Account_Actions.java"));
        String locators = Files.readString(dir.resolve("src/main/java/project/pages/Order_Locators.java"));
        Assert.assertFalse((test + order + account).contains("KLA-1001"), test);
        Assert.assertTrue(locators.contains("order-id") || locators.contains("order_id"), locators);
        Assert.assertTrue(order.contains("captureFrom("), order);
        Assert.assertTrue(account.contains("compareCapturedExact("), account);
        String accountLocators = Files.readString(dir.resolve("src/main/java/project/pages/Account_Locators.java"));
        Assert.assertTrue(accountLocators.contains("#order-list li"), accountLocators);
        Assert.assertTrue(test.contains("Capture()"), test);
        Assert.assertTrue(test.contains("Equals_Captured()"), test);
    }

    @Test
    public void explicitSignedOutEmitsSuppliedLocatorNotHardcodedHeader() throws Exception {
        Path dir = Files.createTempDirectory("explicit-signed-out-emit");
        ProvenStep overlay = bodyText("TC_SES_01", "Account", "Session expired — sign in again");
        ProvenStep header = new ProvenStep("TC_SES_01", "Account", "elementAction", "assert",
                "id", "session-email", "", "signedOut", "empty", true, "intent:SIGNED_OUT");
        ProvenStep accountEmail = new ProvenStep("TC_SES_01", "Account", "elementAction", "assert",
                "id", "account-email", "", "signedOut", "text:Not signed in", true, "intent:SIGNED_OUT");
        new CodeWriter(TEMPLATES).write(dir, List.of(new TcOutcome(
                "TC_SES_01", "Session expiry overlay", TcStatus.PASSED,
                List.of(overlay, header, accountEmail), "", null, false, List.of())));
        String actions = Files.readString(dir.resolve("src/main/java/project/pages/Account_Actions.java"));
        Assert.assertTrue(actions.contains("signedOut("), actions);
        Assert.assertTrue(actions.contains("empty") || actions.contains("Not signed in"), actions);
        Assert.assertFalse(actions.contains("authenticatedIdentityGone()"), actions);
        String locators = Files.readString(dir.resolve("src/main/java/project/pages/Account_Locators.java"));
        Assert.assertTrue(locators.contains("session-email"), locators);
        Assert.assertTrue(locators.contains("account-email"), locators);
    }

    @Test
    public void pageActionsTemplateWiresLocatorCaptureCompareAndSignedOut() throws Exception {
        String source = Files.readString(TEMPLATES.resolve("PageActions.java.ftl"));
        Assert.assertTrue(source.contains("captureFrom("), source);
        Assert.assertTrue(source.contains("compareCapturedExact("), source);
        Assert.assertTrue(source.contains("signedOut("), source);
        Assert.assertFalse(source.contains("captureBodyMatch"), source);
        Assert.assertFalse(source.contains("authenticatedIdentityGone"), source);
    }

    private static ProvenStep bodyText(String tcId, String page, String expected) {
        return new ProvenStep(tcId, page, "elementAction", "assert",
                "xpath", "//body//*[contains(normalize-space(.),'" + expected + "')]",
                "", "textContains", expected, true, "intent:ASSERT_VISIBLE:text");
    }
}
