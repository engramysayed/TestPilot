package delivery.portal.service;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Flaky tags SoT: {@code {projectRoot}/flaky-tags.json}.
 */
public final class FlakyTagDiskStore {
    public static final String FILE_NAME = "flaky-tags.json";
    public static final String TAG_FLAKY = "flaky";
    public static final Set<String> SIGNALS = Set.of(
            "timeout", "intermittent", "heal_retry", "manual", "other");

    private static final Pattern ID = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9_-]{0,127}$");

    private final ObjectMapper mapper;

    public FlakyTagDiskStore(ObjectMapper mapper) {
        this.mapper = mapper == null ? new ObjectMapper() : mapper;
    }

    public FlakyTagDiskStore() {
        this(new ObjectMapper());
    }

    public Path file(Path projectRoot) {
        return projectRoot.resolve(FILE_NAME);
    }

    public List<Map<String, Object>> list(Path projectRoot, boolean includeCleared) throws IOException {
        List<Map<String, Object>> all = readAll(projectRoot);
        if (includeCleared) {
            return List.copyOf(all);
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : all) {
            if (row.get("clearedAt") == null || str(row.get("clearedAt")).isBlank()) {
                out.add(row);
            }
        }
        return List.copyOf(out);
    }

    public Optional<Map<String, Object>> get(Path projectRoot, String tagId) throws IOException {
        validateId(tagId, "tagId");
        for (Map<String, Object> row : readAll(projectRoot)) {
            if (tagId.trim().equals(str(row.get("tagId")))) {
                return Optional.of(row);
            }
        }
        return Optional.empty();
    }

    public Map<String, Object> add(
            Path projectRoot,
            String tcId,
            Integer stepIndex,
            String reason,
            String sourceJobId,
            String signal
    ) throws IOException {
        if (tcId == null || tcId.isBlank()) {
            throw new BadRequestException("tcId is required");
        }
        String sig = (signal == null || signal.isBlank()) ? "manual" : signal.trim().toLowerCase(Locale.ROOT);
        if (!SIGNALS.contains(sig)) {
            sig = "other";
        }
        String tagId = UUID.randomUUID().toString().replace("-", "");
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("tagId", tagId);
        row.put("tcId", tcId.trim());
        row.put("stepIndex", stepIndex);
        row.put("tag", TAG_FLAKY);
        row.put("reason", reason == null ? "" : reason);
        row.put("sourceJobId", sourceJobId == null || sourceJobId.isBlank() ? null : sourceJobId.trim());
        row.put("signal", sig);
        row.put("createdAt", Instant.now().toString());
        row.put("clearedAt", null);

        List<Map<String, Object>> all = new ArrayList<>(readAll(projectRoot));
        all.add(row);
        writeAll(projectRoot, all);
        return normalize(row);
    }

    /** Soft-clear by setting clearedAt. */
    public Map<String, Object> clear(Path projectRoot, String tagId) throws IOException {
        validateId(tagId, "tagId");
        List<Map<String, Object>> all = new ArrayList<>(readAll(projectRoot));
        boolean found = false;
        Map<String, Object> updated = null;
        for (int i = 0; i < all.size(); i++) {
            Map<String, Object> row = new LinkedHashMap<>(all.get(i));
            if (tagId.trim().equals(str(row.get("tagId")))) {
                if (row.get("clearedAt") == null || str(row.get("clearedAt")).isBlank()) {
                    row.put("clearedAt", Instant.now().toString());
                }
                all.set(i, row);
                updated = normalize(row);
                found = true;
                break;
            }
        }
        if (!found) {
            throw new NotFoundException("Flaky tag not found: " + tagId);
        }
        writeAll(projectRoot, all);
        return updated;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> readAll(Path projectRoot) throws IOException {
        Path path = file(projectRoot);
        if (!Files.isRegularFile(path)) {
            return List.of();
        }
        Object raw = mapper.readValue(path.toFile(), Object.class);
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object o : list) {
            if (o instanceof Map<?, ?> map) {
                out.add(normalize((Map<String, Object>) map));
            }
        }
        return out;
    }

    private void writeAll(Path projectRoot, List<Map<String, Object>> rows) throws IOException {
        Path path = file(projectRoot);
        Files.createDirectories(path.getParent());
        List<Map<String, Object>> normalized = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            normalized.add(normalize(row));
        }
        mapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), normalized);
    }

    static Map<String, Object> normalize(Map<String, Object> raw) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tagId", str(raw.get("tagId")));
        m.put("tcId", str(raw.get("tcId")));
        Object step = raw.get("stepIndex");
        if (step == null || str(step).isBlank() || "null".equalsIgnoreCase(str(step))) {
            m.put("stepIndex", null);
        } else if (step instanceof Number n) {
            m.put("stepIndex", n.intValue());
        } else {
            try {
                m.put("stepIndex", Integer.parseInt(String.valueOf(step)));
            } catch (Exception e) {
                m.put("stepIndex", null);
            }
        }
        m.put("tag", TAG_FLAKY);
        m.put("reason", str(raw.get("reason")));
        Object sourceJobId = raw.get("sourceJobId");
        m.put("sourceJobId", sourceJobId == null || str(sourceJobId).isBlank() ? null : str(sourceJobId));
        String signal = str(raw.get("signal")).toLowerCase(Locale.ROOT);
        if (!SIGNALS.contains(signal)) {
            signal = "other";
        }
        m.put("signal", signal);
        m.put("createdAt", str(raw.get("createdAt")));
        Object cleared = raw.get("clearedAt");
        m.put("clearedAt", cleared == null || str(cleared).isBlank() ? null : str(cleared));
        return m;
    }

    public static void validateId(String id, String label) {
        if (id == null || id.isBlank() || !ID.matcher(id.trim()).matches()) {
            throw new BadPathException("Invalid " + label);
        }
        String lower = id.trim().toLowerCase(Locale.ROOT);
        if (lower.contains("..") || lower.contains("/") || lower.contains("\\")) {
            throw new BadPathException("Invalid " + label);
        }
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    public static class BadPathException extends RuntimeException {
        public BadPathException(String message) {
            super(message);
        }
    }

    public static class BadRequestException extends RuntimeException {
        public BadRequestException(String message) {
            super(message);
        }
    }

    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String message) {
            super(message);
        }
    }
}
