package delivery.portal.api;

import delivery.portal.security.CurrentUserService;
import delivery.portal.service.DesignReferenceService;
import delivery.portal.service.PortalStore;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/projects/{projectId}/design-references")
public class DesignReferenceController {

    private final PortalStore store;
    private final CurrentUserService currentUser;
    private final DesignReferenceService references;

    public DesignReferenceController(
            PortalStore store,
            CurrentUserService currentUser,
            DesignReferenceService references
    ) {
        this.store = store;
        this.currentUser = currentUser;
        this.references = references;
    }

    @GetMapping
    public ResponseEntity<?> list(@PathVariable("projectId") String projectId) {
        if (store.getOwnedProject(projectId, currentUser.requireUserId()).isEmpty()) {
            return notFound();
        }
        try {
            Path root = store.projectDiskRoot(projectId);
            List<Map<String, Object>> items = references.list(root).stream()
                    .map(tcId -> {
                        Map<String, Object> row = new HashMap<>();
                        row.put("tcId", tcId);
                        return row;
                    })
                    .collect(Collectors.toList());
            return ResponseEntity.ok(items);
        } catch (Exception e) {
            return internalError(e);
        }
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> upload(
            @PathVariable("projectId") String projectId,
            @RequestParam("tcId") String tcId,
            @RequestParam("file") MultipartFile file
    ) {
        if (store.getOwnedProject(projectId, currentUser.requireUserId()).isEmpty()) {
            return notFound();
        }
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(new ApiError("BAD_REQUEST", "File is required").asMap());
        }
        try {
            Path root = store.projectDiskRoot(projectId);
            references.upload(root, tcId, file.getBytes(), file.getContentType());
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "projectId", projectId,
                    "tcId", tcId.trim(),
                    "storedAs", tcId.trim() + ".png"
            ));
        } catch (DesignReferenceService.BadPathException | DesignReferenceService.BadUploadException e) {
            return ResponseEntity.badRequest()
                    .body(new ApiError("BAD_REQUEST", e.getMessage()).asMap());
        } catch (Exception e) {
            return internalError(e);
        }
    }

    @GetMapping("/{tcId}")
    public ResponseEntity<?> serve(
            @PathVariable("projectId") String projectId,
            @PathVariable("tcId") String tcId
    ) {
        if (store.getOwnedProject(projectId, currentUser.requireUserId()).isEmpty()) {
            return notFound();
        }
        try {
            Path root = store.projectDiskRoot(projectId);
            Path file = references.resolve(root, tcId)
                    .orElseThrow(() -> new DesignReferenceService.BadPathException("Reference not found"));
            Resource resource = new FileSystemResource(file);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + tcId + ".png\"")
                    .contentType(MediaType.IMAGE_PNG)
                    .contentLength(Files.size(file))
                    .body(resource);
        } catch (DesignReferenceService.BadPathException e) {
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
