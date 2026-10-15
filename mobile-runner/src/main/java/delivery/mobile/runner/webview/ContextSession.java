package delivery.mobile.runner.webview;

/** Remembers the active context for one case and never saves a WebView process handle. */
public final class ContextSession {
    private String active = "NATIVE";

    public String active() {
        return active;
    }

    public String persistedHandle() {
        return "";
    }

    public Switch apply(WebViewSelection selection) {
        if (selection == null || !selection.selected()) {
            active = "NATIVE";
            String code = selection == null ? "NOT_FOUND" : selection.code();
            return new Switch("NATIVE", code);
        }
        active = "WEBVIEW";
        return new Switch("WEBVIEW", "");
    }

    public void restoreNative() {
        active = "NATIVE";
    }

    public record Switch(String active, String code) {}
}
