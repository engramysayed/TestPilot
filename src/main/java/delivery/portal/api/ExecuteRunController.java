package delivery.portal.api;

import delivery.excel.ExcelTcReader;
import delivery.excel.GenerateQualityGate;
import delivery.excel.InvalidExcelTemplateException;
import delivery.excel.KeelPathCaseFilter;
import delivery.excel.KeelPathCounts;
import delivery.excel.KeelPathSurfaceGuard;
import delivery.excel.ManualTestCase;
import delivery.excel.TcImportRepair;
import delivery.portal.model.JobRecord;
import delivery.portal.model.ProjectRecord;
import delivery.portal.security.CurrentUserService;
import delivery.portal.service.BugReportService;
import delivery.portal.service.ExecuteResultsExcelService;
import delivery.portal.service.ExecuteRunService;
import delivery.portal.service.GeneratedWorkbookService;
import delivery.portal.service.PortalStore;
import delivery.portal.service.ProjectCredentialService;
import delivery.portal.worker.ExecuteWorker;
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

@RestController
@RequestMapping("/api")
public class ExecuteRunController {
    private final PortalStore store;
    private final ExecuteWorker worker;
    private final ExecuteRunService executeRuns;
    private final BugReportService bugReports;
    private final ExecuteResultsExcelService resultsExcel;
    private final CurrentUserService currentUser;
    private final ProjectCredentialService credentials;
    private final GeneratedWorkbookService workbooks;

    public ExecuteRunController(
            PortalStore store,
            ExecuteWorker worker,
            ExecuteRunService executeRuns,
            BugReportService bugReports,
            ExecuteResultsExcelService resultsExcel,
            CurrentUserService currentUser,
            ProjectCredentialService credentials,
            GeneratedWorkbookService workbooks
    ) {
        this.store = store;
        this.worker = worker;
        this.executeRuns = executeRuns;
        this.bugReports = bugReports;
        this.resultsExcel = resultsExcel;
        this.currentUser = currentUser;
        this.credentials = credentials;
        this.workbooks = workbooks;
    }

    @PostMapping("/projects/{projectId}/execute-runs")
    public ResponseEntity<?> createExecuteRun(
            @PathVariable("projectId") String projectId,
            @RequestParam(value = "excel", required = false) MultipartFile excel,
            @RequestParam(value = "useGenerated", required = false, defaultValue = "false") String useGenerated,
            @RequestParam(value = "credentialProfile", required = false) String credentialProfile
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
                    .body(new ApiError("PROJECT_ARCHIVED", "Cannot start execute runs on archived project").asMap());
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
        boolean useStoredWorkbook = isTruthy(useGenerated);
        if (useStoredWorkbook && excel != null && !excel.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(new ApiError("BAD_REQUEST", "Provide either excel upload or useGenerated, not both").asMap());
        }
        Path excelPath;
        if (useStoredWorkbook) {
            try {
                excelPath = workbooks.copyForJob(projectId);
            } catch (IllegalStateException e) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(new ApiError("NO_GENERATED_WORKBOOK",
                                "No generated workbook for this project — generate TCs first").asMap());
            }
        } else {
            if (excel == null || excel.isEmpty()) {
                throw new InvalidExcelTemplateException("INVALID_EXCEL", "excel file is required");
            }
            Path uploadDir = Path.of(System.getProperty("java.io.tmpdir"), "delivery-uploads", projectId);
            Files.createDirectories(uploadDir);
            excelPath = uploadDir.resolve(UUID.randomUUID() + ".xlsx");
            excel.transferTo(excelPath);
        }

