package delivery.hunt;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Append-only coverage map for Bug Hunter compounding memory across cycles.
 */
public final class HuntCoverageMap {
    public static final String FILE_NAME = "coverage-map.md";
    public static final String JSON_FILE_NAME = "coverage-map.json";
    public static final int STUCK_FAIL_STREAK = 2;
    public static final int PROMPT_MAX_CHARS = 12_000;

    private static final Set<String> STREAK_TYPES = Set.of("click", "type", "clear", "assert_visible");

    private final Path file;
    private final Path jsonFile;
    private final List<String> urls = new ArrayList<>();
    private final Set<String> urlSeen = new LinkedHashSet<>();
    private final List<String> fingerprints = new ArrayList<>();
    private final Map<String, String> controls = new LinkedHashMap<>();
    private final List<String> strategiesCompleted = new ArrayList<>();
    private final Map<String, Integer> failStreaks = new HashMap<>();
    private final List<String> untested = new ArrayList<>();
    private final Set<String> blocked = new LinkedHashSet<>();

    public HuntCoverageMap(Path huntRoot) {
        this.file = huntRoot.resolve(FILE_NAME);
        this.jsonFile = huntRoot.resolve(JSON_FILE_NAME);
    }

    public void noteVisit(String url, String title, String heading) throws Exception {
        if (url != null && !url.isBlank() && urlSeen.add(url.trim())) {
            urls.add(url.trim());
        }
        if (url != null && !url.isBlank()) {
            String fp = url.trim() + " — title: " + nullToEmpty(title)
                    + ", heading: " + nullToEmpty(heading);
            if (!fingerprints.contains(fp)) {
                fingerprints.add(fp);
            }
        }
        flush();
    }

    public void recordActions(List<Map<String, Object>> actionLog) throws Exception {
        if (actionLog == null || actionLog.isEmpty()) {
            return;
        }
        for (Map<String, Object> row : actionLog) {
            String type = str(row.get("type")).toLowerCase();
            String status = str(row.get("status")).toLowerCase();
            String locatorKey = locatorKeyOf(row);

            if (!locatorKey.isBlank()) {
                controls.put(type + " `" + locatorKey + "`", status);
            }

            if (!STREAK_TYPES.contains(type) || locatorKey.isBlank()) {
                continue;
            }

            String streakKey = type + "\0" + locatorKey;
            if ("fail".equals(status)) {
                failStreaks.merge(streakKey, 1, Integer::sum);
            } else if ("ok".equals(status)) {
                failStreaks.remove(streakKey);
            }
        }
        flush();
    }

    public void markStrategyDone(String strategy) throws Exception {
        if (strategy == null || strategy.isBlank()) {
            return;
        }
        String name = strategy.trim();
        if (!strategiesCompleted.contains(name)) {
            strategiesCompleted.add(name);
        }
        flush();
    }

    public void noteUntested(String gap) throws Exception {
        if (gap == null || gap.isBlank()) {
            return;
        }
        String g = gap.trim();
        if (!untested.contains(g)) {
            untested.add(g);
            flush();
        }
    }

    public void markTestedHint(String gapPrefix) throws Exception {
        if (gapPrefix == null || gapPrefix.isBlank() || untested.isEmpty()) {
            return;
        }
        String p = gapPrefix.trim().toLowerCase();
        untested.removeIf(g -> g.toLowerCase().contains(p));
        flush();
    }

    /** Seed common login-hunt gaps once at start. */
    public void seedLoginGaps() throws Exception {
        noteUntested("Valid login with ${TARGET_USERNAME}/${TARGET_PASSWORD}");
        noteUntested("OTP challenge after successful sign-in");
        noteUntested("Empty username validation");
        noteUntested("Empty password validation");
        noteUntested("Invalid credentials error UI");
    }

    public int failStreak(String type, String locatorKey) {
        if (type == null || locatorKey == null) {
            return 0;
        }
        return failStreaks.getOrDefault(type.toLowerCase() + "\0" + locatorKey, 0);
    }

    public boolean shouldStopStuck() {
        for (int streak : failStreaks.values()) {
            if (streak >= STUCK_FAIL_STREAK) {
                return true;
            }
        }
        return false;
    }

    public void clearFailStreaks() throws Exception {
        if (failStreaks.isEmpty()) {
            return;
        }
        failStreaks.clear();
        flush();
    }

