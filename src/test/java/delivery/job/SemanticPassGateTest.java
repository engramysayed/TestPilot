package delivery.job;

import delivery.codegen.ProvenStep;
import delivery.excel.ManualTestCase;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

public class SemanticPassGateTest {
    @Test
    public void rejectsUnknownAssertionType() {
        ProvenStep bad = new ProvenStep(
                "TC1", "Page", "elementAction", "assert",
                "id", "x", "", "weirdType", "", true, "intent:ASSERT_VISIBLE");
        String reason = SemanticPassGate.rejectReason(
                new ManualTestCase("TC1", "t", "", "1. See x", "ok", "", ""),
                List.of(bad), "https://example.com");
        Assert.assertNotNull(reason);
        Assert.assertTrue(reason.contains("unknown assertionType"), reason);
    }

    @Test
    public void rejectsSubmitThatNavigatedToALoginHref() {
        ProvenStep loginClick = new ProvenStep(
                "TC1", "Page", "elementAction", "click",
                "css", "a[href='https://web.example.com/login/']", "", "", "", true,
                "intent:CLICK");
        String reason = SemanticPassGate.rejectReason(
                new ManualTestCase(
                        "TC1", "Register", "",
                        "1. Open the form at /reg/\n2. Click the Submit button",
                        "Form submitted", "", ""),
                List.of(loginClick),
                "https://web.example.com/login/");
        Assert.assertNotNull(reason, "Submit must not PASS after a login-href click");
        Assert.assertTrue(reason.toLowerCase().contains("login"), reason);
    }

    @Test
    public void acceptsExplicitCaptureCompareAndSignedOut() {
        ProvenStep capture = new ProvenStep(
                "TC1", "Order", "elementAction", "assert",
                "id", "order-id", "orderId", "captureText", "exactText", true, "intent:CAPTURE");
        ProvenStep compare = new ProvenStep(
                "TC1", "Account", "elementAction", "assert",
                "css", "#order-list li", "orderId", "capturedEquals", "exactText", true,
                "intent:COMPARE_CAPTURED");
        ProvenStep signedOut = new ProvenStep(
                "TC1", "Account", "elementAction", "assert",
                "id", "session-email", "", "signedOut", "empty", true, "intent:SIGNED_OUT");
        Assert.assertNull(SemanticPassGate.rejectReason(
                new ManualTestCase("TC1", "t", "", "1. Capture from id=order-id as orderId using exact text",
                        "ok", "", ""),
                List.of(capture, compare, signedOut), "https://shop.example"));
    }

    @Test
    public void rejectsSubmitThatClickedASelfPathHref() {
        ProvenStep selfNav = new ProvenStep(
                "TC1", "Page", "elementAction", "click",
                "css", "a[href='https://web.example.com/reg/']", "", "", "", true,
                "intent:CLICK");
        String reason = SemanticPassGate.rejectReason(
                new ManualTestCase(
                        "TC1", "Register", "",
                        "1. Open the form at /reg/\n2. Click the Submit button",
                        "Form submitted", "", ""),
                List.of(selfNav),
                "https://web.example.com/reg/");
        Assert.assertNotNull(reason, "Submit must not PASS after clicking the Excel path href");
        Assert.assertTrue(reason.toLowerCase().contains("submit")
                        || reason.toLowerCase().contains("reg"),
                reason);
    }
}
