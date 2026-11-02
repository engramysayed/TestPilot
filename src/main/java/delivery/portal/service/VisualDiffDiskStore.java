package delivery.portal.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import delivery.effect.ScreenshotDiff;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
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
 * Project-disk layout (locked interim contract for AE/FE):
 * <pre>
 * visual-baselines/{caseKey}/{viewport}/baseline.png + meta.json
 * visual-diffs/{runId}/{caseKey}/{viewport}/actual.png, diff.png?, result.json
 * </pre>
 */
public final class VisualDiffDiskStore {
    public static final String BASELINES_DIR = "visual-baselines";
    public static final String DIFFS_DIR = "visual-diffs";
    public static final String BASELINE_PNG = "baseline.png";
    public static final String META_JSON = "meta.json";
    public static final String ACTUAL_PNG = "actual.png";
    public static final String DIFF_PNG = "diff.png";
    public static final String RESULT_JSON = "result.json";

    public static final int DEFAULT_BLOCK = 8;
    public static final double DEFAULT_LUMA_THRESHOLD = 8.0;
    public static final double DEFAULT_MAX_CHANGED_FRACTION = 0.02;

    private static final Pattern KEY = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9_-]{0,127}$");

    private final ObjectMapper mapper;

    public VisualDiffDiskStore(ObjectMapper mapper) {
        this.mapper = mapper == null ? new ObjectMapper() : mapper;
    }

    public VisualDiffDiskStore() {
        this(new ObjectMapper());
    }

    public Path baselinesRoot(Path projectRoot) {
        return projectRoot.resolve(BASELINES_DIR);
    }

    public Path diffsRoot(Path projectRoot) {
        return projectRoot.resolve(DIFFS_DIR);
    }

    public Path baselineDir(Path projectRoot, String caseKey, String viewport) {
        validateKey(caseKey, "caseKey");
        validateKey(viewport, "viewport");
        return baselinesRoot(projectRoot).resolve(caseKey.trim()).resolve(viewport.trim());
    }

    public Path diffDir(Path projectRoot, String runId, String caseKey, String viewport) {
        validateKey(runId, "runId");
        validateKey(caseKey, "caseKey");
        validateKey(viewport, "viewport");
        return diffsRoot(projectRoot).resolve(runId.trim()).resolve(caseKey.trim()).resolve(viewport.trim());
    }

    public Map<String, Object> saveBaseline(
            Path projectRoot,
            String caseKey,
            String viewport,
            byte[] pngBytes,
            String sourceRunId,
            Integer block,
            Double lumaThreshold,
            Double maxChangedFraction
    ) throws IOException {
        validateKey(caseKey, "caseKey");
        validateKey(viewport, "viewport");
        if (sourceRunId != null && !sourceRunId.isBlank()) {
            validateKey(sourceRunId, "runId");
        }
        if (pngBytes == null || pngBytes.length == 0) {
            throw new BadUploadException("Baseline image is empty");
        }
        byte[] png = requirePng(pngBytes);
        Path dir = baselineDir(projectRoot, caseKey, viewport);
        Files.createDirectories(dir);
        Files.write(dir.resolve(BASELINE_PNG), png);

        Instant now = Instant.now();
        Map<String, Object> existing = readMeta(dir).orElse(Map.of());
        String createdAt = stringOr(existing.get("createdAt"), now.toString());

        BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("caseKey", caseKey.trim());
        meta.put("viewport", viewport.trim());
        meta.put("createdAt", createdAt);
        meta.put("updatedAt", now.toString());
        if (sourceRunId != null && !sourceRunId.isBlank()) {
            meta.put("sourceRunId", sourceRunId.trim());
        }
        if (img != null) {
            meta.put("width", img.getWidth());
            meta.put("height", img.getHeight());
        }
        meta.put("block", block == null ? DEFAULT_BLOCK : block);
        meta.put("lumaThreshold", lumaThreshold == null ? DEFAULT_LUMA_THRESHOLD : lumaThreshold);
        meta.put("maxChangedFraction",
                maxChangedFraction == null ? DEFAULT_MAX_CHANGED_FRACTION : maxChangedFraction);
        mapper.writerWithDefaultPrettyPrinter().writeValue(dir.resolve(META_JSON).toFile(), meta);
        return meta;
    }

    public Optional<Map<String, Object>> loadBaselineMeta(Path projectRoot, String caseKey, String viewport) throws IOException {
        Path dir = baselineDir(projectRoot, caseKey, viewport);
        if (!Files.isRegularFile(dir.resolve(BASELINE_PNG))) {
            return Optional.empty();
        }
        return Optional.of(readMeta(dir).orElseGet(() -> {
            Map<String, Object> stub = new LinkedHashMap<>();
            stub.put("caseKey", caseKey.trim());
            stub.put("viewport", viewport.trim());
            return stub;
        }));
    }

    public Optional<Path> baselineImage(Path projectRoot, String caseKey, String viewport) {
        Path file = baselineDir(projectRoot, caseKey, viewport).resolve(BASELINE_PNG);
        return Files.isRegularFile(file) ? Optional.of(file) : Optional.empty();
    }

    public List<Map<String, Object>> listBaselines(Path projectRoot) throws IOException {
        Path root = baselinesRoot(projectRoot);
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        try (var cases = Files.list(root)) {
            cases.filter(Files::isDirectory)
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .forEach(caseDir -> {
                        try (var views = Files.list(caseDir)) {
                            views.filter(Files::isDirectory)
                                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                                    .forEach(viewDir -> {
                                        Path png = viewDir.resolve(BASELINE_PNG);
                                        if (!Files.isRegularFile(png)) {
                                            return;
                                        }
                                        try {
                                            Map<String, Object> meta = readMeta(viewDir).orElseGet(LinkedHashMap::new);
                                            meta.putIfAbsent("caseKey", caseDir.getFileName().toString());
                                            meta.putIfAbsent("viewport", viewDir.getFileName().toString());
                                            meta.put("hasBaseline", true);
                                            out.add(meta);
                                        } catch (IOException ignored) {
                                        }
                                    });
                        } catch (IOException ignored) {
                        }
                    });
        }
        return List.copyOf(out);
    }

    public Map<String, Object> compare(
            Path projectRoot,
            String runId,
            String caseKey,
            String viewport,
            byte[] actualPngBytes,
            boolean writeDiffPng
    ) throws IOException {
        validateKey(runId, "runId");
        validateKey(caseKey, "caseKey");
        validateKey(viewport, "viewport");
        Path baseline = baselineImage(projectRoot, caseKey, viewport)
                .orElseThrow(() -> new NotFoundException("No baseline for caseKey/viewport"));
        if (actualPngBytes == null || actualPngBytes.length == 0) {
            throw new BadUploadException("Actual image is empty");
        }
        byte[] actual = requirePng(actualPngBytes);
        byte[] baselineBytes = Files.readAllBytes(baseline);
        Map<String, Object> meta = loadBaselineMeta(projectRoot, caseKey, viewport).orElse(Map.of());
        int block = intOr(meta.get("block"), DEFAULT_BLOCK);
        double luma = doubleOr(meta.get("lumaThreshold"), DEFAULT_LUMA_THRESHOLD);
        double maxFrac = doubleOr(meta.get("maxChangedFraction"), DEFAULT_MAX_CHANGED_FRACTION);

        ScreenshotDiff.Result result = ScreenshotDiff.compare(baselineBytes, actual, List.of(), block, luma);
        boolean passed = result.passes(maxFrac);

        Path dir = diffDir(projectRoot, runId, caseKey, viewport);
        Files.createDirectories(dir);
        Files.write(dir.resolve(ACTUAL_PNG), actual);

        String diffFile = null;
        if (writeDiffPng && !passed) {
            byte[] diff = ScreenshotDiff.renderDiffPng(baselineBytes, actual, List.of(), block, luma);
            Files.write(dir.resolve(DIFF_PNG), diff);
            diffFile = DIFF_PNG;
        } else if (Files.isRegularFile(dir.resolve(DIFF_PNG))) {
            Files.deleteIfExists(dir.resolve(DIFF_PNG));
        }

        Map<String, Object> json = new LinkedHashMap<>();
        json.put("runId", runId.trim());
        json.put("caseKey", caseKey.trim());
        json.put("viewport", viewport.trim());
        json.put("passed", passed);
        json.put("changedBlocks", result.changedBlocks());
        json.put("totalBlocks", result.totalBlocks());
        json.put("changedFraction", result.changedFraction());
        json.put("maxChangedFraction", maxFrac);
        json.put("block", block);
        json.put("lumaThreshold", luma);
        json.put("baselineRel", BASELINES_DIR + "/" + caseKey.trim() + "/" + viewport.trim() + "/" + BASELINE_PNG);
        json.put("actualFile", ACTUAL_PNG);
        if (diffFile != null) {
            json.put("diffFile", diffFile);
        }
        json.put("comparedAt", Instant.now().toString());
        mapper.writerWithDefaultPrettyPrinter().writeValue(dir.resolve(RESULT_JSON).toFile(), json);
        return json;
    }

    public Map<String, Object> approveFromRun(
            Path projectRoot,
            String runId,
            String caseKey,
            String viewport
    ) throws IOException {
        Path actual = diffDir(projectRoot, runId, caseKey, viewport).resolve(ACTUAL_PNG);
        if (!Files.isRegularFile(actual)) {
            throw new NotFoundException("No actual.png for run/case/viewport — compare first");
        }
        Map<String, Object> prior = loadBaselineMeta(projectRoot, caseKey, viewport).orElse(Map.of());
        Integer block = prior.containsKey("block") ? intOr(prior.get("block"), DEFAULT_BLOCK) : null;
        Double luma = prior.containsKey("lumaThreshold") ? doubleOr(prior.get("lumaThreshold"), DEFAULT_LUMA_THRESHOLD) : null;
        Double maxFrac = prior.containsKey("maxChangedFraction")
                ? doubleOr(prior.get("maxChangedFraction"), DEFAULT_MAX_CHANGED_FRACTION) : null;
        return saveBaseline(projectRoot, caseKey, viewport, Files.readAllBytes(actual), runId, block, luma, maxFrac);
    }

    public Optional<Map<String, Object>> loadDiffResult(Path projectRoot, String runId, String caseKey, String viewport)
            throws IOException {
        Path result = diffDir(projectRoot, runId, caseKey, viewport).resolve(RESULT_JSON);
        if (!Files.isRegularFile(result)) {
            return Optional.empty();
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> map = mapper.readValue(result.toFile(), Map.class);
        return Optional.of(map);
    }

    public Optional<Path> diffImage(Path projectRoot, String runId, String caseKey, String viewport, String which) {
        String name = switch (which == null ? "" : which.toLowerCase(Locale.ROOT)) {
            case "actual" -> ACTUAL_PNG;
            case "diff" -> DIFF_PNG;
            case "baseline" -> null;
            default -> throw new BadPathException("Image kind must be actual or diff");
        };
        if (name == null) {
            return baselineImage(projectRoot, caseKey, viewport);
        }
        Path file = diffDir(projectRoot, runId, caseKey, viewport).resolve(name);
        return Files.isRegularFile(file) ? Optional.of(file) : Optional.empty();
    }

    public List<Map<String, Object>> listDiffs(Path projectRoot, String runIdFilter) throws IOException {
        Path root = diffsRoot(projectRoot);
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        try (var runs = Files.list(root)) {
            runs.filter(Files::isDirectory)
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .forEach(runDir -> {
                        String runId = runDir.getFileName().toString();
                        if (runIdFilter != null && !runIdFilter.isBlank() && !runIdFilter.equals(runId)) {
                            return;
                        }
                        try (var cases = Files.list(runDir)) {
                            cases.filter(Files::isDirectory).forEach(caseDir -> {
                                try (var views = Files.list(caseDir)) {
                                    views.filter(Files::isDirectory).forEach(viewDir -> {
                                        Path result = viewDir.resolve(RESULT_JSON);
                                        if (!Files.isRegularFile(result)) {
                                            return;
                                        }
                                        try {
                                            @SuppressWarnings("unchecked")
                                            Map<String, Object> map = mapper.readValue(result.toFile(), Map.class);
                                            out.add(map);
                                        } catch (IOException ignored) {
                                        }
                                    });
                                } catch (IOException ignored) {
                                }
                            });
                        } catch (IOException ignored) {
                        }
                    });
        }
        out.sort(Comparator.comparing(m -> String.valueOf(m.getOrDefault("comparedAt", ""))));
        return List.copyOf(out);
    }

    public List<Map<String, Object>> listPending(Path projectRoot, String runIdFilter) throws IOException {
        List<Map<String, Object>> all = listDiffs(projectRoot, runIdFilter);
        List<Map<String, Object>> pending = new ArrayList<>();
        for (Map<String, Object> row : all) {
            Object passed = row.get("passed");
            if (passed instanceof Boolean b && !b) {
                pending.add(row);
            } else if (passed != null && !Boolean.parseBoolean(String.valueOf(passed))) {
                pending.add(row);
            }
        }
        return List.copyOf(pending);
    }

    static void validateKey(String value, String field) {
        if (value == null || value.isBlank() || !KEY.matcher(value.trim()).matches()) {
            throw new BadPathException("Invalid " + field);
        }
    }

    static byte[] requirePng(byte[] bytes) {
        if (bytes.length >= 8
                && bytes[0] == (byte) 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47) {
            return bytes;
        }
        throw new BadUploadException("Only PNG images are allowed");
    }

    private Optional<Map<String, Object>> readMeta(Path dir) throws IOException {
        Path meta = dir.resolve(META_JSON);
        if (!Files.isRegularFile(meta)) {
            return Optional.empty();
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> map = mapper.readValue(meta.toFile(), Map.class);
        return Optional.of(map);
    }

    private static String stringOr(Object v, String fallback) {
        if (v == null) {
            return fallback;
        }
        String s = String.valueOf(v);
        return s.isBlank() ? fallback : s;
    }

    private static int intOr(Object v, int fallback) {
        if (v instanceof Number n) {
            return n.intValue();
        }
        if (v != null) {
            try {
                return Integer.parseInt(String.valueOf(v));
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    private static double doubleOr(Object v, double fallback) {
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        if (v != null) {
            try {
                return Double.parseDouble(String.valueOf(v));
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    public static class BadPathException extends RuntimeException {
        public BadPathException(String message) {
            super(message);
        }
    }

    public static class BadUploadException extends RuntimeException {
        public BadUploadException(String message) {
            super(message);
        }
    }

    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String message) {
            super(message);
        }
    }
}
