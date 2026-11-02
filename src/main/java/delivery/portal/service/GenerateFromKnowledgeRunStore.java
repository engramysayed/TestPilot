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
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Disk store for Generate-from-knowledge runs under
 * {@code {projectRoot}/generate-from-knowledge/runs/{runId}.json}.
 * Phase 1 stub: create as QUEUED, optionally mark SUCCEEDED with a note.
 */
public final class GenerateFromKnowledgeRunStore {
    public static final String ROOT_DIR = "generate-from-knowledge";
    public static final String RUNS_DIR = "runs";

    public enum Status {
        QUEUED, RUNNING, SUCCEEDED, FAILED, CANCELLED;

        public static Status parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return QUEUED;
            }
            String t = raw.trim().toUpperCase(Locale.ROOT);
            for (Status s : values()) {
                if (s.name().equals(t)) {
                    return s;
                }
            }
            return QUEUED;
        }
    }

    private static final Pattern RUN_ID = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9_-]{0,127}$");
    private final ObjectMapper mapper;

    public GenerateFromKnowledgeRunStore(ObjectMapper mapper) {
        this.mapper = mapper == null ? new ObjectMapper() : mapper;
    }

    public GenerateFromKnowledgeRunStore() {
        this(new ObjectMapper());
    }

    public Path runsRoot(Path projectRoot) {
        return projectRoot.resolve(ROOT_DIR).resolve(RUNS_DIR);
    }

    public Map<String, Object> create(
            Path projectRoot,
            String projectId,
            List<String> packIds,
            String title,
            Integer batchSize
    ) throws IOException {
        if (packIds == null || packIds.isEmpty()) {
            throw new BadRequestException("packIds is required");
        }
        List<String> ids = new ArrayList<>();
        for (String id : packIds) {
            if (id != null && !id.isBlank()) {
                ids.add(id.trim());
            }
        }
        if (ids.isEmpty()) {
            throw new BadRequestException("packIds is required");
        }
        String runId = "gfk_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String now = Instant.now().toString();
        Map<String, Object> rec = new LinkedHashMap<>();
        rec.put("runId", runId);
        rec.put("projectId", projectId == null ? "" : projectId.trim());
        rec.put("packIds", ids);
        rec.put("status", Status.QUEUED.name());
        rec.put("createdAt", now);
        rec.put("updatedAt", now);
        if (title != null && !title.isBlank()) {
            rec.put("title", title.trim());
        }
        if (batchSize != null) {
            rec.put("batchSize", KnowledgePackDiskStore.clampBatch(batchSize));
        }
        // Phase 1 stub: mark SUCCEEDED immediately with an explanatory note.
        // Agent walk / real generation lands in a later slice.
        rec.put("status", Status.SUCCEEDED.name());
        rec.put("note", "Phase 1 stub: run recorded; agent walk not executed yet.");
        rec.put("updatedAt", Instant.now().toString());

        Path dir = runsRoot(projectRoot);
        Files.createDirectories(dir);
        Path file = dir.resolve(runId + ".json");
        mapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), rec);
        return rec;
    }

    public Optional<Map<String, Object>> load(Path projectRoot, String runId) throws IOException {
        validateRunId(runId);
        Path file = runsRoot(projectRoot).resolve(runId.trim() + ".json");
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> rec = mapper.readValue(file.toFile(), Map.class);
        return Optional.of(new LinkedHashMap<>(rec));
    }

    public static void validateRunId(String runId) {
        if (runId == null || runId.isBlank() || !RUN_ID.matcher(runId.trim()).matches()) {
            throw new BadRequestException("Invalid runId");
        }
        String lower = runId.trim().toLowerCase(Locale.ROOT);
        if (lower.contains("..") || lower.contains("/") || lower.contains("\\")) {
            throw new BadRequestException("Invalid runId");
        }
    }

    public static class BadRequestException extends RuntimeException {
        public BadRequestException(String message) {
            super(message);
        }
    }
}
