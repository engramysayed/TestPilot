package delivery.mobile.runner.webview;

import delivery.mobile.runner.bind.LocatorStrategy;

import java.util.ArrayList;
import java.util.List;

/** Walks native → WebView → native. A bad Chromedriver does not silently run the WebView step as native. */
public final class HybridReplay {
    private HybridReplay() {}

    public record Step(String contextKind, LocatorStrategy strategy) {}

    public static List<String> trace(ChromedriverQualification.Result driver, WebViewSelection web, List<Step> steps) {
        List<String> out = new ArrayList<>();
        ContextSession session = new ContextSession();
        for (Step step : steps) {
            if ("WEBVIEW".equals(step.contextKind())) {
                if (driver == null || !driver.ok()) {
                    out.add("BLOCKED:" + (driver == null ? "CHROMEDRIVER_PATH" : driver.code()));
                    session.restoreNative();
                    continue;
                }
                SwitchResult applied = applyWeb(session, web);
                if (!applied.ok()) {
                    out.add("BLOCKED:" + applied.code());
                    continue;
                }
                WebViewStrategyGate.requireAllowed("WEBVIEW", step.strategy());
                out.add("WEBVIEW");
                continue;
            }
            session.restoreNative();
            WebViewStrategyGate.requireAllowed("NATIVE", step.strategy());
            out.add(session.active());
        }
        return List.copyOf(out);
    }

    private static SwitchResult applyWeb(ContextSession session, WebViewSelection web) {
        ContextSession.Switch sw = session.apply(web);
        if (!"WEBVIEW".equals(sw.active())) {
            return new SwitchResult(false, sw.code().isBlank() ? "WEBVIEW_UNAVAILABLE" : sw.code());
        }
        return new SwitchResult(true, "");
    }

    private record SwitchResult(boolean ok, String code) {}
}
