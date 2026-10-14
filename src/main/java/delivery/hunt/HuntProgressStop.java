package delivery.hunt;

import java.util.List;
import java.util.Map;

/**
 * P1: prefer finishing when a cycle added neither a new visited URL nor a new unique bug fingerprint
 * (and the planner did not emit new scenarios). Cuts CYCLE_CAP thrash.
 */
public final class HuntProgressStop {
    private HuntProgressStop() {}

    public record Snapshot(int visitedUrls, int uniqueBugs, int scenarios) {}

    public static Snapshot of(HuntCoverageMap coverage, List<Map<String, Object>> bugs, List<?> scenarios) {
        int urls = coverage == null ? 0 : coverage.visitedUrlCount();
        int b = bugs == null ? 0 : bugs.size();
        int s = scenarios == null ? 0 : scenarios.size();
        return new Snapshot(urls, b, s);
    }

    /** True when this cycle made no measurable progress vs the pre-cycle snapshot. */
    public static boolean noProgress(Snapshot before, Snapshot after, boolean plannerEmittedActions) {
        if (before == null || after == null) return false;
        boolean grew = after.visitedUrls() > before.visitedUrls()
                || after.uniqueBugs() > before.uniqueBugs()
                || after.scenarios() > before.scenarios();
        if (grew) return false;
        // idle finish only when we at least attempted a cycle with actions or an empty continue
        return true;
    }
}