    /**
     * Moves every locator at or past the stuck threshold onto the blocked list and clears its
     * streak. The blocked list reaches the planner via {@link #forPrompt()} so it changes
     * approach instead of the hunt stopping early.
     *
     * @return newly blocked entries (empty when nothing was stuck)
     */
    public List<String> markBlockedFromStreaks() throws Exception {
        List<String> newlyBlocked = new ArrayList<>();
        for (Map.Entry<String, Integer> e : failStreaks.entrySet()) {
            if (e.getValue() < STUCK_FAIL_STREAK) {
                continue;
            }
            String[] parts = e.getKey().split("\0", 2);
            String entry = parts[0] + " `" + (parts.length > 1 ? parts[1] : "") + "`";
            if (blocked.add(entry)) {
                newlyBlocked.add(entry);
            }
        }
        if (!newlyBlocked.isEmpty()) {
            failStreaks.entrySet().removeIf(e -> e.getValue() >= STUCK_FAIL_STREAK);
            flush();
        }
        return newlyBlocked;
    }

    public List<String> blockedLocators() {
        return List.copyOf(blocked);
    }

    public String forPrompt() {
        String all = renderMarkdown();
        if (all.length() <= PROMPT_MAX_CHARS) {
            return all;
        }
        return "…(earlier coverage truncated)…\n" + all.substring(all.length() - PROMPT_MAX_CHARS);
    }

    public int visitedUrlCount() {
        return urls.size();
    }

    /** Snapshot of URLs visited so far (ordered, deduped). */
    public List<String> visitedUrls() {
        return List.copyOf(urls);
    }

    public Path path() {
        return file;
    }

    private void flush() throws Exception {
        Files.writeString(file, renderMarkdown(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("urls", urls);
        json.put("fingerprints", fingerprints);
        json.put("controls", controls);
        json.put("strategiesCompleted", strategiesCompleted);
        json.put("failStreaks", failStreaks);
        json.put("blocked", List.copyOf(blocked));
        json.put("untested", untested);
        Files.writeString(jsonFile, new org.json.JSONObject(json).toString(2), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
    }

    private String renderMarkdown() {
        StringBuilder sb = new StringBuilder();
        sb.append("# Bug Hunter coverage map\n\n");
        sb.append("URLs, controls exercised, and failure streaks across the hunt.\n\n");

        sb.append("## URLs visited\n\n");
        if (urls.isEmpty()) {
            sb.append("_none_\n\n");
        } else {
            for (String url : urls) {
                sb.append("- ").append(url).append('\n');
            }
            sb.append('\n');
        }

        sb.append("## Screen fingerprints\n\n");
        if (fingerprints.isEmpty()) {
            sb.append("_none_\n\n");
        } else {
            for (String fp : fingerprints) {
                sb.append("- ").append(fp).append('\n');
            }
            sb.append('\n');
        }

        sb.append("## Controls exercised\n\n");
        if (controls.isEmpty()) {
            sb.append("_none_\n\n");
        } else {
            for (Map.Entry<String, String> e : controls.entrySet()) {
                sb.append("- ").append(e.getKey()).append(" → `").append(e.getValue()).append("`\n");
            }
            sb.append('\n');
        }

        sb.append("## Strategies completed\n\n");
        if (strategiesCompleted.isEmpty()) {
            sb.append("_none_\n\n");
        } else {
            for (String s : strategiesCompleted) {
                sb.append("- ").append(s).append('\n');
            }
            sb.append('\n');
        }

        sb.append("## Failure streaks\n\n");
        if (failStreaks.isEmpty()) {
            sb.append("_none_\n\n");
        } else {
            for (Map.Entry<String, Integer> e : failStreaks.entrySet()) {
                String[] parts = e.getKey().split("\0", 2);
                sb.append("- ").append(parts[0]).append(" `").append(parts[1])
                        .append("`: ").append(e.getValue()).append('\n');
            }
            sb.append('\n');
        }

        sb.append("## Do not retry (blocked)\n\n");
        if (blocked.isEmpty()) {
            sb.append("_none_\n\n");
        } else {
            for (String b : blocked) {
                sb.append("- ").append(b).append('\n');
            }
            sb.append("\nThese failed repeatedly — pick a different control, or use "
                    + "navigate/refresh/restart_browser instead of retrying them.\n\n");
        }

        sb.append("## Untested\n\n");
        if (untested.isEmpty()) {
            sb.append("_none_\n");
        } else {
            for (String g : untested) {
                sb.append("- ").append(g).append('\n');
            }
        }
        return sb.toString();
    }

    private static String locatorKeyOf(Map<String, Object> row) {
        String loc = str(row.get("locator"));
        if (!loc.isBlank()) {
            return loc;
        }
        String strategy = str(row.get("locatorStrategy"));
        String value = str(row.get("locatorValue"));
        if (!strategy.isBlank() && !value.isBlank()) {
            return strategy + ":" + value;
        }
        if (!value.isBlank()) {
            return value;
        }
        return "";
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
