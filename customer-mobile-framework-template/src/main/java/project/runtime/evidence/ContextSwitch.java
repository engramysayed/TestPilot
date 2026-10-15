package project.runtime.evidence;

/**
 * Records the context a generated test switched to.
 * The saved value is package + URL + title. A WebView process name is rejected.
 */
public final class ContextSwitch {
    private static final ThreadLocal<String> ACTIVE = ThreadLocal.withInitial(() -> "NATIVE");
    private static final ThreadLocal<String> PREDICATE = ThreadLocal.withInitial(() -> "");

    private ContextSwitch() {}

    public static void to(Object driver, String kind, String packageName, String url, String title) {
        String pkg = packageName == null ? "" : packageName;
        String u = url == null ? "" : url;
        String t = title == null ? "" : title;
        String predicate = pkg + "|" + u + "|" + t;
        if (predicate.contains("WEBVIEW_")) {
            throw new IllegalArgumentException("volatile WEBVIEW_ handle must not be stored");
        }
        PREDICATE.set(predicate);
        ACTIVE.set(kind != null && kind.equalsIgnoreCase("WEBVIEW") ? "WEBVIEW" : "NATIVE");
    }

    public static String active() {
        return ACTIVE.get();
    }

    public static String predicate() {
        return PREDICATE.get();
    }

    public static void restoreNative() {
        ACTIVE.set("NATIVE");
    }

    public static void clear() {
        ACTIVE.set("NATIVE");
        PREDICATE.set("");
    }
}
