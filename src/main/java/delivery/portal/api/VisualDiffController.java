package delivery.portal.api;

import delivery.portal.security.CurrentUserService;
import delivery.portal.service.PortalStore;
import delivery.portal.service.VisualDiffDiskStore;
import delivery.portal.service.VisualDiffService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Visual diff list / compare / image serve for FE approve UX.
 * Reads: owned project. Compare write: ProjectAccess.denyUnlessOperable.
 */
@RestController
@RequestMapping("/api/projects/{projectId}/visual-diffs")
public class VisualDiffController {

    private final PortalStore store;
    private final CurrentUserService currentUser;
    private final VisualDiffService visuals;

    public VisualDiffController(PortalStore store, CurrentUserService currentUser, VisualDiffService visuals) {
        this.store = store;
        this.currentUser = currentUser;
        this.visuals = visuals;
    }

    @GetMapping
    public ResponseEntity<?> list(
            @PathVariable("projectId") String projectId,
            @RequestParam(value = "runId", required = false) String runId,
            @RequestParam(value = "pending", required = false, defaultValue = "false") boolean pending
    ) {
        if (store.getOwnedProject(projectId, currentUser.requireUserId()).isEmpty()) {
            return notFound();
        }
        try {
            return ResponseEntity.ok(visuals.listDiffs(projectId, runId, pending));
        } catch (Exception e) {
            return internalError(e);
        }
    }

    @GetMapping("/{runId}/{caseKey}/{viewport}")
    public ResponseEntity<?> getOne(
            @PathVariable("projectId") String projectId,
            @PathVariable("runId") String runId,
            @PathVariable("caseKey") String caseKey,
            @PathVariable("viewport") String viewport
    ) {
        if (store.getOwnedProject(projectId, currentUser.requireUserId()).isEmpty()) {
            return notFound();
        }
        try {
            return visuals.getDiff(projectId, runId, caseKey, viewport)
                    .<ResponseEntity<?>>map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body(new ApiError("NOT_FOUND", "Visual diff not found").asMap()));
        } catch (VisualDiffDiskStore.BadPathException e) {
            return ResponseEntity.badRequest().body(new ApiError("BAD_REQUEST", e.getMessage()).asMap());
        } catch (Exception e) {
            return internalError(e);
        }
    }

    @GetMapping("/{runId}/{caseKey}/{viewport}/actual")
    public ResponseEntity<?> actualImage(
            @PathVariable("projectId") String projectId,
            @PathVariable("runId") String runId,
            @PathVariable("caseKey") String caseKey,
            @PathVariable("viewport") String viewport
    ) {
        return serveImage(projectId, runId, caseKey, viewport, "actual");
    }

    @GetMapping("/{runId}/{caseKey}/{viewport}/diff")
    public ResponseEntity<?> diffImage(
            @PathVariable("projectId") String projectId,
            @PathVariable("runId") String runId,
            @PathVariable("caseKey") String caseKey,
            @PathVariable("viewport") String viewport
    ) {
        return serveImage(projectId, runId, caseKey, viewport, "diff");
    }

    private ResponseEntity<?> serveImage(
            String projectId, String runId, String caseKey, String viewport, String which) {
        if (store.getOwnedProject(projectId, currentUser.requireUserId()).isEmpty()) {
            return notFound();
        }
        try {
            Path file = visuals.diffImage(projectId, runId, caseKey, viewport, which)
                    .orElseThrow(() -> new VisualDiffDiskStore.NotFoundException(which + " image not found"));
            Resource resource = new FileSystemResource(file);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + which + ".png\"")
                    .contentType(MediaType.IMAGE_PNG)
                    .contentLength(Files.size(file))
                    .body(resource);
        } catch (VisualDiffDiskStore.BadPathException e) {
            return ResponseEntity.badRequest().body(new ApiError("BAD_REQUEST", e.getMessage()).asMap());
        } catch (VisualDiffDiskStore.NotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiError("NOT_FOUND", e.getMessage()).asMap());
        } catch (Exception e) {
            return internalError(e);
        }
    }

    @PostMapping(path = "/compare", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> compare(
            @PathVariable("projectId") String projectId,
            @RequestParam("runId") String runId,
            @RequestParam("caseKey") String caseKey,
            @RequestParam("viewport") String viewport,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "writeDiffPng", required = false, defaultValue = "true") boolean writeDiffPng
    ) {
        ResponseEntity<?> denied = ProjectAccess.denyUnlessOperable(store, projectId, currentUser.requireUserId());
        if (denied != null) {
            return denied;
        }
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(new ApiError("BAD_REQUEST", "file (actual PNG) is required").asMap());
        }
        try {
            Map<String, Object> result = visuals.compareActual(
                    projectId, runId, caseKey, viewport, file.getBytes(), writeDiffPng,
                    currentUser.requireUserId());
            Map<String, Object> body = new LinkedHashMap<>(result);
            body.put("projectId", projectId);
            return ResponseEntity.ok(body);
        } catch (VisualDiffDiskStore.BadPathException | VisualDiffDiskStore.BadUploadException e) {
            return ResponseEntity.badRequest().body(new ApiError("BAD_REQUEST", e.getMessage()).asMap());
        } catch (VisualDiffDiskStore.NotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiError("NOT_FOUND", e.getMessage()).asMap());
        } catch (delivery.portal.service.credits.InsufficientCreditsException e) {
            throw e;
        } catch (Exception e) {
            return internalError(e);
        }
    }

    private static ResponseEntity<?> notFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError("NOT_FOUND", "Unknown project").asMap());
    }

    private static ResponseEntity<?> internalError(Exception e) {
        String msg = e.getMessage() == null ? "Internal error" : e.getMessage();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError("INTERNAL_ERROR", msg).asMap());
    }
}
