package delivery.mobile.runner.webview;

import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.*;

public class WebViewSelectorTest {

    private static DiscoveredContext web(String handle, String url, String title, boolean debuggable) {
        return new DiscoveredContext(handle, "com.example.test", url, title, debuggable, false);
    }

    private static ContextPredicate checkout() {
        return new ContextPredicate("WEBVIEW", "com.example.test", "https://test.example/checkout", "Checkout");
    }

    @Test
    public void oneMatchingDebuggableWebViewIsSelectedByPredicateNotPid() {
        WebViewSelection sel = new WebViewSelector().select(checkout(), List.of(
                web("WEBVIEW_111", "https://test.example/home", "Home", true),
                web("WEBVIEW_222", "https://test.example/checkout", "Checkout", true)
        ));
        assertTrue(sel.selected());
        assertEquals(sel.kind(), "WEBVIEW");
        assertEquals(sel.durableKey(), "com.example.test|https://test.example/checkout|Checkout");
        assertFalse(sel.durableKey().contains("WEBVIEW_"));
        assertEquals(sel.liveHandle(), "WEBVIEW_222");
    }

    @Test
    public void multipleMatchesStayBlocked() {
        WebViewSelection sel = new WebViewSelector().select(checkout(), List.of(
                web("WEBVIEW_1", "https://test.example/checkout", "Checkout", true),
                web("WEBVIEW_2", "https://test.example/checkout", "Checkout", true)
        ));
        assertFalse(sel.selected());
        assertEquals(sel.code(), "AMBIGUOUS");
    }

    @Test
    public void lateContextIsUsedWhenItAppears() {
        WebViewSelector selector = new WebViewSelector();
        assertEquals(selector.select(checkout(), List.of()).code(), "NOT_FOUND");
        WebViewSelection later = selector.select(checkout(), List.of(
                web("WEBVIEW_9", "https://test.example/checkout", "Checkout", true)));
        assertTrue(later.selected());
    }

    @Test
    public void disappearedContextIsNotFound() {
        WebViewSelection sel = new WebViewSelector().select(checkout(), List.of(
                web("WEBVIEW_9", "https://test.example/other", "Other", true)));
        assertEquals(sel.code(), "URL_MISMATCH");
    }

    @Test
    public void inaccessibleWebViewBlocks() {
        WebViewSelection sel = new WebViewSelector().select(checkout(), List.of(
                web("WEBVIEW_9", "https://test.example/checkout", "Checkout", false)));
        assertEquals(sel.code(), "WEBVIEW_INACCESSIBLE");
        assertEquals(sel.liveHandle(), "");
    }

    @Test
    public void urlMismatchBlocks() {
        WebViewSelection sel = new WebViewSelector().select(checkout(), List.of(
                web("WEBVIEW_9", "https://test.example/cart", "Cart", true)));
        assertEquals(sel.code(), "URL_MISMATCH");
    }

    @Test
    public void systemPermissionDialogStaysNative() {
        DiscoveredContext dialog = new DiscoveredContext("", "android", "", "Permission", true, true);
        WebViewSelection sel = new WebViewSelector().select(checkout(), List.of(
                dialog,
                web("WEBVIEW_9", "https://test.example/checkout", "Checkout", true)));
        assertEquals(sel.code(), "SYSTEM_DIALOG");
        assertEquals(sel.kind(), "NATIVE");
    }

    @Test
    public void changedPidDoesNotChangeDurableKey() {
        WebViewSelector selector = new WebViewSelector();
        WebViewSelection first = selector.select(checkout(), List.of(
                web("WEBVIEW_11", "https://test.example/checkout", "Checkout", true)));
        WebViewSelection second = selector.select(checkout(), List.of(
                web("WEBVIEW_99", "https://test.example/checkout", "Checkout", true)));
        assertEquals(first.durableKey(), second.durableKey());
        assertNotEquals(first.liveHandle(), second.liveHandle());
    }

    @Test
    public void sessionRestoresNativeAfterFailedSwitch() {
        ContextSession session = new ContextSession();
        WebViewSelection bad = new WebViewSelector().select(checkout(), List.of());
        assertEquals(session.apply(bad).code(), "NOT_FOUND");
        assertEquals(session.active(), "NATIVE");
        assertEquals(session.persistedHandle(), "");

        WebViewSelection ok = new WebViewSelector().select(checkout(), List.of(
                web("WEBVIEW_5", "https://test.example/checkout", "Checkout", true)));
        assertEquals(session.apply(ok).active(), "WEBVIEW");
        session.restoreNative();
        assertEquals(session.active(), "NATIVE");
        assertEquals(session.persistedHandle(), "");
    }
}
