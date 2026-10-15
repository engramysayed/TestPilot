package delivery.mobile.runner.webview;

/** One context Appium reported. The handle is live-only and must not be saved. */
public record DiscoveredContext(
        String volatileHandle,
        String packageName,
        String url,
        String title,
        boolean debuggable,
        boolean systemDialog
) {
    public DiscoveredContext {
        volatileHandle = volatileHandle == null ? "" : volatileHandle;
        packageName = packageName == null ? "" : packageName;
        url = url == null ? "" : url;
        title = title == null ? "" : title;
    }
}
