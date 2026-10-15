package delivery.mobile.runner.webview;

import java.util.ArrayList;
import java.util.List;

/**
 * Picks exactly one debuggable WebView by package, URL, and title.
 * A system permission dialog keeps the session native.
 */
public final class WebViewSelector {

    public WebViewSelection select(ContextPredicate want, List<DiscoveredContext> contexts) {
        List<DiscoveredContext> found = contexts == null ? List.of() : contexts;
        if (found.stream().anyMatch(DiscoveredContext::systemDialog)) {
            return WebViewSelection.blocked("SYSTEM_DIALOG");
        }
        List<DiscoveredContext> samePackage = new ArrayList<>();
        for (DiscoveredContext c : found) {
            if (want.packageName().equals(c.packageName())) {
                samePackage.add(c);
            }
        }
        if (samePackage.isEmpty()) {
            return WebViewSelection.blocked("NOT_FOUND");
        }
        List<DiscoveredContext> matched = new ArrayList<>();
        boolean sawPackageButNotUrl = false;
        for (DiscoveredContext c : samePackage) {
            boolean urlOk = c.url().contains(want.urlContains());
            boolean titleOk = want.titleContains().isBlank() || c.title().contains(want.titleContains());
            if (!urlOk) {
                sawPackageButNotUrl = true;
                continue;
            }
            if (urlOk && titleOk) {
                matched.add(c);
            }
        }
        if (matched.isEmpty()) {
            return WebViewSelection.blocked(sawPackageButNotUrl ? "URL_MISMATCH" : "NOT_FOUND");
        }
        if (matched.size() > 1) {
            return WebViewSelection.blocked("AMBIGUOUS");
        }
        DiscoveredContext one = matched.get(0);
        if (!one.debuggable()) {
            return WebViewSelection.blocked("WEBVIEW_INACCESSIBLE");
        }
        String durable = want.packageName() + "|" + want.urlContains() + "|" + want.titleContains();
        return WebViewSelection.selected(durable, one.volatileHandle());
    }
}
