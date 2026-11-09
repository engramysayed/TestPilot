package delivery.portal.api;

import delivery.portal.model.JobRecord;
import delivery.portal.security.CurrentUserService;
import delivery.portal.service.PortalStore;
import delivery.store.ProjectStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

/**
 * One delete endpoint for every run on the /runs page: Execute, Automate (CONVERT),
 * Generate, Hunt. Execute has its own richer endpoint under /api/execute-runs/{jobId}
 * (keeps live-worker guards); this one covers the other kinds and the generic case.
 */
@RestController
public class JobDeletionController {
    private static final Logger log = LoggerFactory.getLogger(JobDeletionController.class);

    private final PortalStore store;
    private final CurrentUserService currentUser;

    public JobDeletionController(PortalStore store, CurrentUserService currentUser) {
        this.store = store;
        this.currentUser = currentUser;
    }

    @DeleteMapping("/api/runs/{jobId}")
    public ResponseEntity<?> delete(@PathVariable("jobId") String jobId) {
        Long ownerId = currentUser.requireUserId();
        Optional<JobRecord> owned = store.getOwnedJob(jobId, ownerId);
        if (owned.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiError("NOT_FOUND", "Unknown run").asMap());
        }
        JobRecord job = owned.get();
        // Ownership alone isn't enough: a user who created a job and then lost `operate` on
        // the project should not be able to purge runs afterwards.
        ResponseEntity<?> denied = ProjectAccess.denyUnlessOperable(store, job.getProjectId(), ownerId);
        if (denied != null) {
            return denied;
        }
        if (job.getStatus() == JobRecord.Status.QUEUED || job.getStatus() == JobRecord.Status.RUNNING) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ApiError("JOB_ACTIVE", "Cancel the run before deleting it").asMap());
        }
        // Best-effort: wipe the per-kind artifacts folder before removing the DB row.
        // If a folder removal fails, keep going — the row is what the list reads from.
        wipeArtifacts(job);
        if (!store.deleteOwnedJobRecord(jobId, ownerId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiError("NOT_FOUND", "Unknown run").asMap());
        }
        return ResponseEntity.ok(Map.of("deleted", true, "jobId", jobId, "kind", job.getJobKind().name()));
    }

    private void wipeArtifacts(JobRecord job) {
        String projectId = job.getProjectId();
        if (projectId == null || projectId.isBlank()) {
            return;
        }
        Path projectRoot;
        try {
            projectRoot = store.projectDiskRoot(projectId);
        } catch (Exception e) {
            return; // project already gone; the DB row cleanup is enough
        }
        for (String folder : foldersFor(job.getJobKind())) {
            Path dir = projectRoot.resolve(folder).resolve(job.getJobId());
            if (Files.isDirectory(dir)) {
                try {
                    ProjectStore.deleteRecursive(dir);
                } catch (Exception e) {
                    log.warn("delete {}: could not remove {}: {}", job.getJobId(), dir, e.getMessage());
                }
            }
        }
    }

    private static java.util.List<String> foldersFor(JobRecord.JobKind kind) {
        if (kind == null) {
            return java.util.List.of();
        }
        return switch (kind) {
            case EXECUTE -> java.util.List.of("execute-runs");
            case HUNT -> java.util.List.of("hunt-runs");
            case GENERATE_BATCH, GENERATE_COMPARE -> java.util.List.of("generate-runs", "generate-batches");
            case CONVERT -> java.util.List.of("jobs"); // Automate stores evidence under jobs/<jobId>
        };
    }
}
