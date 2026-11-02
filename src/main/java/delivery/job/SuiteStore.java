package delivery.job;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Read-only AE view of Backend SoT {@code {projectRoot}/suites.json} (Phase 3 interim contract).
 * Does not invent a second store; write/CRUD stay Backend-owned.
 */
public final class SuiteStore {
    public static final String FILE = "suites.json";

    public record Suite(
            String suiteId,
            String name,
            List<String> tags,
            List<String> tcIds,
            boolean builtIn,
            String alertChannelId,
            String alertMinSeverity,
            JSONObject schedule,
            JSONObject monitor
    ) {
        public Suite {
            tags = tags == null ? List.of() : List.copyOf(tags);
            tcIds = tcIds == null ? List.of() : List.copyOf(tcIds);
        }

        public Optional<String> monitorTarget() {
            if (monitor == null || !monitor.optBoolean("enabled", false)) {
                return Optional.empty();
            }
            String t = monitor.optString("target", "").trim();
            return t.isEmpty() ? Optional.empty() : Optional.of(t.toLowerCase(Locale.ROOT));
        }
    }

    private SuiteStore() {
    }

    public static Path file(Path projectRoot) {
        return projectRoot == null ? null : projectRoot.resolve(FILE);
    }

    public static List<Suite> load(Path projectRoot) {
        Path file = file(projectRoot);
        if (file == null || !Files.isRegularFile(file)) {
            return List.of();
        }
        try {
            String raw = Files.readString(file, StandardCharsets.UTF_8).trim();
            if (raw.isEmpty()) {
                return List.of();
            }
            JSONArray arr = raw.startsWith("[") ? new JSONArray(raw) : new JSONArray();
            List<Suite> out = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) {
                    continue;
                }
                String id = o.optString("suiteId", "").trim();
                if (id.isEmpty()) {
                    continue;
                }
                out.add(new Suite(
                        id,
                        o.optString("name", ""),
                        stringList(o.optJSONArray("tags")),
                        stringList(o.optJSONArray("tcIds")),
                        o.optBoolean("builtIn", false),
                        blankToNull(o.optString("alertChannelId", null)),
                        blankToNull(o.optString("alertMinSeverity", null)),
                        o.optJSONObject("schedule"),
                        o.optJSONObject("monitor")
                ));
            }
            return out;
        } catch (Exception e) {
            utils.LogsManager.warn("SUITE_STORE: " + e.getMessage());
            return List.of();
        }
    }

    public static Optional<Suite> findById(Path projectRoot, String suiteId) {
        if (suiteId == null || suiteId.isBlank()) {
            return Optional.empty();
        }
        String want = suiteId.trim();
        for (Suite s : load(projectRoot)) {
            if (want.equals(s.suiteId())) {
                return Optional.of(s);
            }
        }
        return Optional.empty();
    }

    /** Suites whose tags contain {@code tag} (case-insensitive any-match). */
    public static List<Suite> findByTag(Path projectRoot, String tag) {
        if (tag == null || tag.isBlank()) {
            return List.of();
        }
        String want = tag.trim().toLowerCase(Locale.ROOT);
        List<Suite> out = new ArrayList<>();
        for (Suite s : load(projectRoot)) {
            for (String t : s.tags()) {
                if (t != null && want.equals(t.trim().toLowerCase(Locale.ROOT))) {
                    out.add(s);
                    break;
                }
            }
        }
        return out;
    }

    /** Union of tcIds for suiteId and/or tag filter. Empty when neither selector is set. */
    public static List<String> resolveTcIds(Path projectRoot, String suiteId, String tag) {
        Set<String> ids = new LinkedHashSet<>();
        if (suiteId != null && !suiteId.isBlank()) {
            findById(projectRoot, suiteId).ifPresent(s -> ids.addAll(s.tcIds()));
        }
        if (tag != null && !tag.isBlank()) {
            for (Suite s : findByTag(projectRoot, tag)) {
                ids.addAll(s.tcIds());
            }
        }
        return List.copyOf(ids);
    }

    private static List<String> stringList(JSONArray arr) {
        if (arr == null || arr.length() == 0) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            String v = arr.optString(i, "").trim();
            if (!v.isEmpty()) {
                out.add(v);
            }
        }
        return out;
    }

    private static String blankToNull(String v) {
        if (v == null || v.isBlank() || "null".equalsIgnoreCase(v)) {
            return null;
        }
        return v.trim();
    }
}