        try {
            List<ManualTestCase> cases = new ExcelTcReader().read(excelPath);
            Optional<String> block = KeelPathSurfaceGuard.hardBlock(
                    KeelPathCaseFilter.Surface.EXECUTE, KeelPathCounts.from(cases));
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

        String jobId = "exec_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        JobRecord job = new JobRecord(
                jobId,
                projectId,
                ownerId,
                "EXECUTE",
                excelPath,
                baseUrl,
                username == null ? "" : username,
                password == null ? "" : password,
                false,
                JobRecord.JobKind.EXECUTE
        );
        store.saveJob(job);
        worker.submit(jobId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                "jobId", jobId,
                "status", JobRecord.Status.QUEUED.name()
        ));
    }

    @GetMapping("/execute-runs/{jobId}")
    public ResponseEntity<?> getExecuteRun(@PathVariable("jobId") String jobId) {
        return executeRuns.requireOwnedExecuteJob(jobId, currentUser.requireUserId())
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
                    return ResponseEntity.ok(body);
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ApiError("NOT_FOUND", "Unknown execute run").asMap()));
    }

    @GetMapping("/execute-runs/{jobId}/tcs")
    public ResponseEntity<?> listTcs(@PathVariable("jobId") String jobId) {
        if (executeRuns.requireOwnedExecuteJob(jobId, currentUser.requireUserId()).isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiError("NOT_FOUND", "Unknown execute run").asMap());
        }
        try {
            return ResponseEntity.ok(executeRuns.listTcs(jobId));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiError("IR_READ_FAILED", e.getMessage() == null ? "IR read failed" : e.getMessage()).asMap());
        }
    }

    @GetMapping("/execute-runs/{jobId}/tcs/{tcId}")
    public ResponseEntity<?> getTc(
            @PathVariable("jobId") String jobId,
            @PathVariable("tcId") String tcId
    ) {
        if (executeRuns.requireOwnedExecuteJob(jobId, currentUser.requireUserId()).isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiError("NOT_FOUND", "Unknown execute run").asMap());
        }
        try {
            return ResponseEntity.ok(executeRuns.getTc(jobId, tcId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiError("NOT_FOUND", e.getMessage()).asMap());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiError("IR_READ_FAILED", e.getMessage() == null ? "IR read failed" : e.getMessage()).asMap());
        }
    }

    @GetMapping("/execute-runs/{jobId}/tcs/{tcId}/screenshots/{fileName}")
    public ResponseEntity<?> screenshot(
            @PathVariable("jobId") String jobId,
            @PathVariable("tcId") String tcId,
            @PathVariable("fileName") String fileName
    ) {
        if (executeRuns.requireOwnedExecuteJob(jobId, currentUser.requireUserId()).isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiError("NOT_FOUND", "Unknown execute run").asMap());
        }
        Path file = executeRuns.resolveScreenshot(jobId, tcId, fileName).orElse(null);
        if (file == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiError("NOT_FOUND", "Screenshot not found").asMap());
        }
        Resource resource = new FileSystemResource(file);
        MediaType type = fileName.toLowerCase().endsWith(".png")
                ? MediaType.IMAGE_PNG
                : MediaType.APPLICATION_OCTET_STREAM;
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fileName + "\"")
                .contentType(type)
                .body(resource);
    }

    @GetMapping("/execute-runs/{jobId}/results.xlsx")
    public ResponseEntity<?> exportResultsExcel(@PathVariable("jobId") String jobId) {
        try {
            Optional<byte[]> bytes = resultsExcel.buildWorkbook(jobId, currentUser.requireUserId());
            if (bytes.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ApiError("NOT_FOUND", "Unknown execute run").asMap());
            }
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"execute-results-" + jobId + ".xlsx\"")
                    .contentType(MediaType.parseMediaType(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(bytes.get());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiError("RESULTS_EXPORT_FAILED",
                            e.getMessage() == null ? "Results export failed" : e.getMessage()).asMap());
        }
    }

    @GetMapping("/execute-runs/{jobId}/bug-report")
    public ResponseEntity<?> bugReportJson(@PathVariable("jobId") String jobId) {
        try {
            Optional<Map<String, Object>> report = bugReports.buildReport(jobId, currentUser.requireUserId());
            if (report.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ApiError("NOT_FOUND", "Unknown execute run").asMap());
            }
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"bug-report-" + jobId + ".json\"")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(report.get());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiError("IR_READ_FAILED", e.getMessage() == null ? "IR read failed" : e.getMessage()).asMap());
        }
    }

    @GetMapping("/execute-runs/{jobId}/bug-report.csv")
    public ResponseEntity<?> bugReportCsv(@PathVariable("jobId") String jobId) {
        try {
            Optional<Map<String, Object>> report = bugReports.buildReport(jobId, currentUser.requireUserId());
            if (report.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ApiError("NOT_FOUND", "Unknown execute run").asMap());
            }
            String csv = bugReports.toCsv(report.get());
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"bug-report-" + jobId + ".csv\"")
                    .contentType(new MediaType("text", "csv"))
                    .body(csv);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiError("IR_READ_FAILED", e.getMessage() == null ? "IR read failed" : e.getMessage()).asMap());
        }
    }

    private static boolean isTruthy(String value) {
        return "true".equalsIgnoreCase(value) || "1".equals(value) || "on".equalsIgnoreCase(value);
    }
}
