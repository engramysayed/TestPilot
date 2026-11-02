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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Visual baseline CRUD for FE approve UX.
 * Auth mirrors DesignReferenceController: owned project for reads; ProjectAccess.denyUnlessOperable for writes.
 */
@RestController
@RequestMapping("/api/projects/{projectId}/visual-baselines")
public class VisualBaselineController {

    private final PortalStore store;
    private final CurrentUserService currentUser;
    private final VisualDiffService visuals;

    public VisualBaselineController(PortalStore store, CurrentUserService currentUser, VisualDiffService visuals) {
        this.store = store;
        this.currentUser = currentUser;
        this.visuals = visuals;
    }

    @GetMapping
    public ResponseEntity<?> list(@PathVariable("projectId") String projectId) {
        if (store.getOwnedProject(projectId, currentUser.requireUserId()).isEmpty()) {
            return notFound();
        }
        try {
            return ResponseEntity.ok(visuals.listBaselines(projectId));
        } catch (Exception e) {
            return internalError(e);
        }
    }

    @GetMapping("/{caseKey}/{viewport}")
    public ResponseEntity<?> getMeta(
            @PathVariable("projectId") String projectId,
            @PathVariable("caseKey") String caseKey,
            @PathVariable("viewport") String viewport
    ) {
        if (store.getOwnedProject(projectId, currentUser.requireUserId()).isEmpty()) {
            return notFound();
        }
        try {
            return visuals.getBaseline(projectId, caseKey, viewport)
                    .<ResponseEntity<?>>map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body(new ApiError("NOT_FOUND", "Baseline not found").asMap()));
        } catch (VisualDiffDiskStore.BadPathException e) {
            return ResponseEntity.badRequest().body(new ApiError("BAD_REQUEST", e.getMessage()).asMap());
        } catch (Exception e) {
            return internalError(e);
        }
    }

    @GetMapping("/{caseKey}/{viewport}/image")
    public ResponseEntity<?> image(
            @PathVariable("projectId") String projectId,
            @PathVariable("caseKey") String caseKey,
            @PathVariable("viewport") String viewport
    ) {
        if (store.getOwnedProject(projectId, currentUser.requireUserId()).isEmpty()) {
            return notFound();
        }
        try {
            Path file = visuals.baselineImage(projectId, caseKey, viewport)
                    .orElseThrow(() -> new VisualDiffDiskStore.NotFoundException("Baseline not found"));
            Resource resource = new FileSystemResource(file);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"baseline.png\"")
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

    @PostMapping(path = "/{caseKey}/{viewport}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadReplace(
            @PathVariable("projectId") String projectId,
            @PathVariable("caseKey") String caseKey,
            @PathVariable("viewport") String viewport,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "sourceRunId", required = false) String sourceRunId,
            @RequestParam(value = "block", required = false) Integer block,
            @RequestParam(value = "lumaThreshold", required = false) Double lumaThreshold,
            @RequestParam(value = "maxChangedFraction", required = false) Double maxChangedFraction
    ) {
        return replace(projectId, caseKey, viewport, file, sourceRunId, block, lumaThreshold, maxChangedFraction);
    }

    @PutMapping(path = "/{caseKey}/{viewport}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> putReplace(
            @PathVariable("projectId") String projectId,
            @PathVariable("caseKey") String caseKey,
            @PathVariable("viewport") String viewport,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "sourceRunId", required = false) String sourceRunId,
            @RequestParam(value = "block", required = false) Integer block,
            @RequestParam(value = "lumaThreshold", required = false) Double lumaThreshold,
            @RequestParam(value = "maxChangedFraction", required = false) Double maxChangedFraction
    ) {
        return replace(projectId, caseKey, viewport, file, sourceRunId, block, lumaThreshold, maxChangedFraction);
    }

    private ResponseEntity<?> replace(
            String projectId,
            String caseKey,
            String viewport,
            MultipartFile file,
            String sourceRunId,
            Integer block,
            Double lumaThreshold,
            Double maxChangedFraction
    ) {
        ResponseEntity<?> denied = ProjectAccess.denyUnlessOperable(store, projectId, currentUser.requireUserId());
        if (denied != null) {
            return denied;
        }
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(new ApiError("BAD_REQUEST", "File is required").asMap());
        }
        try {
            Map<String, Object> meta = visuals.replaceBaseline(
                    projectId, caseKey, viewport, file.getBytes(), sourceRunId, block, lumaThreshold, maxChangedFraction);
            Map<String, Object> body = new LinkedHashMap<>(meta);
            body.put("projectId", projectId);
            return ResponseEntity.status(HttpStatus.CREATED).body(body);
        } catch (VisualDiffDiskStore.BadPathException | VisualDiffDiskStore.BadUploadException e) {
            return ResponseEntity.badRequest().body(new ApiError("BAD_REQUEST", e.getMessage()).asMap());
        } catch (Exception e) {
            return internalError(e);
        }
    }

    @PostMapping(path = "/{caseKey}/{viewport}/approve", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> approve(
            @PathVariable("projectId") String projectId,
            @PathVariable("caseKey") String caseKey,
            @PathVariable("viewport") String viewport,
            @RequestBody Map<String, Object> body
    ) {
        ResponseEntity<?> denied = ProjectAccess.denyUnlessOperable(store, projectId, currentUser.requireUserId());
        if (denied != null) {
            return denied;
        }
        Object runIdObj = body == null ? null : body.get("runId");
        if (runIdObj == null || String.valueOf(runIdObj).isBlank()) {
            return ResponseEntity.badRequest().body(new ApiError("BAD_REQUEST", "runId is required").asMap());
        }
        try {
            Map<String, Object> meta = visuals.approveFromRun(projectId, String.valueOf(runIdObj), caseKey, viewport);
            Map<String, Object> out = new LinkedHashMap<>(meta);
            out.put("projectId", projectId);
            out.put("approvedFromRunId", String.valueOf(runIdObj).trim());
            return ResponseEntity.ok(out);
        } catch (VisualDiffDiskStore.BadPathException | VisualDiffDiskStore.BadUploadException e) {
            return ResponseEntity.badRequest().body(new ApiError("BAD_REQUEST", e.getMessage()).asMap());
        } catch (VisualDiffDiskStore.NotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiError("NOT_FOUND", e.getMessage()).asMap());
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
