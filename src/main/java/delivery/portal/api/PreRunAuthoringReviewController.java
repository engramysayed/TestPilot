package delivery.portal.api;

import delivery.excel.ExcelTcReader;
import delivery.excel.GenerateQualityGate;
import delivery.excel.ManualTestCase;
import delivery.portal.security.CurrentUserService;
import delivery.portal.service.AuthoringReviewService;
import delivery.portal.service.GeneratedWorkbookService;
import delivery.portal.service.JobWorkbookResolver;
import delivery.portal.service.PortalStore;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Preview-only Cursor review of the exact workbook inputs selected for Automate/Execute.
 */
@RestController
@RequestMapping("/api/projects")
public class PreRunAuthoringReviewController {
    private static final Logger log = LogManager.getLogger(PreRunAuthoringReviewController.class);

    private final PortalStore store;
    private final GeneratedWorkbookService workbooks;
    private final AuthoringReviewService reviews;
    private final CurrentUserService currentUser;

    public PreRunAuthoringReviewController(
            PortalStore store,
            GeneratedWorkbookService workbooks,
            AuthoringReviewService reviews,
            CurrentUserService currentUser
    ) {
        this.store = store;
        this.workbooks = workbooks;
        this.reviews = reviews;
        this.currentUser = currentUser;
    }

    @PostMapping(
            value = "/{projectId}/pre-run-authoring-review",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<?> review(
            @PathVariable("projectId") String projectId,
            @RequestParam(value = "excel", required = false) MultipartFile excel,
            @RequestParam(value = "useGenerated", required = false, defaultValue = "false") String useGenerated,
            @RequestParam(value = "tcIds", required = false) List<String> tcIds
    ) {
        Long ownerId = currentUser.requireUserId();
        if (store.getOwnedProject(projectId, ownerId).isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiError("NOT_FOUND", "Unknown project").asMap());
        }

        Path materialized = null;
        try {
            boolean useLibrary = isTruthy(useGenerated);
            materialized = JobWorkbookResolver.resolve(
                    workbooks, projectId, useLibrary, tcIds, excel);
            List<ManualTestCase> cases = new ExcelTcReader(true).read(materialized);
            log.info("PRE_RUN_REVIEW_START project={} provider=cursor cases={}", projectId, cases.size());
            Map<String, Object> result = reviews.reviewCases(
                    projectId, ownerId, cases, "cursor", "", "", "");
            log.info("PRE_RUN_REVIEW_COMPLETE project={} cases={} previewOk={} findings={}",
                    projectId,
                    result.get("tcCount"),
                    result.get("previewOk"),
                    result.get("findings") instanceof List<?> findings ? findings.size() : 0);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            if (GenerateQualityGate.isQualityGateFailure(e)) {
                return ResponseEntity.badRequest()
                        .body(new ApiError(
                                "QUALITY_GATE",
                                GenerateQualityGate.qualityGateDetail(e)).asMap());
            }
            return ResponseEntity.badRequest()
                    .body(new ApiError("BAD_REQUEST", safeMessage(e, "Invalid review input")).asMap());
        } catch (IllegalStateException e) {
            String message = safeMessage(e, "Cursor review unavailable");
            if (message.contains("NO_GENERATED_WORKBOOK")) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(new ApiError(
                                "NO_GENERATED_WORKBOOK",
                                "No generated workbook saved for this project").asMap());
            }
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new ApiError("PROVIDER_UNAVAILABLE", message).asMap());
        } catch (Exception e) {
            log.error("PRE_RUN_REVIEW_FAILED project={}", projectId, e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new ApiError(
                            "PROVIDER_UNAVAILABLE",
                            safeMessage(e, "Cursor review unavailable")).asMap());
        } finally {
            if (materialized != null) {
                try {
                    Files.deleteIfExists(materialized);
                } catch (Exception ignored) {
                    // Temporary review input cleanup is best-effort.
                }
            }
        }
    }

    private static boolean isTruthy(String value) {
        return "true".equalsIgnoreCase(value)
                || "1".equals(value)
                || "on".equalsIgnoreCase(value);
    }

    private static String safeMessage(Exception e, String fallback) {
        return e.getMessage() == null || e.getMessage().isBlank() ? fallback : e.getMessage();
    }
}
