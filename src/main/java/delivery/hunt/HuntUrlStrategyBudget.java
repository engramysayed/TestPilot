package delivery.hunt;

import java.util.HashMap;
import java.util.Map;

/**
 * P1: per-URL strategy budget. When invent/explore cycles on the same URL exceed the budget
 * without growing coverage URLs, invent is considered plateaued.
 */
public final class HuntUrlStrategyBudget {
    public static final int DEFAULT_CYCLES_PER_URL = 3;

    private final int cyclesPerUrl;
    private final Map<String, Integer> cyclesOnUrl = new HashMap<>();
    private int lastVisitedUrlCount;

    public HuntUrlStrategyBudget() {
        this(DEFAULT_CYCLES_PER_URL);
    }

    public HuntUrlStrategyBudget(int cyclesPerUrl) {
        this.cyclesPerUrl = Math.max(1, cyclesPerUrl);
    }

    public void noteCycle(String url, int visitedUrlCount) {
        String key = normalize(url);
        cyclesOnUrl.merge(key, 1, Integer::sum);
        lastVisitedUrlCount = visitedUrlCount;
    }

    /** True when invent should stop: same URL burned its budget and coverage URLs did not grow. */
    public boolean inventPlateau(String url, int visitedUrlCountNow, String strategyMode) {
        if (strategyMode == null || !strategyMode.toLowerCase().contains("invent")) {
            // also treat explore-like plateau
            if (strategyMode != null && !"invent".equalsIgnoreCase(strategyMode)
                    && cyclesOnUrl.getOrDefault(normalize(url), 0) < cyclesPerUrl) {
                return false;
            }
        }
        int onUrl = cyclesOnUrl.getOrDefault(normalize(url), 0);
        boolean burned = onUrl >= cyclesPerUrl;
        boolean noNewUrls = visitedUrlCountNow <= lastVisitedUrlCount;
        return burned && noNewUrls;
    }

    /** Simpler check used after noteCycle: plateau if this URL hit budget and urls did not increase. */
    public boolean plateauAfterCycle(String url, int visitedBefore, int visitedAfter) {
        int onUrl = cyclesOnUrl.getOrDefault(normalize(url), 0);
        return onUrl >= cyclesPerUrl && visitedAfter <= visitedBefore;
    }

    static String normalize(String url) {
        if (url == null || url.isBlank()) return "(blank)";
        String u = url.trim();
        int q = u.indexOf('?');
        if (q > 0) u = u.substring(0, q);
        int h = u.indexOf('#');
        if (h > 0) u = u.substring(0, h);
        while (u.endsWith("/") && u.length() > 8) u = u.substring(0, u.length() - 1);
        return u.toLowerCase();
    }
}
