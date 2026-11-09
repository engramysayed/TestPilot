package delivery.portal.service;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/** A3: structured action/assert plans under {@code {projectRoot}/structured-plans/{id}.json}. */
public final class StructuredPlanDiskStore {
    public static final String DIR = "structured-plans";
    private static final Pattern ID = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9_-]{0,127}$");
    private static final Set<String> STEP_TYPES = Set.of("action", "assert");

    private final ObjectMapper mapper;

    public StructuredPlanDiskStore(ObjectMapper mapper) {
        this.mapper = mapper == null ? new ObjectMapper() : mapper;
    }

    public List<Map<String, Object>> list(Path projectRoot) throws IOException {
        Path dir = projectRoot.resolve(DIR);
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        try (var stream = Files.list(dir)) {
            stream.filter(p -> p.getFileName().toString().endsWith(".json"))
                    .forEach(p -> {
                        try {
                            read(projectRoot, stem(p)).ifPresent(out::add);
                        } catch (IOException ignored) {
                        }
                    });
        }
        return List.copyOf(out);
    }

    public Optional<Map<String, Object>> get(Path projectRoot, String planId) throws IOException {
        return read(projectRoot, planId);
    }

    public Map<String, Object> create(Path projectRoot, String planId, String name, List<Map<String, Object>> steps)
            throws IOException {
        validateId(planId);
        Path file = file(projectRoot, planId);
        if (Files.exists(file)) {
            throw new ConflictException("Plan already exists: " + planId);
        }
        Map<String, Object> doc = newPlan(planId, name, steps, 1, null);
        write(projectRoot, planId, doc);
        return doc;
    }

    public Map<String, Object> update(Path projectRoot, String planId, String name,
                                      List<Map<String, Object>> steps, Integer expectedRevision) throws IOException {
        validateId(planId);
        Map<String, Object> current = read(projectRoot, planId)
                .orElseThrow(() -> new NotFoundException("Plan not found: " + planId));
        int rev = toInt(current.get("revision"));
        if (expectedRevision != null && expectedRevision != rev) {
            throw new ConflictException("Stale revision: expected " + expectedRevision + " head " + rev);
        }
        Map<String, Object> doc = newPlan(planId, name, steps, rev + 1, current);
        write(projectRoot, planId, doc);
        return doc;
    }

    private Map<String, Object> newPlan(String planId, String name, List<Map<String, Object>> steps,
                                        int revision, Map<String, Object> prev) {
        List<Map<String, Object>> normalized = normalizeSteps(steps);
        if (normalized.isEmpty()) {
            throw new BadRequestException("steps must not be empty");
        }
        long assertCount = normalized.stream().filter(s -> "assert".equals(s.get("type"))).count();
        if (assertCount < 1) {
            throw new BadRequestException("At least one assert step is required");
        }
        String n = name == null ? "" : name.trim();
        if (n.isBlank()) {
            throw new BadRequestException("name is required");
        }
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("id", planId.trim());
        doc.put("name", n);
        doc.put("revision", revision);
        doc.put("schemaVersion", 1);
        doc.put("steps", normalized);
        doc.put("contentHash", contentHash(normalized));
        doc.put("updatedAt", Instant.now().toString());
        if (prev != null && prev.get("createdAt") != null) {
            doc.put("createdAt", prev.get("createdAt"));
        } else {
            doc.put("createdAt", Instant.now().toString());
        }
        return doc;
    }

    static List<Map<String, Object>> normalizeSteps(List<Map<String, Object>> steps) {
        if (steps == null || steps.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        java.util.Set<String> ids = new java.util.LinkedHashSet<>();
        int i = 0;
        for (Map<String, Object> raw : steps) {
            String type = str(raw.get("type")).toLowerCase(Locale.ROOT);
            if (!STEP_TYPES.contains(type)) {
                throw new BadRequestException("Invalid step type: " + type);
            }
            String text = str(raw.get("text"));
            if (text.isBlank()) {
                throw new BadRequestException("step text is required");
            }
            if (text.length() > 8000) {
                throw new BadRequestException("step text too long");
            }
            String sid = str(raw.get("id"));
            if (sid.isBlank()) {
                sid = "s" + (++i);
            }
            validateId(sid);
            if (!ids.add(sid)) {
                throw new BadRequestException("Duplicate step id: " + sid);
            }
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("id", sid);
            s.put("type", type);
            s.put("text", text);
            out.add(s);
        }
        return List.copyOf(out);
    }

    static String contentHash(List<Map<String, Object>> steps) {
        try {
            StringBuilder sb = new StringBuilder();
            for (Map<String, Object> s : steps) {
                sb.append(s.get("id")).append('|').append(s.get("type")).append('|').append(s.get("text")).append('\n');
            }
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return "";
        }
    }

    private void write(Path projectRoot, String planId, Map<String, Object> doc) throws IOException {
        Path file = file(projectRoot, planId);
        Files.createDirectories(file.getParent());
        Path tmp = Path.of(file.toString() + ".tmp");
        mapper.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), doc);
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private Optional<Map<String, Object>> read(Path projectRoot, String planId) throws IOException {
        validateId(planId);
        Path file = file(projectRoot, planId);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> raw = mapper.readValue(file.toFile(), Map.class);
        return Optional.of(raw);
    }

    private static Path file(Path projectRoot, String planId) {
        return projectRoot.resolve(DIR).resolve(planId.trim() + ".json");
    }

    private static String stem(Path p) {
        String n = p.getFileName().toString();
        return n.endsWith(".json") ? n.substring(0, n.length() - 5) : n;
    }

    static void validateId(String id) {
        if (id == null || id.isBlank() || !ID.matcher(id.trim()).matches()) {
            throw new BadRequestException("Invalid id");
        }
    }

    private static int toInt(Object o) {
        if (o instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (Exception e) {
            return 0;
        }
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }

    public static class BadRequestException extends RuntimeException {
        public BadRequestException(String message) { super(message); }
    }

    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String message) { super(message); }
    }

    public static class ConflictException extends RuntimeException {
        public ConflictException(String message) { super(message); }
    }
}
