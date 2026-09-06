package delivery.portal.api;

import delivery.excel.GenerateQualityGate;
import delivery.portal.model.JobRecord;
import delivery.portal.model.ProjectRecord;
import delivery.portal.security.CurrentUserService;
import delivery.portal.service.AuthoringReviewService;
import delivery.portal.service.CompareJobFiles;
import delivery.portal.service.GenerateModelService;
import delivery.portal.service.PortalStore;
import delivery.portal.service.TcGenerateService;
import delivery.portal.service.TcImportService;
import delivery.portal.worker.CompareGenerateWorker;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects")
public class GenerateTcController {

    private final TcGenerateService generate;
    private final TcImportService tcImport;
    private final CurrentUserService currentUser;
    private final PortalStore store;
    private final CompareGenerateWorker compareWorker;
    private final GenerateModelService models;
    private final AuthoringReviewService authoringReviews;

    public GenerateTcController(
            TcGenerateService generate,
            TcImportService tcImport,
            CurrentUserService currentUser,
            PortalStore store,
            CompareGenerateWorker compareWorker,
            GenerateModelService models,
            AuthoringReviewService authoringReviews
    ) {
        this.generate = generate;
        this.tcImport = tcImport;
        this.currentUser = currentUser;
        this.store = store;
        this.compareWorker = compareWorker;
        this.models = models;
        this.authoringReviews = authoringReviews;
    }
    public record GenerateTcRequest(String stories, Map<String, Object> options) {
    }

    public record CompareGenerateRequest(String stories, String modelA, String modelB) {
    }

    public record SaveComparedRequest(String csv, String model, String coverageNotes) {
    }

    public record ImportGenerateRequest(String raw, String format) {
    }

    public record AuthoringReviewRequest(String provider, String requirementsNotes, String stories) {
    }

