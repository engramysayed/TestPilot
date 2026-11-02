package delivery.portal.api;

import delivery.portal.security.CurrentUserService;
import delivery.portal.service.ExecuteRunService;
import delivery.portal.service.FailureEvidenceDiskStore;
import delivery.portal.service.FailureEvidenceService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Failure replay APIs over execute-runs evidence {@code failure/{stepIndex}/} trees.
 * Auth: same ownership gate as existing execute-run evidence endpoints.
 */
@RestController
@RequestMapping("/api/execute-runs/{jobId}/failures")
public class FailureReplayController {
    private final ExecuteRunService executeRuns;
    private final FailureEvidenceService failures;
    private final CurrentUserService currentUser;

    public FailureReplayController(
            ExecuteRunService executeRuns,
            FailureEvidenceService failures,
            CurrentUserService currentUser
    ) {
        this.executeRuns = executeRuns;
        this.failures = failures;
        this.currentUser = currentUser;
    }

    @GetMapping
    public ResponseEntity<?> list(@PathVariable("jobId") String jobId) {
        var owned = executeRuns.requireOwnedExecuteJob(jobId, currentUser.requireUserId());
        if (owned.isEmpty()) {
            return notFoundJob();
        }
        try {
            List<Map<String, Object>> items = new ArrayList<>();
            for (Map<String, Object> row : failures.list(owned.get().getProjectId(), jobId)) {
                items.add(FailureEvidenceDiskStore.toApiJson(row));
            }
            return ResponseEntity.ok(items);
        } catch (FailureEvidenceDiskStore.BadPathException e) {
            return badRequest(e.getMessage());
        } catch (Exception e) {
            return internalError(e);
        }
    }

    @GetMapping("/{tcId}")
    public ResponseEntity<?> listForTc(
            @PathVariable("jobId") String jobId,
            @PathVariable("tcId") String tcId
    ) {
        var owned = executeRuns.requireOwnedExecuteJob(jobId, currentUser.requireUserId());
        if (owned.isEmpty()) {
            return notFoundJob();
        }
        try {
            List<Map<String, Object>> items = new ArrayList<>();
            for (Map<String, Object> row : failures.listForTc(owned.get().getProjectId(), jobId, tcId)) {
                items.add(FailureEvidenceDiskStore.toApiJson(row));
            }
            return ResponseEntity.ok(items);
        } catch (FailureEvidenceDiskStore.BadPathException | FailureEvidenceDiskStore.BadRequestException e) {
            return badRequest(e.getMessage());
        } catch (Exception e) {
            return internalError(e);
        }
    }

    @GetMapping("/{tcId}/{stepIndex}")
    public ResponseEntity<?> getOne(
            @PathVariable("jobId") String jobId,
            @PathVariable("tcId") String tcId,
            @PathVariable("stepIndex") int stepIndex
    ) {
        var owned = executeRuns.requireOwnedExecuteJob(jobId, currentUser.requireUserId());
        if (owned.isEmpty()) {
            return notFoundJob();
        }
        try {
            return failures.get(owned.get().getProjectId(), jobId, tcId, stepIndex)
                    .<ResponseEntity<?>>map(row -> ResponseEntity.ok(FailureEvidenceDiskStore.toApiJson(row)))
                    .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body(new ApiError("NOT_FOUND", "Failure evidence not found").asMap()));
        } catch (FailureEvidenceDiskStore.BadPathException | FailureEvidenceDiskStore.BadRequestException e) {
            return badRequest(e.getMessage());
        } catch (Exception e) {
            return internalError(e);
        }
    }

    @GetMapping("/{tcId}/{stepIndex}/screen")
    public ResponseEntity<?> screen(
            @PathVariable("jobId") String jobId,
            @PathVariable("tcId") String tcId,
            @PathVariable("stepIndex") int stepIndex
    ) {
        return blob(jobId, tcId, stepIndex, "screen", MediaType.IMAGE_PNG, "screen.png");
    }

    @GetMapping("/{tcId}/{stepIndex}/network")
    public ResponseEntity<?> network(
            @PathVariable("jobId") String jobId,
            @PathVariable("tcId") String tcId,
            @PathVariable("stepIndex") int stepIndex
    ) {
        return blob(jobId, tcId, stepIndex, "network", MediaType.APPLICATION_JSON, "network.json");
    }

    @GetMapping("/{tcId}/{stepIndex}/console")
    public ResponseEntity<?> console(
            @PathVariable("jobId") String jobId,
            @PathVariable("tcId") String tcId,
            @PathVariable("stepIndex") int stepIndex
    ) {
        return blob(jobId, tcId, stepIndex, "console", MediaType.APPLICATION_JSON, "console.json");
    }

    private ResponseEntity<?> blob(
            String jobId, String tcId, int stepIndex, String kind, MediaType type, String fileName
    ) {
        var owned = executeRuns.requireOwnedExecuteJob(jobId, currentUser.requireUserId());
        if (owned.isEmpty()) {
            return notFoundJob();
        }
        try {
            String projectId = owned.get().getProjectId();
            Path file = switch (kind) {
                case "screen" -> failures.screen(projectId, jobId, tcId, stepIndex).orElse(null);
                case "network" -> failures.network(projectId, jobId, tcId, stepIndex).orElse(null);
                case "console" -> failures.console(projectId, jobId, tcId, stepIndex).orElse(null);
                default -> null;
            };
            if (file == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ApiError("NOT_FOUND", kind + " evidence not found").asMap());
            }
            Resource resource = new FileSystemResource(file);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fileName + "\"")
                    .contentType(type)
                    .body(resource);
        } catch (FailureEvidenceDiskStore.BadPathException | FailureEvidenceDiskStore.BadRequestException e) {
            return badRequest(e.getMessage());
        } catch (Exception e) {
            return internalError(e);
        }
    }

    private static ResponseEntity<?> notFoundJob() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError("NOT_FOUND", "Unknown execute run").asMap());
    }

    private static ResponseEntity<?> badRequest(String message) {
        return ResponseEntity.badRequest()
                .body(new ApiError("BAD_REQUEST", message).asMap());
    }

    private static ResponseEntity<?> internalError(Exception e) {
        String msg = e.getMessage() == null ? "Internal error" : e.getMessage();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError("INTERNAL_ERROR", msg).asMap());
    }
}
