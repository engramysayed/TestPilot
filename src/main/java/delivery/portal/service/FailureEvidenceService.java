package delivery.portal.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Portal facade for failure replay evidence under execute-runs evidence folders.
 */
@Service
public class FailureEvidenceService {
    private final PortalStore store;
    private final FailureEvidenceDiskStore disk;

    public FailureEvidenceService(PortalStore store, ObjectMapper objectMapper) {
        this.store = store;
        this.disk = new FailureEvidenceDiskStore(objectMapper);
    }

    public Path projectRoot(String projectId) {
        return store.projectDiskRoot(projectId);
    }

    public List<Map<String, Object>> list(String projectId, String jobId) throws IOException {
        return disk.listFailures(projectRoot(projectId), jobId);
    }

    public List<Map<String, Object>> listForTc(String projectId, String jobId, String tcId) throws IOException {
        return disk.listFailuresForTc(projectRoot(projectId), jobId, tcId);
    }

    public Optional<Map<String, Object>> get(String projectId, String jobId, String tcId, int stepIndex)
            throws IOException {
        return disk.getFailure(projectRoot(projectId), jobId, tcId, stepIndex);
    }

    public Optional<Path> screen(String projectId, String jobId, String tcId, int stepIndex) throws IOException {
        return disk.resolveScreen(projectRoot(projectId), jobId, tcId, stepIndex);
    }

    public Optional<Path> network(String projectId, String jobId, String tcId, int stepIndex) throws IOException {
        return disk.resolveNetwork(projectRoot(projectId), jobId, tcId, stepIndex);
    }

    public Optional<Path> console(String projectId, String jobId, String tcId, int stepIndex) throws IOException {
        return disk.resolveConsole(projectRoot(projectId), jobId, tcId, stepIndex);
    }

    /** In-process / AE helper to persist a failure tree matching the locked contract. */
    public Map<String, Object> writeFailure(
            String projectId,
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
        return disk.writeFailure(
                projectRoot(projectId), jobId, tcId, tcFolder, stepIndex,
                reason, pageUrl, source, screenPng, networkJson, consoleJson);
    }
}
