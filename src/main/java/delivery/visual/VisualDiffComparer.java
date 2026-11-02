package delivery.visual;

import delivery.effect.ScreenshotDiff;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * In-process compare against {@code visual-baselines/.../baseline.png} using
 * {@link ScreenshotDiff}. Writes run-scoped artifacts under {@code visual-diffs/}.
 * Never rewrites the baseline.
 */
public final class VisualDiffComparer {
    private VisualDiffComparer() {
    }

    public static VisualDiffCompareResult compareAndWrite(
            Path projectRoot,
            String runId,
            String caseKey,
            String viewport,
            byte[] actualPng
    ) {
        String vp = VisualBaselinePaths.sanitizeViewport(viewport);
        String rid = VisualBaselinePaths.sanitizeRunId(runId);
        double threshold = VisualDiffConfig.failFraction();
        Path baselinePath = projectRoot == null
                ? null
                : VisualBaselinePaths.baselinePng(projectRoot, caseKey, vp);
        Path actualPath = null;
        Path resultPath = null;

        if (projectRoot == null) {
            return new VisualDiffCompareResult(
                    VisualDiffStatus.ERROR, caseKey, vp, rid, 0, 0, 0, threshold,
                    "projectRoot missing", null, baselinePath, null);
        }
        if (actualPng == null || actualPng.length == 0) {
            return writeResult(projectRoot, rid, caseKey, vp, threshold,
                    VisualDiffStatus.ERROR, 0, 0, 0, "actual png empty",
                    null, baselinePath);
        }

        try {
            Path dir = VisualBaselinePaths.diffDir(projectRoot, rid, caseKey, vp);
            Files.createDirectories(dir);
            actualPath = dir.resolve(VisualBaselinePaths.ACTUAL_PNG);
            Files.write(actualPath, actualPng);
            resultPath = dir.resolve(VisualBaselinePaths.RESULT_JSON);
        } catch (Exception e) {
            return new VisualDiffCompareResult(
                    VisualDiffStatus.ERROR, caseKey, vp, rid, 0, 0, 0, threshold,
                    "write actual failed: " + e.getMessage(), actualPath, baselinePath, resultPath);
        }

        Optional<Path> baseline = VisualBaselineStore.findBaselinePng(projectRoot, caseKey, vp);
        if (baseline.isEmpty()) {
            return writeResult(projectRoot, rid, caseKey, vp, threshold,
                    VisualDiffStatus.NO_BASELINE, 0, 0, 0, "no baseline.png",
                    actualPath, baselinePath);
        }

        try {
            byte[] baselineBytes = Files.readAllBytes(baseline.get());
            ScreenshotDiff.Result diff = ScreenshotDiff.compare(
                    baselineBytes, actualPng, List.of(),
                    VisualDiffConfig.block(), VisualDiffConfig.lumaThreshold());
            VisualDiffStatus status = diff.changedFraction() > threshold
                    ? VisualDiffStatus.FAIL
                    : VisualDiffStatus.PASS;
            String message = status == VisualDiffStatus.PASS
                    ? "within threshold"
                    : "changedFraction=" + diff.changedFraction() + " > " + threshold;
            return writeResult(projectRoot, rid, caseKey, vp, threshold,
                    status, diff.changedFraction(), diff.changedBlocks(), diff.totalBlocks(),
                    message, actualPath, baseline.get());
        } catch (Exception e) {
            return writeResult(projectRoot, rid, caseKey, vp, threshold,
                    VisualDiffStatus.ERROR, 1.0, 1, 1, "compare failed: " + e.getMessage(),
                    actualPath, baseline.get());
        }
    }

    private static VisualDiffCompareResult writeResult(
            Path projectRoot,
            String runId,
            String caseKey,
            String viewport,
            double threshold,
            VisualDiffStatus status,
            double changedFraction,
            int changedBlocks,
            int totalBlocks,
            String message,
            Path actualPath,
            Path baselinePath
    ) {
        Path resultPath = VisualBaselinePaths.resultJson(projectRoot, runId, caseKey, viewport);
        VisualDiffCompareResult result = new VisualDiffCompareResult(
                status, caseKey, viewport, runId, changedFraction, changedBlocks, totalBlocks,
                threshold, message, actualPath, baselinePath, resultPath);
        try {
            Files.createDirectories(resultPath.getParent());
            Files.writeString(resultPath, result.toJson().toString(2), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            // keep in-memory result even if result.json write fails
        }
        return result;
    }
}
