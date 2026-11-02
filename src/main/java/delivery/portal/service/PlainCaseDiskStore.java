package delivery.portal.service;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Disk store for plain-English cases under {@code {projectRoot}/plain-cases/{id}.json}.
 */
public final class PlainCaseDiskStore {
    public static final String ROOT_DIR = "plain-cases";

    private static final Pattern CASE_ID = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9_-]{0,127}$");
    private final ObjectMapper mapper;

    public PlainCaseDiskStore(ObjectMapper mapper) {
        this.mapper = mapper == null ? new ObjectMapper() : mapper;
    }

    public PlainCaseDiskStore() {
        this(new ObjectMapper());
    }

    public Path casesRoot(Path projectRoot) {
        return projectRoot.resolve(ROOT_DIR);
    }

    public Path casePath(Path projectRoot, String caseId) {
        validateCaseId(caseId);
        return casesRoot(projectRoot).resolve(caseId.trim() + ".json");
    }

    public List<Map<String, Object>> list(Path projectRoot) throws IOException {
        Path root = casesRoot(projectRoot);
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        try (var stream = Files.list(root)) {
            stream.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .forEach(path -> {
                        try {
                            load(projectRoot, stripJson(path.getFileName().toString())).ifPresent(out::add);
                        } catch (IOException ignored) {
                        }
                    });
        }
        return List.copyOf(out);
    }

    public Optional<Map<String, Object>> load(Path projectRoot, String caseId) throws IOException {
        validateCaseId(caseId);
        Path path = casePath(projectRoot, caseId);
        if (!Files.isRegularFile(path)) {
            return Optional.empty();
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> raw = mapper.readValue(path.toFile(), Map.class);
        return Optional.of(normalize(raw));
    }

    public Map<String, Object> saveNew(
            Path projectRoot,
            String caseId,
            String title,
            List<String> lines
    ) throws IOException {
        validateCaseId(caseId);
        List<String> cleaned = cleanLines(lines);
        if (cleaned.isEmpty()) {
            throw new BadRequestException("lines must contain at least one non-blank entry");
        }
        Path path = casePath(projectRoot, caseId);
        if (Files.exists(path)) {
            throw new AlreadyExistsException("Plain case already exists: " + caseId);
        }
        Files.createDirectories(path.getParent());
        String now = Instant.now().toString();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", caseId.trim());
        body.put("title", title == null ? "" : title.trim());
        body.put("lines", cleaned);
        body.put("createdAt", now);
        body.put("updatedAt", now);
        mapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), body);
        return Map.copyOf(body);
    }

    public Map<String, Object> update(
            Path projectRoot,
            String caseId,
            String title,
            List<String> lines
    ) throws IOException {
        validateCaseId(caseId);
        Path path = casePath(projectRoot, caseId);
        if (!Files.isRegularFile(path)) {
            throw new NotFoundException("Plain case not found: " + caseId);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> raw = new LinkedHashMap<>(mapper.readValue(path.toFile(), Map.class));
        boolean changed = false;
        if (title != null) {
            raw.put("title", title.trim());
            changed = true;
        }
        if (lines != null) {
            List<String> cleaned = cleanLines(lines);
            if (cleaned.isEmpty()) {
                throw new BadRequestException("lines must contain at least one non-blank entry");
            }
            raw.put("lines", cleaned);
            changed = true;
        }
        if (!changed) {
            return normalize(raw);
        }
        raw.put("id", caseId.trim());
        raw.put("updatedAt", Instant.now().toString());
        if (!raw.containsKey("createdAt") || String.valueOf(raw.get("createdAt")).isBlank()) {
            raw.put("createdAt", raw.get("updatedAt"));
        }
        mapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), normalize(raw));
        return normalize(raw);
    }

    static List<String> cleanLines(List<String> lines) {
        if (lines == null) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String line : lines) {
            if (line == null) {
                continue;
            }
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                out.add(trimmed);
            }
        }
        return List.copyOf(out);
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> normalize(Map<String, Object> raw) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", str(raw.get("id")));
        m.put("title", str(raw.get("title")));
        Object linesObj = raw.get("lines");
        List<String> lines = new ArrayList<>();
        if (linesObj instanceof List<?> list) {
            for (Object o : list) {
                if (o == null) {
                    continue;
                }
                String s = String.valueOf(o).trim();
                if (!s.isEmpty()) {
                    lines.add(s);
                }
            }
        }
        m.put("lines", List.copyOf(lines));
        m.put("createdAt", str(raw.get("createdAt")));
        m.put("updatedAt", str(raw.get("updatedAt")));
        return m;
    }

    public static void validateCaseId(String caseId) {
        if (caseId == null || caseId.isBlank() || !CASE_ID.matcher(caseId.trim()).matches()) {
            throw new BadPathException("Invalid plain case id");
        }
        String lower = caseId.trim().toLowerCase(Locale.ROOT);
        if (lower.contains("..") || lower.contains("/") || lower.contains("\\")) {
            throw new BadPathException("Invalid plain case id");
        }
    }

    private static String stripJson(String fileName) {
        if (fileName != null && fileName.endsWith(".json")) {
            return fileName.substring(0, fileName.length() - 5);
        }
        return fileName;
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    public static class BadPathException extends RuntimeException {
        public BadPathException(String message) {
            super(message);
        }
    }

    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String message) {
            super(message);
        }
    }

    public static class AlreadyExistsException extends RuntimeException {
        public AlreadyExistsException(String message) {
            super(message);
        }
    }

    public static class BadRequestException extends RuntimeException {
        public BadRequestException(String message) {
            super(message);
        }
    }
}
