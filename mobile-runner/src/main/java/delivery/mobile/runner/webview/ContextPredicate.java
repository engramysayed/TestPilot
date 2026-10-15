package delivery.mobile.runner.webview;

/** Durable match rule. Package plus URL and title text. Never a process id. */
public record ContextPredicate(
        String kind,
        String packageName,
        String urlContains,
        String titleContains
) {
    public ContextPredicate {
        kind = kind == null || kind.isBlank() ? "NATIVE" : kind.trim().toUpperCase();
        packageName = packageName == null ? "" : packageName;
        urlContains = urlContains == null ? "" : urlContains;
        titleContains = titleContains == null ? "" : titleContains;
        String joined = packageName + urlContains + titleContains;
        if (joined.contains("WEBVIEW_")) {
            throw new IllegalArgumentException("volatile WEBVIEW_ handle must not be a predicate");
        }
    }
}
