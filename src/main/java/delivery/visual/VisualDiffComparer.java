package delivery.visual;

import delivery.portal.service.VisualDiffDiskStore;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * AE Execute hook: compare against project-disk baselines using the same trees as
 * {@link VisualDiffDiskStore} / portal REST. Never rewrites baseline (approve is Backend/FE).
 */
public final class VisualDiffComparer {
    private VisualDiffComparer() {
    }

    /**
     * Compare {@code actualPng} to baseline; write {@code visual-diffs/...}.
     * When no baseline exists: write actual + result with {@link VisualDiffStatus#NO_BASELINE}
     * (does not throw; Execute stays green).
     */
    public static VisualDiffCompareResult compareAndWrite(
            Path projectRoot,
            String runId,
            String caseKey,
            String viewport,
            byte[] actualPng
    ) {
        String key = VisualBaselinePaths.caseKey(caseKey);
        // If caller already passed a sanitized key (TC_LOGIN), caseKey() is identity.
        if (VisualBaselinePaths.KEY.matcher(caseKey == null ? "" : caseKey.trim()).matches()) {
            key = caseKey.trim();
        }
        String vp = VisualBaselinePaths.sanitizeViewport(viewport);
        String rid = VisualBaselinePaths.sanitizeRunId(runId);
        double threshold = VisualDiffConfig.failFraction();
        Path baselinePath = projectRoot == null
                ? null
                : VisualBaselinePaths.baselinePng(projectRoot, key, vp);

        if (projectRoot == null) {
            return new VisualDiffCompareResult(
                    VisualDiffStatus.ERROR, key, vp, rid, 0, 0, 0, threshold,
                    "projectRoot missing", null, baselinePath, null);
        }
        if (actualPng == null || actualPng.length == 0) {
            return errorResult(projectRoot, rid, key, vp, threshold, "actual png empty", null, baselinePath);
        }

        VisualDiffDiskStore disk = new VisualDiffDiskStore();
        Optional<Path> baseline = disk.baselineImage(projectRoot, key, vp);
        if (baseline.isEmpty()) {
            return writeNoBaseline(projectRoot, rid, key, vp, threshold, actualPng, baselinePath);
        }

        try {
            Map<String, Object> json = disk.compare(projectRoot, rid, key, vp, actualPng, true);
            boolean passed = Boolean.TRUE.equals(json.get("passed"))
                    || Boolean.parseBoolean(String.valueOf(json.get("passed")));
            double fraction = asDouble(json.get("changedFraction"));
            int changed = asInt(json.get("changedBlocks"));
            int total = asInt(json.get("totalBlocks"));
            double maxFrac = asDouble(json.get("maxChangedFraction"));
            if (maxFrac <= 0) {
                maxFrac = threshold;
            }
            Path actualPath = VisualBaselinePaths.actualPng(projectRoot, rid, key, vp);
            Path resultPath = VisualBaselinePaths.resultJson(projectRoot, rid, key, vp);
            VisualDiffStatus status = passed ? VisualDiffStatus.PASS : VisualDiffStatus.FAIL;
            String message = passed
                    ? "within threshold"
                    : "changedFraction=" + fraction + " > " + maxFrac;
            return new VisualDiffCompareResult(
                    status, key, vp, rid, fraction, changed, total, maxFrac,
                    message, actualPath, baseline.orElse(baselinePath), resultPath);
        } catch (Exception e) {
            return errorResult(projectRoot, rid, key, vp, threshold,
                    "compare failed: " + e.getMessage(), null, baseline.orElse(baselinePath));
        }
    }

    private static VisualDiffCompareResult writeNoBaseline(
            Path projectRoot,
            String runId,
            String caseKey,
            String viewport,
            double threshold,
            byte[] actualPng,
            Path baselinePath
    ) {
        Path actualPath = null;
        Path resultPath = VisualBaselinePaths.resultJson(projectRoot, runId, caseKey, viewport);
        try {
            Path dir = VisualBaselinePaths.diffDir(projectRoot, runId, caseKey, viewport);
            Files.createDirectories(dir);
            actualPath = dir.resolve(VisualBaselinePaths.ACTUAL_PNG);
            Files.write(actualPath, actualPng);
            JSONObject o = new JSONObject();
            o.put("runId", runId);
            o.put("caseKey", caseKey);
            o.put("viewport", viewport);
            o.put("passed", false);
            o.put("status", VisualDiffStatus.NO_BASELINE.name());
            o.put("noBaseline", true);
            o.put("changedBlocks", 0);
            o.put("totalBlocks", 0);
            o.put("changedFraction", 0);
            o.put("maxChangedFraction", threshold);
            o.put("actualFile", VisualBaselinePaths.ACTUAL_PNG);
            o.put("comparedAt", Instant.now().toString());
            o.put("message", "no baseline.png");
            Files.writeString(resultPath, o.toString(2), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return new VisualDiffCompareResult(
                    VisualDiffStatus.ERROR, caseKey, viewport, runId, 0, 0, 0, threshold,
                    "write no-baseline artifacts failed: " + e.getMessage(),
                    actualPath, baselinePath, resultPath);
        }
        return new VisualDiffCompareResult(
                VisualDiffStatus.NO_BASELINE, caseKey, viewport, runId, 0, 0, 0, threshold,
                "no baseline.png", actualPath, baselinePath, resultPath);
    }

    private static VisualDiffCompareResult errorResult(
            Path projectRoot,
            String runId,
            String caseKey,
            String viewport,
            double threshold,
            String message,
            Path actualPath,
            Path baselinePath
    ) {
        Path resultPath = VisualBaselinePaths.resultJson(projectRoot, runId, caseKey, viewport);
        VisualDiffCompareResult result = new VisualDiffCompareResult(
                VisualDiffStatus.ERROR, caseKey, viewport, runId, 0, 0, 0, threshold,
                message, actualPath, baselinePath, resultPath);
        try {
            Files.createDirectories(resultPath.getParent());
            Files.writeString(resultPath, result.toJson().toString(2), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            // keep in-memory result
        }
        return result;
    }

    private static double asDouble(Object v) {
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        if (v == null) {
            return 0;
        }
        try {
            return Double.parseDouble(String.valueOf(v));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static int asInt(Object v) {
        if (v instanceof Number n) {
            return n.intValue();
        }
        if (v == null) {
            return 0;
        }
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
