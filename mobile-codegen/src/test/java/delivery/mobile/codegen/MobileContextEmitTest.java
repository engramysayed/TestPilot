package delivery.mobile.codegen;

import delivery.mobile.ir.MobileContext;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

public class MobileContextEmitTest {

    @Test
    public void switchCallStoresPredicateNotPid() {
        String call = MobileContextEmit.switchCall(
                new MobileContext("WEBVIEW", "com.example.test", "https://test.example/checkout", "Checkout"));
        assertTrue(call.contains("ContextSwitch.to"));
        assertTrue(call.contains("com.example.test"));
        assertTrue(call.contains("https://test.example/checkout"));
        assertFalse(call.contains("WEBVIEW_"));
    }

    @Test
    public void pidInMatcherIsRejected() {
        try {
            MobileContextEmit.switchCall(new MobileContext("WEBVIEW", "com.example.test", "WEBVIEW_42", ""));
            fail("expected rejection");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("WEBVIEW_"));
        }
    }
}
