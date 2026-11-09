package delivery.portal.service;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/** A4: approved run-memory snippets under {@code {projectRoot}/run-memory/{memId}.json}. */
public final class RunMemoryDiskStore {
    public static final String DIR = "run-memory";
    private static final Pattern ID = Pattern.compile("^mem_[A-Za-z0-9][A-Za-z0-9_-]{0,120}$");
    private static final java.util.Set<String> STATUSES = java.util.Set.of("proposed", "approved", "retired");

    private final ObjectMapper mapper;

    public RunMemoryDiskStore(ObjectMapper mapper) {
        this.mapper = mapper == null ? new ObjectMapper() : mapper;
    }

    public List<Map<String, Object>> list(Path projectRoot, String statusFilter) throws IOException {
        Path dir = projectRoot.resolve(DIR);
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        try (var stream = Files.list(dir)) {
            stream.filter(p -> p.getFileName().toString().endsWith(".json"))
                    .forEach(p -> {
                        try {
                            readFile(p).ifPresent(m -> {
                                if (statusFilter == null || statusFilter.isBlank()
                                        || statusFilter.equalsIgnoreCase(str(m.get("status")))) {
                                    out.add(m);
                                }
                            });
                        } catch (IOException ignored) {
                        }
                    });
        }
        return List.copyOf(out);
    }

    public Optional<Map<String, Object>> get(Path projectRoot, String memId) throws IOException {
        validateId(memId);
        Path file = file(projectRoot, memId);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        return readFile(file);
    }

    public Map<String, Object> propose(Path projectRoot, String memId, String summary, String locatorHint, String sourceJobId)
            throws IOException {
        validateId(memId);
        Path file = file(projectRoot, memId);
        if (Files.exists(file)) {
            throw new ConflictException("Memory already exists: " + memId);
        }
        Map<String, Object> doc = baseDoc(memId, summary, locatorHint, sourceJobId, "proposed");
        write(file, doc);
        return doc;
    }

    public Map<String, Object> setStatus(Path projectRoot, String memId, String status) throws IOException {
        validateId(memId);
        String st = status == null ? "" : status.trim().toLowerCase(Locale.ROOT);
        if (!STATUSES.contains(st)) {
            throw new BadRequestException("Invalid status: " + status);
        }
        Path file = file(projectRoot, memId);
        Map<String, Object> doc = readFile(file)
                .orElseThrow(() -> new NotFoundException("Memory not found: " + memId));
        doc.put("status", st);
        doc.put("updatedAt", Instant.now().toString());
        write(file, doc);
        return doc;
    }

    private Map<String, Object> baseDoc(String memId, String summary, String locatorHint, String sourceJobId, String status) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("id", memId.trim());
        doc.put("schemaVersion", 1);
        doc.put("status", status);
        doc.put("summary", FailurePackSanitizer.text(str(summary)));
        doc.put("locatorHint", FailurePackSanitizer.text(str(locatorHint)));
        doc.put("sourceJobId", sourceJobId == null ? "" : sourceJobId.trim());
        doc.put("memoryUsedCount", 0);
        doc.put("createdAt", Instant.now().toString());
        doc.put("updatedAt", Instant.now().toString());
        return doc;
    }

    private void write(Path file, Map<String, Object> doc) throws IOException {
        Files.createDirectories(file.getParent());
        Path tmp = Path.of(file.toString() + ".tmp");
        mapper.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), doc);
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private Optional<Map<String, Object>> readFile(Path file) throws IOException {
        @SuppressWarnings("unchecked")
        Map<String, Object> raw = mapper.readValue(file.toFile(), Map.class);
        return Optional.of(raw);
    }

    private static Path file(Path projectRoot, String memId) {
        return projectRoot.resolve(DIR).resolve(memId.trim() + ".json");
    }

    static void validateId(String id) {
        if (id == null || id.isBlank() || !ID.matcher(id.trim()).matches()) {
            throw new BadRequestException("Invalid id; use mem_*");
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
