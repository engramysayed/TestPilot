package delivery.portal.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Portal facade for visual baselines / diffs under {@link PortalStore#projectDiskRoot(String)}.
 * Execute/Hunt can call {@link #compareActual} once a screenshot is available; FE uses the REST controllers.
 */
@Service
public class VisualDiffService {
    private final PortalStore store;
    private final VisualDiffDiskStore disk;

    public VisualDiffService(PortalStore store, ObjectMapper objectMapper) {
        this.store = store;
        this.disk = new VisualDiffDiskStore(objectMapper);
    }

    public Path projectRoot(String projectId) {
        return store.projectDiskRoot(projectId);
    }

    public List<Map<String, Object>> listBaselines(String projectId) throws IOException {
        return disk.listBaselines(projectRoot(projectId));
    }

    public Optional<Map<String, Object>> getBaseline(String projectId, String caseKey, String viewport) throws IOException {
        return disk.loadBaselineMeta(projectRoot(projectId), caseKey, viewport);
    }

    public Optional<Path> baselineImage(String projectId, String caseKey, String viewport) {
        return disk.baselineImage(projectRoot(projectId), caseKey, viewport);
    }

    public Map<String, Object> replaceBaseline(
            String projectId,
            String caseKey,
            String viewport,
            byte[] pngBytes,
            String sourceRunId,
            Integer block,
            Double lumaThreshold,
            Double maxChangedFraction
    ) throws IOException {
        return disk.saveBaseline(
                projectRoot(projectId), caseKey, viewport, pngBytes, sourceRunId, block, lumaThreshold, maxChangedFraction);
    }

    public Map<String, Object> approveFromRun(String projectId, String runId, String caseKey, String viewport)
            throws IOException {
        return disk.approveFromRun(projectRoot(projectId), runId, caseKey, viewport);
    }

    /**
     * Compare {@code actualPng} against the stored baseline and write
     * {@code visual-diffs/{runId}/{caseKey}/{viewport}/}. Safe to call from Execute/Hunt after a screenshot lands.
     */
    public Map<String, Object> compareActual(
            String projectId,
            String runId,
            String caseKey,
            String viewport,
            byte[] actualPng,
            boolean writeDiffPng
    ) throws IOException {
        return disk.compare(projectRoot(projectId), runId, caseKey, viewport, actualPng, writeDiffPng);
    }

    public List<Map<String, Object>> listDiffs(String projectId, String runId, boolean pendingOnly) throws IOException {
        if (pendingOnly) {
            return disk.listPending(projectRoot(projectId), runId);
        }
        return disk.listDiffs(projectRoot(projectId), runId);
    }

    public Optional<Map<String, Object>> getDiff(String projectId, String runId, String caseKey, String viewport)
            throws IOException {
        return disk.loadDiffResult(projectRoot(projectId), runId, caseKey, viewport);
    }

    public Optional<Path> diffImage(String projectId, String runId, String caseKey, String viewport, String which) {
        return disk.diffImage(projectRoot(projectId), runId, caseKey, viewport, which);
    }

    public VisualDiffDiskStore disk() {
        return disk;
    }
}
