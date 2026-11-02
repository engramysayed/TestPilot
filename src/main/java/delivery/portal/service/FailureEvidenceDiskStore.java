package delivery.portal.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import delivery.ir.EvidencePaths;
import delivery.ir.TcIdentity;

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
 * Failure replay evidence under existing execute-runs evidence folders:
 * {@code execute-runs/{jobId}/evidence/{tcFolder}/failure/{stepIndex}/}.
 */
public final class FailureEvidenceDiskStore {
    public static final String FAILURE_DIR = "failure";
    public static final String META_JSON = "meta.json";
    public static final String SCREEN_PNG = "screen.png";
    public static final String NETWORK_JSON = "network.json";
    public static final String CONSOLE_JSON = "console.json";

    private static final Pattern SAFE_ID = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9_.~-]{0,127}$");

    private final ObjectMapper mapper;

    public FailureEvidenceDiskStore(ObjectMapper mapper) {
        this.mapper = mapper == null ? new ObjectMapper() : mapper;
    }

    public FailureEvidenceDiskStore() {
        this(new ObjectMapper());
    }

    public Path evidenceRoot(Path projectRoot, String jobId) {
        validateJobId(jobId);
        return projectRoot.resolve("execute-runs").resolve(jobId.trim()).resolve("evidence");
    }

    public Path failureDir(Path projectRoot, String jobId, String tcFolder, int stepIndex) {
        validateJobId(jobId);
        validateTcFolder(tcFolder);
        if (stepIndex < 0) {
            throw new BadPathException("Invalid stepIndex");
        }
        return evidenceRoot(projectRoot, jobId)
                .resolve(tcFolder.trim())
                .resolve(FAILURE_DIR)
                .resolve(Integer.toString(stepIndex));
    }

    /**
     * Persist failure evidence. Writes meta.json always; omits missing blobs.
     * When {@code tcFolder} is null, uses {@link TcIdentity#storageKey(String)}.
     */
    public Map<String, Object> writeFailure(
            Path projectRoot,
            String jobId,
            String tcId,
            String tcFolder,
            int stepIndex,
            String reason,
            String pageUrl,
            String source,
            byte[] screenPng,
            Object networkJson,
            Object consoleJson
    ) throws IOException {
        validateJobId(jobId);
        if (tcId == null || tcId.isBlank()) {
            throw new BadRequestException("tcId is required");
        }
        if (stepIndex < 0) {
            throw new BadPathException("Invalid stepIndex");
        }
        String folder = (tcFolder == null || tcFolder.isBlank())
                ? TcIdentity.storageKey(tcId.trim())
                : tcFolder.trim();
        validateTcFolder(folder);

        Path dir = failureDir(projectRoot, jobId, folder, stepIndex);
        Files.createDirectories(dir);

        boolean hasScreen = screenPng != null && screenPng.length > 0;
        if (hasScreen) {
            Files.write(dir.resolve(SCREEN_PNG), screenPng);
        }
        boolean hasNetwork = networkJson != null;
        if (hasNetwork) {
            mapper.writerWithDefaultPrettyPrinter().writeValue(dir.resolve(NETWORK_JSON).toFile(), networkJson);
        }
        boolean hasConsole = consoleJson != null;
        if (hasConsole) {
            mapper.writerWithDefaultPrettyPrinter().writeValue(dir.resolve(CONSOLE_JSON).toFile(), consoleJson);
        }

        String src = (source == null || source.isBlank()) ? "execute" : source.trim().toLowerCase(Locale.ROOT);
        if (!"prove".equals(src) && !"execute".equals(src)) {
            src = "execute";
        }

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("jobId", jobId.trim());
        meta.put("tcId", tcId.trim());
        meta.put("stepIndex", stepIndex);
        meta.put("failedAt", Instant.now().toString());
        meta.put("reason", reason == null ? "" : reason);
        meta.put("pageUrl", pageUrl == null ? "" : pageUrl);
        meta.put("hasScreen", hasScreen);
        meta.put("hasNetwork", hasNetwork);
        meta.put("hasConsole", hasConsole);
        meta.put("source", src);
        meta.put("tcFolder", folder);
        mapper.writerWithDefaultPrettyPrinter().writeValue(dir.resolve(META_JSON).toFile(), meta);
        return enrich(meta, dir);
    }

    public List<Map<String, Object>> listFailures(Path projectRoot, String jobId) throws IOException {
        Path evidence = evidenceRoot(projectRoot, jobId);
        if (!Files.isDirectory(evidence)) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        try (var tcDirs = Files.list(evidence)) {
            for (Path tcDir : tcDirs.filter(Files::isDirectory).sorted().toList()) {
                Path failureRoot = tcDir.resolve(FAILURE_DIR);
                if (!Files.isDirectory(failureRoot)) {
                    continue;
                }
                try (var steps = Files.list(failureRoot)) {
                    for (Path stepDir : steps.filter(Files::isDirectory).toList()) {
                        readMeta(stepDir).ifPresent(m -> out.add(enrich(m, stepDir)));
                    }
                }
            }
        }
        out.sort(Comparator
                .comparing((Map<String, Object> m) -> str(m.get("tcId")))
                .thenComparingInt(m -> toInt(m.get("stepIndex"))));
        return List.copyOf(out);
    }

    public List<Map<String, Object>> listFailuresForTc(Path projectRoot, String jobId, String tcId)
            throws IOException {
        if (tcId == null || tcId.isBlank()) {
            throw new BadRequestException("tcId is required");
        }
        String want = tcId.trim();
        String wantKey = TcIdentity.storageKey(want);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : listFailures(projectRoot, jobId)) {
            if (want.equals(str(row.get("tcId"))) || wantKey.equals(str(row.get("tcFolder")))) {
                out.add(row);
            }
        }
        return List.copyOf(out);
    }

    public Optional<Map<String, Object>> getFailure(
            Path projectRoot, String jobId, String tcId, int stepIndex
    ) throws IOException {
        Optional<Path> dir = resolveFailureDir(projectRoot, jobId, tcId, stepIndex);
        if (dir.isEmpty()) {
            return Optional.empty();
        }
        return readMeta(dir.get()).map(m -> enrich(m, dir.get()));
    }

    public Optional<Path> resolveScreen(Path projectRoot, String jobId, String tcId, int stepIndex)
            throws IOException {
        return resolveBlob(projectRoot, jobId, tcId, stepIndex, SCREEN_PNG);
    }

    public Optional<Path> resolveNetwork(Path projectRoot, String jobId, String tcId, int stepIndex)
            throws IOException {
        return resolveBlob(projectRoot, jobId, tcId, stepIndex, NETWORK_JSON);
    }

    public Optional<Path> resolveConsole(Path projectRoot, String jobId, String tcId, int stepIndex)
            throws IOException {
        return resolveBlob(projectRoot, jobId, tcId, stepIndex, CONSOLE_JSON);
    }

    private Optional<Path> resolveBlob(
            Path projectRoot, String jobId, String tcId, int stepIndex, String fileName
    ) throws IOException {
        Optional<Path> dir = resolveFailureDir(projectRoot, jobId, tcId, stepIndex);
        if (dir.isEmpty()) {
            return Optional.empty();
        }
        Path file = dir.get().resolve(fileName);
        return Files.isRegularFile(file) ? Optional.of(file) : Optional.empty();
    }

    Optional<Path> resolveFailureDir(Path projectRoot, String jobId, String tcId, int stepIndex)
            throws IOException {
        if (stepIndex < 0) {
            throw new BadPathException("Invalid stepIndex");
        }
        Path evidence = evidenceRoot(projectRoot, jobId);
        if (!Files.isDirectory(evidence)) {
            return Optional.empty();
        }
        Path preferred = EvidencePaths.resolve(evidence, tcId).resolve(FAILURE_DIR)
                .resolve(Integer.toString(stepIndex));
        if (Files.isDirectory(preferred) && Files.isRegularFile(preferred.resolve(META_JSON))) {
            return Optional.of(preferred);
        }
        for (Map<String, Object> row : listFailuresForTc(projectRoot, jobId, tcId)) {
            if (toInt(row.get("stepIndex")) == stepIndex && row.get("_dir") instanceof String d) {
                Path p = Path.of(d);
                if (Files.isDirectory(p)) {
                    return Optional.of(p);
                }
            }
        }
        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    private Optional<Map<String, Object>> readMeta(Path stepDir) {
        Path meta = stepDir.resolve(META_JSON);
        if (!Files.isRegularFile(meta)) {
            return Optional.empty();
        }
        try {
            Map<String, Object> raw = mapper.readValue(meta.toFile(), Map.class);
            return Optional.of(normalizeMeta(raw, stepDir));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private Map<String, Object> normalizeMeta(Map<String, Object> raw, Path stepDir) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("jobId", str(raw.get("jobId")));
        m.put("tcId", str(raw.get("tcId")));
        m.put("stepIndex", toInt(raw.get("stepIndex")));
        m.put("failedAt", str(raw.get("failedAt")));
        m.put("reason", str(raw.get("reason")));
        m.put("pageUrl", str(raw.get("pageUrl")));
        boolean hasScreen = bool(raw.get("hasScreen"), Files.isRegularFile(stepDir.resolve(SCREEN_PNG)));
        boolean hasNetwork = bool(raw.get("hasNetwork"), Files.isRegularFile(stepDir.resolve(NETWORK_JSON)));
        boolean hasConsole = bool(raw.get("hasConsole"), Files.isRegularFile(stepDir.resolve(CONSOLE_JSON)));
        m.put("hasScreen", hasScreen);
        m.put("hasNetwork", hasNetwork);
        m.put("hasConsole", hasConsole);
        String source = str(raw.get("source"));
        if (source.isBlank()) {
            source = "execute";
        }
        m.put("source", source);
        String folder = str(raw.get("tcFolder"));
        if (folder.isBlank() && stepDir.getParent() != null && stepDir.getParent().getParent() != null) {
            folder = stepDir.getParent().getParent().getFileName().toString();
        }
        m.put("tcFolder", folder);
        return m;
    }

    private Map<String, Object> enrich(Map<String, Object> meta, Path stepDir) {
        Map<String, Object> m = new LinkedHashMap<>(meta);
        m.put("_dir", stepDir.toAbsolutePath().normalize().toString());
        String jobId = str(m.get("jobId"));
        String tcId = str(m.get("tcId"));
        int step = toInt(m.get("stepIndex"));
        String base = "/api/execute-runs/" + jobId + "/failures/" + tcId + "/" + step;
        Map<String, Object> urls = new LinkedHashMap<>();
        urls.put("self", base);
        urls.put("screen", base + "/screen");
        urls.put("network", base + "/network");
        urls.put("console", base + "/console");
        m.put("urls", urls);
        return m;
    }

    public static Map<String, Object> toApiJson(Map<String, Object> row) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (String k : List.of(
                "jobId", "tcId", "stepIndex", "failedAt", "reason", "pageUrl",
                "hasScreen", "hasNetwork", "hasConsole", "source", "tcFolder", "urls")) {
            if (row.containsKey(k)) {
                m.put(k, row.get(k));
            }
        }
        return m;
    }

    static void validateJobId(String jobId) {
        if (jobId == null || jobId.isBlank() || !SAFE_ID.matcher(jobId.trim()).matches()) {
            throw new BadPathException("Invalid jobId");
        }
        denyTraversal(jobId);
    }

    static void validateTcFolder(String tcFolder) {
        if (tcFolder == null || tcFolder.isBlank() || !SAFE_ID.matcher(tcFolder.trim()).matches()) {
            throw new BadPathException("Invalid tcFolder");
        }
        denyTraversal(tcFolder);
    }

    static void denyTraversal(String value) {
        String lower = value.trim().toLowerCase(Locale.ROOT);
        if (lower.contains("..") || lower.contains("/") || lower.contains("\\")) {
            throw new BadPathException("Invalid path segment");
        }
    }

    private static boolean bool(Object o, boolean fallback) {
        if (o instanceof Boolean b) {
            return b;
        }
        if (o == null) {
            return fallback;
        }
        return Boolean.parseBoolean(String.valueOf(o));
    }

    private static int toInt(Object o) {
        if (o instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (Exception e) {
            return -1;
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
}
