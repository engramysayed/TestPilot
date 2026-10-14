package delivery.portal.api;

import delivery.excel.GenerateQualityGate;
import delivery.excel.InvalidExcelTemplateException;
import delivery.portal.security.CurrentUserService;
import delivery.portal.service.GeneratedWorkbookService;
import delivery.portal.service.PortalStore;
import delivery.portal.service.WorkbookUploadPreviewService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * B15: {@code POST /api/projects/{id}/generated-workbook/preview} (multipart {@code file} or {@code excel})
 * and {@code POST /api/projects/{id}/generated-workbook/commit {"previewToken":"…"}}. Same access rule as the
 * existing upload ({@code denyUnlessOperable}; cross-tenant → 404). The existing direct upload is unchanged.
 */
@RestController
@RequestMapping("/api/projects")
public class WorkbookUploadPreviewController {
    private final PortalStore store;
    private final CurrentUserService currentUser;
    private final GeneratedWorkbookService workbooks;
    private final WorkbookUploadPreviewService previews;

    public WorkbookUploadPreviewController(PortalStore store, CurrentUserService currentUser,
                                           GeneratedWorkbookService workbooks, WorkbookUploadPreviewService previews) {
        this.store = store;
        this.currentUser = currentUser;
        this.workbooks = workbooks;
        this.previews = previews;
    }

    public record CommitRequest(String previewToken) {
    }

    @PostMapping(value = "/{projectId}/generated-workbook/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> preview(@PathVariable("projectId") String projectId,
                                     @RequestParam(value = "file", required = false) MultipartFile file,
                                     @RequestParam(value = "excel", required = false) MultipartFile excel) throws Exception {
        Long ownerId = currentUser.requireUserId();
        ResponseEntity<?> denied = ProjectAccess.denyUnlessOperable(store, projectId, ownerId);
        if (denied != null) {
            return denied;
        }
        var project = store.getOwnedProject(projectId, ownerId);
        if (project.isEmpty()) {
            return notFound("Unknown project");
        }
        MultipartFile f = file != null && !file.isEmpty() ? file : excel;
        if (f == null || f.isEmpty()) {
            return ResponseEntity.badRequest().body(new ApiError("BAD_REQUEST", "file is required").asMap());
        }
        try {
            return ResponseEntity.ok(previews.preview(projectId, ownerId, f.getOriginalFilename(), f.getBytes(),
                    project.get().getBaseUrl()));
        } catch (InvalidExcelTemplateException e) {
            return ResponseEntity.badRequest().body(new ApiError(
                    e.getErrorCode() == null ? "INVALID_EXCEL" : e.getErrorCode(),
                    e.getMessage() == null ? "Invalid Excel/CSV" : e.getMessage()).asMap());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ApiError("BAD_REQUEST",
                    e.getMessage() == null ? "Invalid upload" : e.getMessage()).asMap());
        }
    }

    @PostMapping(value = "/{projectId}/generated-workbook/commit", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> commit(@PathVariable("projectId") String projectId,
                                    @RequestBody(required = false) CommitRequest body) throws Exception {
        Long ownerId = currentUser.requireUserId();
        ResponseEntity<?> denied = ProjectAccess.denyUnlessOperable(store, projectId, ownerId);
        if (denied != null) {
            return denied;
        }
        var project = store.getOwnedProject(projectId, ownerId);
        if (project.isEmpty()) {
            return notFound("Unknown project");
        }
        if (body == null || body.previewToken() == null || body.previewToken().isBlank()) {
            return ResponseEntity.badRequest().body(new ApiError("BAD_REQUEST", "previewToken is required").asMap());
        }
        WorkbookUploadPreviewService.Pending p;
        try {
            p = previews.take(body.previewToken().trim(), projectId, ownerId);
        } catch (WorkbookUploadPreviewService.CommitException e) {
            return switch (e.error()) {
                case NOT_FOUND -> notFound(e.getMessage());
                case EXPIRED -> ResponseEntity.status(HttpStatus.GONE)
                        .body(new ApiError("PREVIEW_EXPIRED", e.getMessage()).asMap());
                case STALE -> ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(new ApiError("PREVIEW_STALE", e.getMessage()).asMap());
            };
        }
        try {
            Map<String, Object> out = workbooks.mergeUploadFile(projectId, p.filename(), p.bytes(),
                    project.get().getBaseUrl(), p.headRevision());
            out.put("committed", true);
            // B19: echo the preview's counts (exact: commit is refused as PREVIEW_STALE if the library moved)
            out.put("addedCount", p.addedCount());
            out.put("updatedCount", p.updatedCount());
            out.put("unchangedCount", p.unchangedCount());
            return ResponseEntity.ok(out);
        } catch (InvalidExcelTemplateException e) {
            return ResponseEntity.badRequest().body(new ApiError(
                    e.getErrorCode() == null ? "INVALID_EXCEL" : e.getErrorCode(),
                    e.getMessage() == null ? "Invalid Excel/CSV" : e.getMessage()).asMap());
        } catch (IllegalArgumentException e) {
            if (GenerateQualityGate.isQualityGateFailure(e)) {
                return ResponseEntity.badRequest()
                        .body(new ApiError("QUALITY_GATE", GenerateQualityGate.qualityGateDetail(e)).asMap());
            }
            return ResponseEntity.badRequest().body(new ApiError("BAD_REQUEST",
                    e.getMessage() == null ? "Invalid upload" : e.getMessage()).asMap());
        }
    }

    private static ResponseEntity<?> notFound(String msg) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError("NOT_FOUND", msg).asMap());
    }
}
