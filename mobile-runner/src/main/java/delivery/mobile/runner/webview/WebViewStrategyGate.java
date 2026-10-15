package delivery.mobile.runner.webview;

import delivery.mobile.runner.bind.BindException;
import delivery.mobile.runner.bind.LocatorStrategy;

/** WebView accepts CSS, id, text, and XPath. Native strategies stay on the native context. */
public final class WebViewStrategyGate {
    private WebViewStrategyGate() {}

    public static void requireAllowed(String contextKind, LocatorStrategy strategy) {
        String kind = contextKind == null ? "NATIVE" : contextKind.trim().toUpperCase();
        if ("WEBVIEW".equals(kind) && strategy != null && !webViewAllowed(strategy)) {
            throw new BindException(BindException.Code.NATIVE_IN_WEBVIEW, "native selector rejected in WebView");
        }
        if ("NATIVE".equals(kind) && strategy == LocatorStrategy.CSS) {
            throw new BindException(BindException.Code.CSS_IN_NATIVE, "CSS is not valid in NATIVE_APP");
        }
    }

    private static boolean webViewAllowed(LocatorStrategy strategy) {
        return strategy == LocatorStrategy.CSS
                || strategy == LocatorStrategy.XPATH
                || strategy == LocatorStrategy.TEXT
                || strategy == LocatorStrategy.RESOURCE_ID;
    }
}
