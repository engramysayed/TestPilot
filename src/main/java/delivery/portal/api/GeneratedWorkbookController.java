package delivery.portal.api;

import delivery.excel.GenerateQualityGate;
import delivery.portal.security.CurrentUserService;
import delivery.portal.service.GeneratedWorkbookService;
import delivery.portal.service.PortalStore;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/projects")
public class GeneratedWorkbookController {

    private final PortalStore store;
    private final GeneratedWorkbookService workbooks;
    private final CurrentUserService currentUser;

    public GeneratedWorkbookController(
            PortalStore store,
            GeneratedWorkbookService workbooks,
            CurrentUserService currentUser
    ) {
        this.store = store;
        this.workbooks = workbooks;
        this.currentUser = currentUser;
    }

    public record KeelPathRowUpdate(String tcId, String keelPath) {
    }

    public record UpdateKeelPathsRequest(List<KeelPathRowUpdate> rows) {
    }

    public record UpdateCaseFieldsRequest(
            String title,
            String preconditions,
            String steps,
            String expectedResult,
            String testData,
            String priority,
            String tags,
            String visualAssertion,
            String keelPath
    ) {
    }

    @GetMapping("/{projectId}/generated-workbook")
    public ResponseEntity<?> describe(@PathVariable("projectId") String projectId) throws Exception {
        Long ownerId = currentUser.requireUserId();
        if (store.getOwnedProject(projectId, ownerId).isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiError("NOT_FOUND", "Unknown project").asMap());
        }
        return workbooks.describe(projectId)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> {
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("available", false);
                    body.put("projectId", projectId);
                    return ResponseEntity.ok(body);
                });
    }

    @PutMapping(value = "/{projectId}/generated-workbook/rows", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> updateRows(
            @PathVariable("projectId") String projectId,
            @RequestBody UpdateKeelPathsRequest body
    ) throws Exception {
        Long ownerId = currentUser.requireUserId();
        if (store.getOwnedProject(projectId, ownerId).isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiError("NOT_FOUND", "Unknown project").asMap());
        }
        if (body == null || body.rows() == null || body.rows().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(new ApiError("BAD_REQUEST", "rows field is required").asMap());
        }
        Map<String, String> updates = new LinkedHashMap<>();
        for (KeelPathRowUpdate row : body.rows()) {
            if (row == null || row.tcId() == null || row.tcId().isBlank()) {
                continue;
            }
            updates.put(row.tcId().trim(), row.keelPath());
        }
        if (updates.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(new ApiError("BAD_REQUEST", "rows must include at least one tcId").asMap());
        }
        try {
            return ResponseEntity.ok(workbooks.updateKeelPaths(projectId, updates));
        } catch (IllegalStateException e) {
            if ("NO_GENERATED_WORKBOOK".equals(e.getMessage())) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(new ApiError("NO_GENERATED_WORKBOOK", "No generated workbook saved for this project").asMap());
            }
            throw e;
        } catch (IllegalArgumentException e) {
            String msg = e.getMessage() == null ? "Invalid KeelPath" : e.getMessage();
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiError("INVALID_KEEL_PATH", msg).asMap());
        }
    }

    public record UpdateCoverageNotesRequest(String coverageNotes) {
    }

    @PutMapping(value = "/{projectId}/generated-workbook/coverage-notes", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> updateCoverageNotes(
            @PathVariable("projectId") String projectId,
            @RequestBody UpdateCoverageNotesRequest body
    ) throws Exception {
        Long ownerId = currentUser.requireUserId();
        if (store.getOwnedProject(projectId, ownerId).isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiError("NOT_FOUND", "Unknown project").asMap());
        }
        try {
            return ResponseEntity.ok(workbooks.updateCoverageNotes(
                    projectId, body == null ? "" : body.coverageNotes()));
        } catch (IllegalStateException e) {
            if ("NO_GENERATED_WORKBOOK".equals(e.getMessage())) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(new ApiError("NO_GENERATED_WORKBOOK",
                                "No generated workbook saved for this project").asMap());
            }
            throw e;
        }
    }

    public record DeleteCasesRequest(List<String> tcIds) {
    }

    @GetMapping("/{projectId}/generated-workbook/cases")
    public ResponseEntity<?> listCases(@PathVariable("projectId") String projectId) throws Exception {
        Long ownerId = currentUser.requireUserId();
        if (store.getOwnedProject(projectId, ownerId).isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiError("NOT_FOUND", "Unknown project").asMap());
        }
        try {
            return ResponseEntity.ok(workbooks.listCases(projectId));
        } catch (IllegalStateException e) {
            if ("NO_GENERATED_WORKBOOK".equals(e.getMessage())) {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("available", false);
                body.put("projectId", projectId);
                body.put("cases", List.of());
                return ResponseEntity.ok(body);
            }
            throw e;
        }
    }

    @DeleteMapping(value = "/{projectId}/generated-workbook/cases", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> deleteCases(
            @PathVariable("projectId") String projectId,
            @RequestBody DeleteCasesRequest body
    ) throws Exception {
        Long ownerId = currentUser.requireUserId();
        if (store.getOwnedProject(projectId, ownerId).isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiError("NOT_FOUND", "Unknown project").asMap());
        }
        try {
            return ResponseEntity.ok(workbooks.deleteCases(
                    projectId, body == null ? List.of() : body.tcIds()));
        } catch (IllegalStateException e) {
            if ("NO_GENERATED_WORKBOOK".equals(e.getMessage())) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(new ApiError("NO_GENERATED_WORKBOOK",
                                "No generated workbook saved for this project").asMap());
            }
            throw e;
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(new ApiError("BAD_REQUEST",
                            e.getMessage() == null ? "Invalid delete" : e.getMessage()).asMap());
        }
    }

    @PutMapping(value = "/{projectId}/generated-workbook/cases/{tcId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> updateCase(
            @PathVariable("projectId") String projectId,
            @PathVariable("tcId") String tcId,
            @RequestBody UpdateCaseFieldsRequest body
    ) throws Exception {
        Long ownerId = currentUser.requireUserId();
        var projectOpt = store.getOwnedProject(projectId, ownerId);
        if (projectOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiError("NOT_FOUND", "Unknown project").asMap());
        }
        if (body == null) {
            return ResponseEntity.badRequest()
                    .body(new ApiError("BAD_REQUEST", "Request body is required").asMap());
        }
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("title", body.title());
        fields.put("preconditions", body.preconditions());
        fields.put("steps", body.steps());
        fields.put("expectedResult", body.expectedResult());
        fields.put("testData", body.testData());
        fields.put("priority", body.priority());
        fields.put("tags", body.tags());
        fields.put("visualAssertion", body.visualAssertion());
        fields.put("keelPath", body.keelPath());
        try {
            return ResponseEntity.ok(workbooks.updateCaseFields(
                    projectId, tcId, fields, projectOpt.get().getBaseUrl()));
        } catch (IllegalStateException e) {
            if ("NO_GENERATED_WORKBOOK".equals(e.getMessage())) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(new ApiError("NO_GENERATED_WORKBOOK", "No generated workbook saved for this project").asMap());
            }
            throw e;
        } catch (IllegalArgumentException e) {
            if (GenerateQualityGate.isQualityGateFailure(e)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ApiError("QUALITY_GATE", GenerateQualityGate.qualityGateDetail(e)).asMap());
            }
            String msg = e.getMessage() == null ? "Invalid case update" : e.getMessage();
            if (msg.contains("Unknown KeelPath") || msg.startsWith("Unknown tcId")) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ApiError("BAD_REQUEST", msg).asMap());
            }
            throw e;
        }
    }
}