    @PostMapping(value = "/{projectId}/generate/authoring-review", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> authoringReview(
            @PathVariable("projectId") String projectId,
            @RequestBody AuthoringReviewRequest body
    ) {
        try {
            Map<String, Object> result = authoringReviews.review(
                    projectId,
                    currentUser.requireUserId(),
                    body == null ? null : body.provider(),
                    body == null ? null : body.requirementsNotes(),
                    body == null ? null : body.stories()
            );
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            if ("Unknown project".equals(e.getMessage())) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ApiError("NOT_FOUND", e.getMessage()).asMap());
            }
            if (GenerateQualityGate.isQualityGateFailure(e)) {
                return ResponseEntity.badRequest()
                        .body(new ApiError(
                                "QUALITY_GATE",
                                GenerateQualityGate.qualityGateDetail(e)).asMap());
            }
            return ResponseEntity.badRequest()
                    .body(new ApiError("BAD_REQUEST", e.getMessage()).asMap());
        } catch (IllegalStateException e) {
            String message = e.getMessage() == null ? "Provider unavailable" : e.getMessage();
            if (message.contains("NO_GENERATED_WORKBOOK")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ApiError(
                                "NO_GENERATED_WORKBOOK",
                                "No generated workbook saved for this project").asMap());
            }
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new ApiError("PROVIDER_UNAVAILABLE", message).asMap());
        } catch (Exception e) {
            String message = e.getMessage() == null ? "Provider unavailable" : e.getMessage();
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new ApiError("PROVIDER_UNAVAILABLE", message).asMap());
        }
    }

    @PostMapping(value = "/{projectId}/generate/compare-async", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> compareAsync(
            @PathVariable("projectId") String projectId,
            @RequestBody CompareGenerateRequest body
    ) throws Exception {
        if (body == null || body.stories() == null || body.stories().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(new ApiError("BAD_REQUEST", "stories field is required").asMap());
        }
        if (body.modelA() == null || body.modelA().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(new ApiError("BAD_REQUEST", "modelA field is required").asMap());
        }
        if (body.modelB() == null || body.modelB().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(new ApiError("BAD_REQUEST", "modelB field is required").asMap());
        }
        String modelA = body.modelA().trim();
        String modelB = body.modelB().trim();
        if (modelA.equalsIgnoreCase(modelB)) {
            return ResponseEntity.badRequest()
                    .body(new ApiError("BAD_REQUEST", "modelA and modelB must differ").asMap());
        }
        Long ownerId = currentUser.requireUserId();
        ProjectRecord project = store.getOwnedProject(projectId, ownerId).orElse(null);
        if (project == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiError("NOT_FOUND", "Unknown project").asMap());
        }
        if (project.isArchived()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ApiError("PROJECT_ARCHIVED", "Cannot start jobs on archived project").asMap());
        }
        try {
            models.resolve(modelA);
            models.resolve(modelB);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiError("INVALID_MODEL", e.getMessage()).asMap());
        }
        String jobId = "gencmp_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Path payloadPath = CompareJobFiles.payloadPath(projectId, jobId);
        CompareJobFiles.writePayload(payloadPath, new CompareJobFiles.Payload(body.stories().trim(), modelA, modelB));
        JobRecord job = new JobRecord(
                jobId,
                projectId,
                ownerId,
                "GENERATE_COMPARE",
                payloadPath,
                project.getBaseUrl() == null ? "" : project.getBaseUrl(),
                "",
                "",
                false,
                JobRecord.JobKind.GENERATE_COMPARE
        );
        job.setGenerateModel(modelA + " vs " + modelB);
        store.saveJob(job);
        compareWorker.submit(jobId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                "jobId", jobId,
                "status", JobRecord.Status.QUEUED.name(),
                "statusUrl", "/status?jobId=" + jobId
        ));
    }

    @PostMapping(value = "/{projectId}/generate/compare", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> compare(
            @PathVariable("projectId") String projectId,
            @RequestBody CompareGenerateRequest body
    ) {
        if (body == null || body.stories() == null || body.stories().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(new ApiError("BAD_REQUEST", "stories field is required").asMap());
        }
        if (body.modelA() == null || body.modelA().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(new ApiError("BAD_REQUEST", "modelA field is required").asMap());
        }
        if (body.modelB() == null || body.modelB().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(new ApiError("BAD_REQUEST", "modelB field is required").asMap());
        }
        try {
            Map<String, Object> result = generate.compare(
                    projectId,
                    currentUser.requireUserId(),
                    body.stories(),
                    body.modelA().trim(),
                    body.modelB().trim()
            );
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return mapGenerateArgumentException(e);
        } catch (IllegalStateException e) {
            return mapGenerateStateException(e);
        } catch (Exception e) {
            return mapGenerateException(e);
        }
    }

    @PostMapping(value = "/{projectId}/generate/save", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> saveCompared(
            @PathVariable("projectId") String projectId,
            @RequestBody SaveComparedRequest body
    ) {
        if (body == null || body.csv() == null || body.csv().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(new ApiError("BAD_REQUEST", "csv field is required").asMap());
        }
        try {
            Map<String, Object> result = generate.saveCompared(
                    projectId,
                    currentUser.requireUserId(),
                    body.csv(),
                    body.model(),
                    body.coverageNotes()
            );
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return mapGenerateArgumentException(e);
        } catch (IllegalStateException e) {
            return mapGenerateStateException(e);
        } catch (Exception e) {
            return mapGenerateException(e);
        }
    }

    @PostMapping(value = "/{projectId}/generate/import", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> importRaw(
            @PathVariable("projectId") String projectId,
            @RequestBody ImportGenerateRequest body
    ) {
        if (body == null || body.raw() == null || body.raw().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(new ApiError("BAD_REQUEST", "raw field is required").asMap());
        }
        try {
            Map<String, Object> result = tcImport.importRaw(
                    projectId,
                    currentUser.requireUserId(),
                    body.raw(),
                    body.format(),
                    "PASTE_IMPORT"
            );
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return mapGenerateArgumentException(e);
        } catch (IllegalStateException e) {
            return mapGenerateStateException(e);
        } catch (Exception e) {
            return mapGenerateException(e);
        }
    }

    @PostMapping(value = "/{projectId}/generate-tcs", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> generate(
            @PathVariable("projectId") String projectId,
            @RequestBody GenerateTcRequest body
    ) {
        if (body == null || body.stories() == null || body.stories().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(new ApiError("BAD_REQUEST", "stories field is required").asMap());
        }
        boolean reviewPass = false;
        String model = null;
        if (body.options() != null) {
            if (body.options().get("reviewPass") instanceof Boolean b) {
                reviewPass = b;
            }
            if (body.options().get("model") instanceof String s && !s.isBlank()) {
                model = s.trim();
            }
        }
        try {
            Map<String, Object> result = generate.generate(
                    projectId,
                    currentUser.requireUserId(),
                    body.stories(),
                    reviewPass,
                    model
            );
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return mapGenerateArgumentException(e);
        } catch (IllegalStateException e) {
            return mapGenerateStateException(e);
        } catch (Exception e) {
            return mapGenerateException(e);
        }
    }

    private ResponseEntity<?> mapGenerateArgumentException(IllegalArgumentException e) {
        if ("Unknown project".equals(e.getMessage())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiError("NOT_FOUND", e.getMessage()).asMap());
        }
        if (e.getMessage() != null && e.getMessage().startsWith("Unsupported generate model")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiError("INVALID_MODEL", e.getMessage()).asMap());
        }
        if (GenerateQualityGate.isQualityGateFailure(e)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiError("QUALITY_GATE", GenerateQualityGate.qualityGateDetail(e)).asMap());
        }
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ApiError("INVALID_STORIES", e.getMessage()).asMap());
    }

    private ResponseEntity<?> mapGenerateStateException(IllegalStateException e) {
        String msg = e.getMessage() == null ? "GENERATE_FAILED" : e.getMessage();
        String code;
        HttpStatus status;
        if (msg.startsWith("OLLAMA_TIMEOUT")) {
            code = "OLLAMA_TIMEOUT";
            status = HttpStatus.REQUEST_TIMEOUT;
        } else if ("OLLAMA_UNAVAILABLE".equals(msg)) {
            code = "OLLAMA_UNAVAILABLE";
            status = HttpStatus.SERVICE_UNAVAILABLE;
        } else if ("GENERATE_DISABLED".equals(msg)) {
            code = "GENERATE_DISABLED";
            status = HttpStatus.SERVICE_UNAVAILABLE;
        } else {
            code = msg;
            status = HttpStatus.UNPROCESSABLE_ENTITY;
        }
        String detail = e.getCause() == null ? msg : String.valueOf(e.getCause().getMessage());
        return ResponseEntity.status(status)
                .body(new ApiError(code, detail).asMap());
    }

    private ResponseEntity<?> mapGenerateException(Exception e) {
        String msg = e.getMessage() == null ? "Generation failed" : e.getMessage();
        if (msg.contains("Unknown KeelPath") || msg.contains("Missing CSV") || msg.contains("no data rows")) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(new ApiError("INVALID_CSV", msg).asMap());
        }
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ApiError("GENERATE_FAILED", msg).asMap());
    }
}
