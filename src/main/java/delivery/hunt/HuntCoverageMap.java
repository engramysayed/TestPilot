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
    public static final int STUCK_FAIL_STREAK = 2;
    public static final int PROMPT_MAX_CHARS = 12_000;

    private static final Set<String> STREAK_TYPES = Set.of("click", "type", "clear", "assert_visible");

    private final Path file;
    private final List<String> urls = new ArrayList<>();
    private final Set<String> urlSeen = new LinkedHashSet<>();
    private final List<String> fingerprints = new ArrayList<>();
    private final Map<String, String> controls = new LinkedHashMap<>();
    private final List<String> strategiesCompleted = new ArrayList<>();
    private final Map<String, Integer> failStreaks = new HashMap<>();

    public HuntCoverageMap(Path huntRoot) {
        this.file = huntRoot.resolve(FILE_NAME);
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

    public String forPrompt() {
        String all = renderMarkdown();
        if (all.length() <= PROMPT_MAX_CHARS) {
            return all;
        }
        return "…(earlier coverage truncated)…\n" + all.substring(all.length() - PROMPT_MAX_CHARS);
    }

    public Path path() {
        return file;
    }

    private void flush() throws Exception {
        Files.writeString(file, renderMarkdown(), StandardCharsets.UTF_8,
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
            sb.append("_none_\n");
        } else {
            for (Map.Entry<String, Integer> e : failStreaks.entrySet()) {
                String[] parts = e.getKey().split("\0", 2);
                sb.append("- ").append(parts[0]).append(" `").append(parts[1])
                        .append("`: ").append(e.getValue()).append('\n');
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
