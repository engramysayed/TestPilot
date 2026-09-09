package delivery.portal.api;

import delivery.excel.ExcelTcReader;
import delivery.excel.GenerateQualityGate;
import delivery.excel.InvalidExcelTemplateException;
import delivery.excel.KeelPathCaseFilter;
import delivery.excel.KeelPathCounts;
import delivery.excel.KeelPathSurfaceGuard;
import delivery.excel.ManualTestCase;
import delivery.excel.TcImportRepair;
import delivery.portal.DeliveryPortalProperties;
import delivery.portal.model.JobRecord;
import delivery.portal.model.ProjectRecord;
import delivery.portal.security.CurrentUserService;
import delivery.portal.service.CompareJobFiles;
import delivery.portal.service.GeneratedWorkbookService;
import delivery.portal.service.PortalStore;
import delivery.portal.service.ProjectCredentialService;
import delivery.portal.worker.ConversionWorker;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class JobController {
    private final PortalStore store;
    private final ConversionWorker worker;
    private final CurrentUserService currentUser;
    private final ProjectCredentialService credentials;
    private final GeneratedWorkbookService workbooks;
    private final DeliveryPortalProperties portalProperties;

    public JobController(PortalStore store, ConversionWorker worker, CurrentUserService currentUser,
                         ProjectCredentialService credentials, GeneratedWorkbookService workbooks,
                         DeliveryPortalProperties portalProperties) {
        this.store = store;
        this.worker = worker;
        this.currentUser = currentUser;
        this.credentials = credentials;
        this.workbooks = workbooks;
        this.portalProperties = portalProperties;
    }

    @PostMapping("/projects/{projectId}/jobs")
    public ResponseEntity<?> createJob(
            @PathVariable("projectId") String projectId,
            @RequestParam(value = "excel", required = false) MultipartFile excel,
            @RequestParam(value = "useGenerated", required = false, defaultValue = "false") String useGenerated,
            @RequestParam(value = "tcIds", required = false) List<String> tcIds,
            @RequestParam(value = "credentialProfile", required = false) String credentialProfile,
            @RequestParam(value = "mode", defaultValue = "NEW") String mode,
            @RequestParam(value = "finalRevise", required = false, defaultValue = "false") String finalRevise
    ) throws Exception {
        Long ownerId = currentUser.requireUserId();
        if (store.getOwnedProject(projectId, ownerId).isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiError("NOT_FOUND", "Unknown project").asMap());
        }
        store.ensureBaseUrlBackfill(projectId);
        ProjectRecord project = store.getOwnedProject(projectId, ownerId).orElseThrow();
        if (project.isArchived()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ApiError("PROJECT_ARCHIVED", "Cannot start jobs on archived project").asMap());
        }
        String baseUrl = project.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(new ApiError("MISSING_BASE_URL", "Project baseUrl is required").asMap());
        }
        String username = "";
        String password = "";
        if (!credentials.list(projectId).isEmpty()) {
            if (credentialProfile == null || credentialProfile.isBlank()) {
                return ResponseEntity.badRequest()
                        .body(new ApiError("CREDENTIAL_PROFILE_REQUIRED", "credentialProfile is required").asMap());
            }
            ProjectCredentialService.ResolvedCredential resolved =
                    credentials.resolveForJob(projectId, credentialProfile.trim());
            username = resolved.username();
            password = resolved.passwordPlain();
        }
        String normalizedMode = mode == null ? "NEW" : mode.trim().toUpperCase();
        if (!"NEW".equals(normalizedMode) && !"UPDATE".equals(normalizedMode)) {
            throw new IllegalArgumentException("mode must be NEW or UPDATE");
        }
        if ("UPDATE".equals(normalizedMode) && !store.hasStoredFramework(projectId)) {
            throw new IllegalStateException("UPDATE_WITHOUT_FRAMEWORK");
        }
        boolean useStoredWorkbook = isTruthy(useGenerated);
        Path excelPath;
        try {
            excelPath = delivery.portal.service.JobWorkbookResolver.resolve(
                    workbooks, projectId, useStoredWorkbook, tcIds, excel);
        } catch (IllegalStateException e) {
            if ("NO_GENERATED_WORKBOOK".equals(e.getMessage())) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(new ApiError("NO_GENERATED_WORKBOOK",
                                "No generated workbook for this project — generate TCs first").asMap());
            }
            throw e;
        } catch (IllegalArgumentException e) {
            ResponseEntity<Map<String, String>> callBefore = CallBeforeApiErrors.badRequestOrNull(e);
            if (callBefore != null) {
                return callBefore;
            }
            throw e;
        }

        try {
            List<ManualTestCase> cases = new ExcelTcReader(true).read(excelPath);
            Optional<String> block = KeelPathSurfaceGuard.hardBlock(
                    KeelPathCaseFilter.Surface.AUTOMATE, KeelPathCounts.from(cases));
            if (block.isPresent()) {
                Files.deleteIfExists(excelPath);
                return ResponseEntity.badRequest()
                        .body(new ApiError("SURFACE_MISMATCH", block.get()).asMap());
            }
            List<String> gateErrors = GenerateQualityGate.validate(
                    TcImportRepair.repairCases(cases), baseUrl);
            if (!gateErrors.isEmpty()) {
                Files.deleteIfExists(excelPath);
                return ResponseEntity.badRequest()
                        .body(new ApiError("QUALITY_GATE", String.join("; ", gateErrors)).asMap());
            }
        } catch (InvalidExcelTemplateException e) {
            Files.deleteIfExists(excelPath);
            throw e;
        }

        boolean clientWantsFinalRevise = "true".equalsIgnoreCase(finalRevise)
                || "1".equals(finalRevise)
                || "on".equalsIgnoreCase(finalRevise);
        boolean wantFinalRevise = portalProperties.isFinalReviseEnabled() && clientWantsFinalRevise;
        String jobId = "job_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        JobRecord job = new JobRecord(
                jobId,
                projectId,
                ownerId,
                normalizedMode,
                excelPath,
                baseUrl,
                username == null ? "" : username,
                password == null ? "" : password,
                wantFinalRevise,
                JobRecord.JobKind.CONVERT
        );
        store.saveJob(job);
        worker.submit(jobId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                "jobId", jobId,
                "status", JobRecord.Status.QUEUED.name()
        ));
    }

    @GetMapping("/jobs")
    public ResponseEntity<List<Map<String, Object>>> listJobs(
            @RequestParam(value = "projectId", required = false) String projectId,
            @RequestParam(value = "kind", required = false) String kind) {
        Long ownerId = currentUser.requireUserId();
        if (projectId != null && !projectId.isBlank()) {
            if (store.getOwnedProject(projectId, ownerId).isEmpty()) {
                return ResponseEntity.ok(List.of());
            }
        }
        String kindFilter = kind == null || kind.isBlank() ? null : kind.trim().toUpperCase();
        List<Map<String, Object>> rows = store.listJobEntities(ownerId).stream()
                .filter(e -> projectId == null || projectId.isBlank()
                        || e.getProjectId().equals(projectId))
                .filter(e -> kindFilter == null || kindFilter.equals(
                        e.getJobKind() == null ? JobRecord.JobKind.CONVERT.name() : e.getJobKind()))
                .map(e -> {
                    // Prefer in-memory live progress so Recent runs matches the open detail panel.
                    var live = store.getJob(e.getJobId());
                    String status = live.map(j -> j.getStatus().name()).orElse(e.getStatus());
                    int passed = live.map(JobRecord::getPassedCount).orElse(e.getPassedCount());
                    int todo = live.map(JobRecord::getTodoCount).orElse(e.getTodoCount());
                    int progressCurrent = live.map(JobRecord::getProgressCurrent).orElse(e.getProgressCurrent());
                    int progressTotal = live.map(JobRecord::getProgressTotal).orElse(e.getProgressTotal());
                    String message = live.map(j -> j.getMessage() == null ? "" : j.getMessage())
                            .orElse(e.getMessage() == null ? "" : e.getMessage());
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("jobId", e.getJobId());
                    m.put("projectId", e.getProjectId());
                    m.put("jobKind", e.getJobKind() == null ? JobRecord.JobKind.CONVERT.name() : e.getJobKind());
                    m.put("mode", e.getMode());
                    m.put("status", status);
                    m.put("passedCount", passed);
                    m.put("todoCount", todo);
                    m.put("message", message);
                    m.put("progressCurrent", progressCurrent);
                    m.put("progressTotal", progressTotal);
                    m.put("createdAt", e.getCreatedAt() == null ? "" : e.getCreatedAt().toString());
                    m.put("downloadable", JobRecord.isDownloadable(
                            JobRecord.parseJobKind(e.getJobKind()), status));
                    m.put("softBlocked", "COMPLETED_WITH_BLOCK".equals(status));
                    return m;
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(rows);
    }

    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<?> getJob(@PathVariable("jobId") String jobId) {
        return store.getOwnedJob(jobId, currentUser.requireUserId())
                .<ResponseEntity<?>>map(job -> {
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("jobId", job.getJobId());
                    body.put("projectId", job.getProjectId());
                    body.put("jobKind", job.getJobKind().name());
                    body.put("mode", job.getMode());
                    body.put("status", job.getStatus().name());
                    body.put("passedCount", job.getPassedCount());
                    body.put("todoCount", job.getTodoCount());
                    body.put("message", job.getMessage() == null ? "" : job.getMessage());
                    body.put("progressCurrent", job.getProgressCurrent());
                    body.put("progressTotal", job.getProgressTotal());
                    body.put("createdAt", job.getCreatedAt() == null ? "" : job.getCreatedAt().toString());
                    body.put("downloadable", JobRecord.isDownloadable(job.getJobKind(), job.getStatus()));
                    body.put("softBlocked", job.getStatus() == JobRecord.Status.COMPLETED_WITH_BLOCK);
                    body.put("finalRevise", job.isFinalRevise());
                    body.put("cancellable", isCancellable(job.getStatus()));
                    return ResponseEntity.ok(body);
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ApiError("NOT_FOUND", "Unknown job").asMap()));
    }

    @PostMapping("/jobs/{jobId}/cancel")
    public ResponseEntity<?> cancelJob(@PathVariable("jobId") String jobId) {
        return store.getOwnedJob(jobId, currentUser.requireUserId())
                .<ResponseEntity<?>>map(job -> {
                    if (!isCancellable(job.getStatus())) {
                        return ResponseEntity.status(HttpStatus.CONFLICT)
                                .body(new ApiError("NOT_CANCELLABLE",
                                        "Job is not running or queued").asMap());
                    }
                    store.requestCancel(jobId);
                    return ResponseEntity.accepted().body(Map.of(
                            "jobId", jobId,
                            "status", job.getStatus().name(),
                            "cancelRequested", true
                    ));
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ApiError("NOT_FOUND", "Unknown job").asMap()));
    }

    /**
     * Immediate owner stop for stuck QUEUED/RUNNING jobs (marks CANCELLED now so Delete works).
     */
    @PostMapping("/jobs/{jobId}/force-stop")
    public ResponseEntity<?> forceStopJob(@PathVariable("jobId") String jobId) {
        Optional<String> result = store.forceStopOwnedJob(jobId, currentUser.requireUserId());
        if (result.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiError("NOT_FOUND", "Unknown job").asMap());
        }
        if ("ALREADY_DONE".equals(result.get())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ApiError("NOT_CANCELLABLE", "Job is not running or queued").asMap());
        }
        return ResponseEntity.accepted().body(Map.of(
                "jobId", jobId,
                "status", JobRecord.Status.CANCELLED.name(),
                "forceStopped", true
        ));
    }

    @GetMapping("/jobs/{jobId}/compare-result")
    public ResponseEntity<?> compareResult(@PathVariable("jobId") String jobId) {
        JobRecord job = store.getOwnedJob(jobId, currentUser.requireUserId()).orElse(null);
        if (job == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiError("NOT_FOUND", "Unknown job").asMap());
        }
        if (job.getJobKind() != JobRecord.JobKind.GENERATE_COMPARE) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ApiError("NOT_COMPARE", "Job is not a model comparison").asMap());
        }
        if (job.getStatus() != JobRecord.Status.COMPLETED) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ApiError("NOT_READY", "Comparison is not completed").asMap());
        }
        Path resultPath = store.resolveZip(job).orElse(null);
        if (resultPath == null || !Files.isRegularFile(resultPath)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ApiError("NOT_READY", "Comparison result is not available").asMap());
        }
        try {
            return ResponseEntity.ok(CompareJobFiles.readResult(resultPath));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(new ApiError("INVALID_RESULT", "Could not read comparison result").asMap());
        }
    }

    @GetMapping("/jobs/{jobId}/download")
    public ResponseEntity<?> download(@PathVariable("jobId") String jobId) {
        JobRecord job = store.getOwnedJob(jobId, currentUser.requireUserId()).orElse(null);
        if (job == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiError("NOT_FOUND", "Unknown job").asMap());
        }
        if (job.getJobKind() == JobRecord.JobKind.GENERATE_BATCH) {
            if (!JobRecord.isDownloadable(job.getJobKind(), job.getStatus())) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(new ApiError("NOT_READY", "Job is not completed").asMap());
            }
            Path csv = store.resolveZip(job).orElse(null);
            if (csv == null || !Files.isRegularFile(csv)) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(new ApiError("NOT_READY", "CSV output is not available").asMap());
            }
            String filename = csv.getFileName().toString();
            if (!filename.toLowerCase().endsWith(".csv")) {
                filename = "generated-tcs.csv";
            }
            Resource resource = new FileSystemResource(csv);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.parseMediaType("text/csv"))
                    .body(resource);
        }
        if (job.getJobKind() != JobRecord.JobKind.CONVERT && job.getJobKind() != JobRecord.JobKind.HUNT) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ApiError("NOT_CONVERT", "Only Automate and Bug Hunter jobs have downloadable ZIPs").asMap());
        }
        if (!JobRecord.isDownloadable(job.getJobKind(), job.getStatus())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ApiError("NOT_READY", "Job is not completed").asMap());
        }
        Path zip = store.resolveZip(job).orElse(null);
        if (zip == null || !Files.isRegularFile(zip)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ApiError("NOT_READY", "ZIP file is not available").asMap());
        }
        String filename = zip.getFileName().toString();
        if (!filename.toLowerCase().endsWith(".zip")) {
            filename = "framework.zip";
        }
        Resource resource = new FileSystemResource(zip);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    private static boolean isTruthy(String value) {
        return "true".equalsIgnoreCase(value) || "1".equals(value) || "on".equalsIgnoreCase(value);
    }

    private static boolean isCancellable(JobRecord.Status status) {
        return status == JobRecord.Status.QUEUED || status == JobRecord.Status.RUNNING;
    }
}
