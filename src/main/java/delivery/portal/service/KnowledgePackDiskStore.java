package delivery.portal.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import delivery.knowledge.KnowledgePack;
import delivery.knowledge.KnowledgePackSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
 * Disk store for knowledge packs under {@code {projectRoot}/knowledge-packs/{packId}/}.
 * Files: {@code pack.md} (body) + {@code pack.json} (metadata). No separate DB.
 */
public final class KnowledgePackDiskStore {
    public static final String ROOT_DIR = "knowledge-packs";
    public static final String PACK_MD = "pack.md";
    public static final String PACK_JSON = "pack.json";

    private static final Pattern PACK_ID = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9_-]{0,127}$");
    private final ObjectMapper mapper;

    public KnowledgePackDiskStore(ObjectMapper mapper) {
        this.mapper = mapper == null ? new ObjectMapper() : mapper;
    }

    public KnowledgePackDiskStore() {
        this(new ObjectMapper());
    }

    public Path packsRoot(Path projectRoot) {
        return projectRoot.resolve(ROOT_DIR);
    }

    public Path packDir(Path projectRoot, String packId) {
        validatePackId(packId);
        return packsRoot(projectRoot).resolve(packId.trim());
    }

    public List<KnowledgePack> list(Path projectRoot, boolean includeMarkdown) throws IOException {
        Path root = packsRoot(projectRoot);
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        List<KnowledgePack> out = new ArrayList<>();
        try (var stream = Files.list(root)) {
            stream.filter(Files::isDirectory)
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .forEach(dir -> {
                        try {
                            load(projectRoot, dir.getFileName().toString(), includeMarkdown).ifPresent(out::add);
                        } catch (IOException ignored) {
                        }
                    });
        }
        return List.copyOf(out);
    }

    public Optional<KnowledgePack> load(Path projectRoot, String packId, boolean includeMarkdown) throws IOException {
        validatePackId(packId);
        Path dir = packDir(projectRoot, packId);
        Path metaPath = dir.resolve(PACK_JSON);
        if (!Files.isRegularFile(metaPath)) {
            return Optional.empty();
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> meta = mapper.readValue(metaPath.toFile(), Map.class);
        String markdown = "";
        Path mdPath = dir.resolve(PACK_MD);
        if (includeMarkdown && Files.isRegularFile(mdPath)) {
            markdown = Files.readString(mdPath, StandardCharsets.UTF_8);
        }
        String rel = ROOT_DIR + "/" + packId.trim() + "/" + PACK_MD;
        return Optional.of(fromMeta(meta, markdown, rel));
    }

    public KnowledgePack saveNew(
            Path projectRoot,
            String projectId,
            String packId,
            String title,
            KnowledgePackSource source,
            int batchSize,
            String markdown
    ) throws IOException {
        validatePackId(packId);
        Path dir = packDir(projectRoot, packId);
        if (Files.exists(dir)) {
            throw new AlreadyExistsException("Pack already exists: " + packId);
        }
        Files.createDirectories(dir);
        String now = Instant.now().toString();
        String body = markdown == null ? "" : markdown;
        Files.writeString(dir.resolve(PACK_MD), body, StandardCharsets.UTF_8);
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("packId", packId.trim());
        meta.put("projectId", projectId == null ? "" : projectId.trim());
        meta.put("title", title == null ? "" : title.trim());
        meta.put("source", (source == null ? KnowledgePackSource.LIBRARY : source).name());
        meta.put("batchSize", clampBatch(batchSize));
        meta.put("revision", 1);
        meta.put("markdownPath", ROOT_DIR + "/" + packId.trim() + "/" + PACK_MD);
        meta.put("createdAt", now);
        meta.put("updatedAt", now);
        mapper.writerWithDefaultPrettyPrinter().writeValue(dir.resolve(PACK_JSON).toFile(), meta);
        return fromMeta(meta, body, String.valueOf(meta.get("markdownPath")));
    }

    public KnowledgePack update(
            Path projectRoot,
            String packId,
            String title,
            Integer batchSize,
            String markdown
    ) throws IOException {
        validatePackId(packId);
        Path dir = packDir(projectRoot, packId);
        Path metaPath = dir.resolve(PACK_JSON);
        if (!Files.isRegularFile(metaPath)) {
            throw new NotFoundException("Pack not found: " + packId);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> meta = new LinkedHashMap<>(mapper.readValue(metaPath.toFile(), Map.class));
        Path mdPath = dir.resolve(PACK_MD);
        String body = Files.isRegularFile(mdPath)
                ? Files.readString(mdPath, StandardCharsets.UTF_8)
                : "";
        boolean changed = false;
        if (title != null) {
            meta.put("title", title.trim());
            changed = true;
        }
        if (batchSize != null) {
            meta.put("batchSize", clampBatch(batchSize));
            changed = true;
        }
        if (markdown != null) {
            body = markdown;
            Files.writeString(mdPath, body, StandardCharsets.UTF_8);
            changed = true;
        }
        if (!changed) {
            return fromMeta(meta, body, String.valueOf(meta.getOrDefault("markdownPath", "")));
        }
        int rev = 1;
        Object revObj = meta.get("revision");
        if (revObj instanceof Number n) {
            rev = Math.max(1, n.intValue());
        }
        meta.put("revision", rev + 1);
        meta.put("updatedAt", Instant.now().toString());
        mapper.writerWithDefaultPrettyPrinter().writeValue(metaPath.toFile(), meta);
        return fromMeta(meta, body, String.valueOf(meta.getOrDefault("markdownPath", "")));
    }

    static KnowledgePack fromMeta(Map<String, Object> meta, String markdown, String markdownPath) {
        String packId = str(meta.get("packId"));
        String projectId = str(meta.get("projectId"));
        String title = str(meta.get("title"));
        KnowledgePackSource source = KnowledgePackSource.parse(str(meta.get("source")));
        int batchSize = 30;
        Object b = meta.get("batchSize");
        if (b instanceof Number n) {
            batchSize = n.intValue();
        }
        int revision = 1;
        Object r = meta.get("revision");
        if (r instanceof Number n) {
            revision = n.intValue();
        }
        String path = markdownPath == null || markdownPath.isBlank()
                ? str(meta.get("markdownPath"))
                : markdownPath;
        return new KnowledgePack(
                packId,
                projectId,
                title,
                source,
                clampBatch(batchSize),
                Math.max(1, revision),
                markdown == null ? "" : markdown,
                path,
                str(meta.get("createdAt")),
                str(meta.get("updatedAt"))
        );
    }

    public static int clampBatch(int batchSize) {
        if (batchSize < 1) {
            return 30;
        }
        return Math.min(500, batchSize);
    }

    public static void validatePackId(String packId) {
        if (packId == null || packId.isBlank() || !PACK_ID.matcher(packId.trim()).matches()) {
            throw new BadPathException("Invalid packId");
        }
        String lower = packId.trim().toLowerCase(Locale.ROOT);
        if (lower.contains("..") || lower.contains("/") || lower.contains("\\")) {
            throw new BadPathException("Invalid packId");
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
}
