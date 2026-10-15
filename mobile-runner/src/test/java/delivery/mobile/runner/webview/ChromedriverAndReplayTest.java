package delivery.mobile.runner.webview;

import delivery.mobile.runner.bind.BindException;
import delivery.mobile.runner.bind.LocatorStrategy;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.*;

public class ChromedriverAndReplayTest {

    @Test
    public void versionMismatchBlocks() {
        ChromedriverQualification.Result r = ChromedriverQualification.check(
                new ChromedriverQualification.Request("120.0.6099.109", "119.0.0.0", "C:/tools/chromedriver.exe", true, false));
        assertFalse(r.ok());
        assertEquals(r.code(), "CHROMEDRIVER_MISMATCH");
    }

    @Test
    public void runtimeDownloadIsBlocked() {
        ChromedriverQualification.Result r = ChromedriverQualification.check(
                new ChromedriverQualification.Request("120.0.6099.109", "120.0.6099.109", "C:/tools/chromedriver.exe", true, true));
        assertEquals(r.code(), "RUNTIME_DOWNLOAD_BLOCKED");
    }

    @Test
    public void approvedPathAndVersionPass() {
        ChromedriverQualification.Result r = ChromedriverQualification.check(
                new ChromedriverQualification.Request("120.0.6099.109", "120.0.6099.109", "C:/tools/chromedriver.exe", true, false));
        assertTrue(r.ok());
        assertEquals(r.approvedPath(), "C:/tools/chromedriver.exe");
    }

    @Test
    public void nativeSelectorRejectedInWebView() {
        try {
            WebViewStrategyGate.requireAllowed("WEBVIEW", LocatorStrategy.ACCESSIBILITY_ID);
            fail("expected native selector rejection");
        } catch (BindException e) {
            assertEquals(e.code(), BindException.Code.NATIVE_IN_WEBVIEW);
        }
    }

    @Test
    public void cssAllowedInWebViewAndRejectedInNative() {
        WebViewStrategyGate.requireAllowed("WEBVIEW", LocatorStrategy.CSS);
        try {
            WebViewStrategyGate.requireAllowed("NATIVE", LocatorStrategy.CSS);
            fail("expected css rejection");
        } catch (BindException e) {
            assertEquals(e.code(), BindException.Code.CSS_IN_NATIVE);
        }
    }

    @Test
    public void mixedReplayUsesWebViewThenRestoresNative() {
        ChromedriverQualification.Result driver = ChromedriverQualification.check(
                new ChromedriverQualification.Request("120.0.6099.109", "120.0.6099.109", "C:/tools/chromedriver.exe", true, false));
        WebViewSelection web = new WebViewSelector().select(
                new ContextPredicate("WEBVIEW", "com.example.test", "https://test.example/checkout", "Checkout"),
                List.of(new DiscoveredContext("WEBVIEW_7", "com.example.test",
                        "https://test.example/checkout", "Checkout", true, false)));
        List<String> trace = HybridReplay.trace(driver, web, List.of(
                new HybridReplay.Step("NATIVE", LocatorStrategy.ACCESSIBILITY_ID),
                new HybridReplay.Step("WEBVIEW", LocatorStrategy.CSS),
                new HybridReplay.Step("NATIVE", LocatorStrategy.ACCESSIBILITY_ID)
        ));
        assertEquals(trace, List.of("NATIVE", "WEBVIEW", "NATIVE"));
    }

    @Test
    public void mismatchedDriverDoesNotFallBackToNativeForWebViewStep() {
        ChromedriverQualification.Result driver = ChromedriverQualification.check(
                new ChromedriverQualification.Request("120.0.6099.109", "119.0.0.0", "C:/tools/chromedriver.exe", true, false));
        WebViewSelection web = new WebViewSelector().select(
                new ContextPredicate("WEBVIEW", "com.example.test", "https://test.example/checkout", "Checkout"),
                List.of(new DiscoveredContext("WEBVIEW_7", "com.example.test",
                        "https://test.example/checkout", "Checkout", true, false)));
        List<String> trace = HybridReplay.trace(driver, web, List.of(
                new HybridReplay.Step("WEBVIEW", LocatorStrategy.CSS)
        ));
        assertEquals(trace, List.of("BLOCKED:CHROMEDRIVER_MISMATCH"));
    }
}
