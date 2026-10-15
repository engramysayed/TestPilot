package delivery.mobile.runner.webview;

/** Result of picking one context. {@code durableKey} is what gets stored. */
public record WebViewSelection(
        boolean selected,
        String kind,
        String code,
        String durableKey,
        String liveHandle
) {
    public static WebViewSelection selected(String durableKey, String liveHandle) {
        return new WebViewSelection(true, "WEBVIEW", "", durableKey, liveHandle == null ? "" : liveHandle);
    }

    public static WebViewSelection blocked(String code) {
        String kind = "SYSTEM_DIALOG".equals(code) ? "NATIVE" : "";
        return new WebViewSelection(false, kind, code, "", "");
    }
}
