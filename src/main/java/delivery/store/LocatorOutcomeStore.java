package delivery.store;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Reads per-slot FirstOf outcomes (test-output/keel-results/locator-outcomes.jsonl) and ranks a
 * bag by Laplace-smoothed win ratio. Runtime writers are {@code FirstOf.recordOutcome} in the
 * customer framework; the file is append-only and NOT reset by KeelResults.reset, so history
 * accumulates across runs. Codegen reorder is silent-by-default: an absent or unreadable file
 * returns an empty Aggregate, which keeps every bag in its incoming stability-tier order.
 */
public final class LocatorOutcomeStore {
    private static final Logger log = LoggerFactory.getLogger(LocatorOutcomeStore.class);

    private LocatorOutcomeStore() {}

    public static Path defaultFile(Path projectRoot) {
        return projectRoot.resolve("test-output").resolve("keel-results").resolve("locator-outcomes.jsonl");
    }

    public static Aggregate read(Path projectRoot) {
        Path file = defaultFile(projectRoot);
        if (projectRoot != null && !Files.isRegularFile(file)) {
            // Signal a path mismatch: a project that has run tests already has a test-output
            // folder, but the outcomes file sits where the WRITER thinks user.dir / keel.results.dir
            // points — if the writer ran under a different CWD, codegen reorder silently no-ops.
            Path marker = projectRoot.resolve("test-output");
            if (Files.isDirectory(marker)) {
                log.info("locator-outcomes.jsonl missing under {} but test-output exists — "
                        + "codegen reorder will no-op. Check -Dkeel.results.dir on the test-run JVM.", projectRoot);
            }
        }
        return readFile(file);
    }

    public static Aggregate readFile(Path file) {
        Map<String, Map<String, Stats>> perField = new HashMap<>();
        if (file == null || !Files.isRegularFile(file)) {
            return new Aggregate(perField);
        }
        try (Stream<String> lines = Files.lines(file)) {
            lines.forEach(line -> ingest(line, perField));
        } catch (IOException e) {
            log.debug("LocatorOutcomeStore read failed: {}", e.getMessage());
            return new Aggregate(new HashMap<>());
        }
        return new Aggregate(perField);
    }

    private static void ingest(String line, Map<String, Map<String, Stats>> perField) {
        if (line == null || line.isBlank()) return;
        try {
            JSONObject o = new JSONObject(line.trim());
            String page = o.optString("page", "");
            String field = o.optString("field", "");
            String target = o.optString("target", "");
            if (field.isEmpty() || target.isEmpty()) return;
            String key = page + '\0' + field;
            Stats s = perField.computeIfAbsent(key, k -> new HashMap<>())
                    .computeIfAbsent(target, t -> new Stats());
            // Compacted schema: a rolled-up row carries wins/misses counts instead of a single outcome.
            if (o.has("wins") || o.has("misses")) {
                s.wins += o.optInt("wins", 0);
                s.misses += o.optInt("misses", 0);
                return;
            }
            String outcome = o.optString("outcome", "");
            if (outcome.isEmpty()) return;
            if ("WIN".equals(outcome)) s.wins++;
            else if ("MISS".equals(outcome)) s.misses++;
        } catch (RuntimeException ignored) {
            // malformed line: ignore, never fail codegen on data
        }
    }

    /**
     * Roll per-event WIN/MISS lines into one line per {@code (page, field, target)} carrying
     * {@code wins} / {@code misses} counts, so the file stops growing with the number of runs
     * and shrinks to {@code O(unique locators)}. Writes atomically via a sibling temp file.
     * No-op on an absent or empty file. Safe to re-run; the compacted schema aggregates back
     * to itself and stays mergeable with legacy per-event lines appended afterwards.
     */
    public static void compact(Path file) throws IOException {
        if (file == null || !Files.isRegularFile(file)) return;
        Aggregate agg = readFile(file);
        if (agg.perField.isEmpty()) return;
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, Map<String, Stats>> perField : agg.perField.entrySet()) {
            String key = perField.getKey();
            int sep = key.indexOf('\0');
            String page = sep < 0 ? "" : key.substring(0, sep);
            String field = sep < 0 ? key : key.substring(sep + 1);
            for (Map.Entry<String, Stats> perTarget : perField.getValue().entrySet()) {
                Stats s = perTarget.getValue();
                if (s.wins == 0 && s.misses == 0) continue;
                JSONObject o = new JSONObject();
                o.put("page", page);
                o.put("field", field);
                o.put("target", perTarget.getKey());
                o.put("wins", s.wins);
                o.put("misses", s.misses);
                out.add(o.toString());
            }
        }
        Path tmp = file.resolveSibling(file.getFileName() + ".compact.tmp");
        Files.writeString(tmp, String.join("\n", out) + (out.isEmpty() ? "" : "\n"), StandardCharsets.UTF_8);
        try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** {@code By.STRATEGY: VALUE} — matches the Selenium By.toString() form that FirstOf logs. */
    public static String targetSig(String strategy, String value) {
        if (strategy == null) strategy = "";
        if (value == null) value = "";
        String prefix = switch (strategy) {
            case "cssSelector" -> "By.cssSelector";
            case "id" -> "By.id";
            case "xpath" -> "By.xpath";
            case "name" -> "By.name";
            case "className" -> "By.className";
            case "tagName" -> "By.tagName";
            case "linkText" -> "By.linkText";
            case "partialLinkText" -> "By.partialLinkText";
            default -> "By." + strategy;
        };
        return prefix + ": " + value;
    }

    public static final class Aggregate {
        private final Map<String, Map<String, Stats>> perField;

        Aggregate(Map<String, Map<String, Stats>> perField) {
            this.perField = perField;
        }

        /** True if at least one locator event has been seen. */
        public boolean isEmpty() {
            return perField.isEmpty();
        }

        public Stats lookup(String page, String field, String targetSig) {
            Map<String, Stats> inner = perField.get((page == null ? "" : page) + '\0' + (field == null ? "" : field));
            if (inner == null) return null;
            return inner.get(targetSig);
        }

        /**
         * Rank input indices by Laplace-smoothed win ratio; stable for ties. Unknown locators score
         * 0.5 (same as 0 wins / 0 misses after smoothing), so an unseen fallback keeps its incoming
         * tier position — a known loser falls below it, a known winner climbs past it.
         */
        public List<Integer> rankedIndices(String page, String field, List<String> targetSigs) {
            List<Integer> indices = new ArrayList<>();
            for (int i = 0; i < targetSigs.size(); i++) indices.add(i);
            if (isEmpty() || targetSigs.isEmpty()) return indices;
            String key = (page == null ? "" : page) + '\0' + (field == null ? "" : field);
            Map<String, Stats> inner = perField.get(key);
            if (inner == null) return indices;
            indices.sort(Comparator.<Integer>comparingDouble(i -> {
                Stats s = inner.get(targetSigs.get(i));
                return s == null ? 0.5 : s.laplaceRatio();
            }).reversed());
            return indices;
        }
    }

    public static final class Stats {
        int wins;
        int misses;

        public int wins() {
            return wins;
        }

        public int misses() {
            return misses;
        }

        public double laplaceRatio() {
            return (wins + 1.0) / (wins + misses + 2.0);
        }
    }
}
