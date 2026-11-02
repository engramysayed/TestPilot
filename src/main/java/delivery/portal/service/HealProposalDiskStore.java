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
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Heal proposals on disk: {@code {projectRoot}/heal-proposals/{proposalId}.json}.
 * AE emits pending proposals only; Accept/Reject mutate status (and Accept patches Java elsewhere).
 */
public final class HealProposalDiskStore {
    public static final String ROOT_DIR = "heal-proposals";
    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_ACCEPTED = "accepted";
    public static final String STATUS_REJECTED = "rejected";

    private static final Pattern ID = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9_-]{0,127}$");

    private final ObjectMapper mapper;

    public HealProposalDiskStore(ObjectMapper mapper) {
        this.mapper = mapper == null ? new ObjectMapper() : mapper;
    }

    public HealProposalDiskStore() {
        this(new ObjectMapper());
    }

    public Path root(Path projectRoot) {
        return projectRoot.resolve(ROOT_DIR);
    }

    public Path proposalPath(Path projectRoot, String proposalId) {
        validateId(proposalId, "proposalId");
        return root(projectRoot).resolve(proposalId.trim() + ".json");
    }

    public List<Map<String, Object>> list(Path projectRoot, String statusFilter) throws IOException {
        Path dir = root(projectRoot);
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        String want = statusFilter == null || statusFilter.isBlank()
                ? null
                : statusFilter.trim().toLowerCase(Locale.ROOT);
        List<Map<String, Object>> out = new ArrayList<>();
        try (var stream = Files.list(dir)) {
            stream.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .forEach(path -> {
                        try {
                            load(projectRoot, stripJson(path.getFileName().toString())).ifPresent(row -> {
                                if (want == null || want.equals(str(row.get("status")))) {
                                    out.add(row);
                                }
                            });
                        } catch (IOException ignored) {
                        }
                    });
        }
        return List.copyOf(out);
    }

    public Optional<Map<String, Object>> load(Path projectRoot, String proposalId) throws IOException {
        validateId(proposalId, "proposalId");
        Path path = proposalPath(projectRoot, proposalId);
        if (!Files.isRegularFile(path)) {
            return Optional.empty();
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> raw = mapper.readValue(path.toFile(), Map.class);
        return Optional.of(normalize(raw));
    }

    public Map<String, Object> create(Path projectRoot, Map<String, Object> input) throws IOException {
        if (input == null) {
            throw new BadRequestException("body is required");
        }
        String pageClass = str(input.get("pageClass"));
        if (pageClass.isBlank()) {
            throw new BadRequestException("pageClass is required");
        }
        Object after = input.get("locatorAfter");
        if (!(after instanceof Map<?, ?>)) {
            throw new BadRequestException("locatorAfter is required");
        }
        String proposalId = str(input.get("proposalId"));
        if (proposalId.isBlank()) {
            proposalId = UUID.randomUUID().toString().replace("-", "");
        }
        validateId(proposalId, "proposalId");
        Path path = proposalPath(projectRoot, proposalId);
        if (Files.exists(path)) {
            throw new AlreadyExistsException("Heal proposal already exists: " + proposalId);
        }
        Files.createDirectories(path.getParent());
        String now = Instant.now().toString();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("proposalId", proposalId);
        body.put("projectId", str(input.get("projectId")));
        body.put("jobId", str(input.get("jobId")));
        body.put("tcId", str(input.get("tcId")));
        body.put("stepIndex", toInt(input.get("stepIndex"), 0));
        body.put("pageClass", pageClass.trim());
        body.put("locatorBefore", asLocatorMap(input.get("locatorBefore")));
        body.put("locatorAfter", asLocatorMap(after));
        String patchKind = str(input.get("patchKind"));
        body.put("patchKind", patchKind.isBlank() ? "locator" : patchKind.trim());
        body.put("tier", str(input.get("tier")));
        body.put("reason", str(input.get("reason")));
        body.put("status", STATUS_PENDING);
        body.put("createdAt", now);
        body.put("decidedAt", null);
        Object evidenceRel = input.get("evidenceRel");
        body.put("evidenceRel", evidenceRel == null || str(evidenceRel).isBlank() ? null : str(evidenceRel));
        mapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), body);
        return normalize(body);
    }

    public Map<String, Object> markStatus(Path projectRoot, String proposalId, String status)
            throws IOException {
        validateId(proposalId, "proposalId");
        String st = status == null ? "" : status.trim().toLowerCase(Locale.ROOT);
        if (!STATUS_ACCEPTED.equals(st) && !STATUS_REJECTED.equals(st)) {
            throw new BadRequestException("status must be accepted or rejected");
        }
        Path path = proposalPath(projectRoot, proposalId);
        if (!Files.isRegularFile(path)) {
            throw new NotFoundException("Heal proposal not found: " + proposalId);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> raw = new LinkedHashMap<>(mapper.readValue(path.toFile(), Map.class));
        String current = str(raw.get("status")).toLowerCase(Locale.ROOT);
        if (!STATUS_PENDING.equals(current) && !current.isBlank()) {
            throw new ConflictException("Heal proposal already decided: " + current);
        }
        raw.put("status", st);
        raw.put("decidedAt", Instant.now().toString());
        Map<String, Object> normalized = normalize(raw);
        mapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), normalized);
        return normalized;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> asLocatorMap(Object o) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (o instanceof Map<?, ?> map) {
            m.put("strategy", str(map.get("strategy")));
            m.put("value", str(map.get("value")));
        } else {
            m.put("strategy", "");
            m.put("value", "");
        }
        return m;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> normalize(Map<String, Object> raw) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("proposalId", str(raw.get("proposalId")));
        m.put("projectId", str(raw.get("projectId")));
        m.put("jobId", str(raw.get("jobId")));
        m.put("tcId", str(raw.get("tcId")));
        m.put("stepIndex", toInt(raw.get("stepIndex"), 0));
        m.put("pageClass", str(raw.get("pageClass")));
        m.put("locatorBefore", asLocatorMap(raw.get("locatorBefore")));
        m.put("locatorAfter", asLocatorMap(raw.get("locatorAfter")));
        String patchKind = str(raw.get("patchKind"));
        m.put("patchKind", patchKind.isBlank() ? "locator" : patchKind);
        m.put("tier", str(raw.get("tier")));
        m.put("reason", str(raw.get("reason")));
        String status = str(raw.get("status")).toLowerCase(Locale.ROOT);
        if (status.isBlank()) {
            status = STATUS_PENDING;
        }
        m.put("status", status);
        m.put("createdAt", str(raw.get("createdAt")));
        Object decided = raw.get("decidedAt");
        m.put("decidedAt", decided == null || str(decided).isBlank() ? null : str(decided));
        Object evidenceRel = raw.get("evidenceRel");
        m.put("evidenceRel", evidenceRel == null || str(evidenceRel).isBlank() ? null : str(evidenceRel));
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

    private static String stripJson(String fileName) {
        if (fileName != null && fileName.endsWith(".json")) {
            return fileName.substring(0, fileName.length() - 5);
        }
        return fileName;
    }

    private static int toInt(Object o, int fallback) {
        if (o instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (Exception e) {
            return fallback;
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

    public static class AlreadyExistsException extends RuntimeException {
        public AlreadyExistsException(String message) {
            super(message);
        }
    }

    public static class ConflictException extends RuntimeException {
        public ConflictException(String message) {
            super(message);
        }
    }
}
