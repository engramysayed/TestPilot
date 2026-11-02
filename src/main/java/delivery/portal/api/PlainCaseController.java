package delivery.portal.api;

import delivery.excel.GenerateQualityGate;
import delivery.excel.InvalidExcelTemplateException;
import delivery.excel.KeelPathCaseFilter;
import delivery.excel.KeelPathCounts;
import delivery.excel.KeelPathSurfaceGuard;
import delivery.excel.ExcelTcReader;
import delivery.excel.ManualTestCase;
import delivery.excel.TcImportRepair;
import delivery.authoring.AuthoringEngine;
import delivery.job.AuthoringJobRequestFiles;
import delivery.portal.model.JobRecord;
import delivery.portal.model.ProjectRecord;
import delivery.portal.roles.PermissionEnforcer;
import delivery.portal.security.CurrentUserService;
import delivery.portal.service.PlainCaseDiskStore;
import delivery.portal.service.PlainCaseService;
import delivery.portal.service.PortalStore;
import delivery.portal.service.ProjectCredentialService;
import delivery.portal.worker.ExecuteWorker;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}/plain-cases")
public class PlainCaseController {

    private final PortalStore store;
    private final CurrentUserService currentUser;
    private final PlainCaseService plainCases;
    private final ExecuteWorker worker;
    private final ProjectCredentialService credentials;
    private final PermissionEnforcer permissionEnforcer;

    public PlainCaseController(
            PortalStore store,
            CurrentUserService currentUser,
            PlainCaseService plainCases,
            ExecuteWorker worker,
            ProjectCredentialService credentials,
            PermissionEnforcer permissionEnforcer
    ) {
        this.store = store;
        this.currentUser = currentUser;
        this.plainCases = plainCases;
        this.worker = worker;
        this.credentials = credentials;
        this.permissionEnforcer = permissionEnforcer;
    }

    public record CreateRequest(String title, List<String> lines) {
    }

    public record UpdateRequest(String title, List<String> lines) {
    }

    @GetMapping
    public ResponseEntity<?> list(@PathVariable("projectId") String projectId) {
        ResponseEntity<?> denied = ProjectAccess.denyUnlessReadable(
                store, projectId, currentUser.requireUserId());
        if (denied != null) {
            return denied;
        }
        try {
            List<Map<String, Object>> items = new ArrayList<>();
            for (Map<String, Object> row : plainCases.list(projectId)) {
                items.add(toJson(row));
            }
            return ResponseEntity.ok(items);
        } catch (Exception e) {
            return internalError(e);
        }
    }

    @GetMapping("/{caseId}")
    public ResponseEntity<?> get(
            @PathVariable("projectId") String projectId,
            @PathVariable("caseId") String caseId
    ) {
        ResponseEntity<?> denied = ProjectAccess.denyUnlessReadable(
                store, projectId, currentUser.requireUserId());
        if (denied != null) {
            return denied;
        }
        try {
            return plainCases.get(projectId, caseId)
                    .<ResponseEntity<?>>map(row -> ResponseEntity.ok(toJson(row)))
                    .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body(new ApiError("NOT_FOUND", "Unknown plain case").asMap()));
        } catch (PlainCaseDiskStore.BadPathException e) {
            return ResponseEntity.badRequest()
                    .body(new ApiError("BAD_REQUEST", e.getMessage()).asMap());
        } catch (Exception e) {
            return internalError(e);
        }
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> create(
            @PathVariable("projectId") String projectId,
            @RequestBody(required = false) CreateRequest body
    ) {
        ResponseEntity<?> denied = ProjectAccess.denyUnlessOperable(
                store, projectId, currentUser.requireUserId());
        if (denied != null) {
            return denied;
        }
        try {
            String title = body == null ? null : body.title();
            List<String> lines = body == null ? null : body.lines();
            Map<String, Object> created = plainCases.create(projectId, title, lines);
            return ResponseEntity.status(HttpStatus.CREATED).body(toJson(created));
        } catch (PlainCaseDiskStore.BadRequestException e) {
            return ResponseEntity.badRequest()
                    .body(new ApiError("BAD_REQUEST", e.getMessage()).asMap());
        } catch (Exception e) {
            return internalError(e);
        }
    }

    @PatchMapping(value = "/{caseId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> patch(
            @PathVariable("projectId") String projectId,
            @PathVariable("caseId") String caseId,
            @RequestBody(required = false) UpdateRequest body
    ) {
        ResponseEntity<?> denied = ProjectAccess.denyUnlessOperable(
                store, projectId, currentUser.requireUserId());
        if (denied != null) {
            return denied;
        }
        try {
            String title = body == null ? null : body.title();
            List<String> lines = body == null ? null : body.lines();
            Map<String, Object> updated = plainCases.update(projectId, caseId, title, lines);
            return ResponseEntity.ok(toJson(updated));
        } catch (PlainCaseDiskStore.NotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiError("NOT_FOUND", e.getMessage()).asMap());
        } catch (PlainCaseDiskStore.BadPathException | PlainCaseDiskStore.BadRequestException e) {
            return ResponseEntity.badRequest()
                    .body(new ApiError("BAD_REQUEST", e.getMessage()).asMap());
        } catch (Exception e) {
            return internalError(e);
        }
    }

    /**
     * Materialize the plain case as a Keel workbook and enqueue an EXECUTE job
     * through the same {@link ExecuteWorker} path as {@code POST .../execute-runs}.
     */
    @PostMapping("/{caseId}/execute")
    public ResponseEntity<?> execute(
            @PathVariable("projectId") String projectId,
            @PathVariable("caseId") String caseId,
            @RequestParam(value = "credentialProfile", required = false) String credentialProfile,
            @RequestParam(value = "runner", required = false) String runner
    ) throws Exception {
        Long ownerId = currentUser.requireUserId();
        permissionEnforcer.requireFeatureJob(currentUser.requireUser(), "EXECUTE");
        ResponseEntity<?> denied = ProjectAccess.denyUnlessOperable(store, projectId, ownerId);
        if (denied != null) {
            return denied;
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

        Path excelPath;
        try {
            excelPath = plainCases.materializeWorkbook(projectId, caseId);
        } catch (PlainCaseDiskStore.NotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiError("NOT_FOUND", e.getMessage()).asMap());
        } catch (PlainCaseDiskStore.BadPathException | PlainCaseDiskStore.BadRequestException e) {
            return ResponseEntity.badRequest()
                    .body(new ApiError("BAD_REQUEST", e.getMessage()).asMap());
        }

        String username = "";
        String password = "";
        if (!credentials.list(projectId).isEmpty()) {
            if (credentialProfile == null || credentialProfile.isBlank()) {
                Files.deleteIfExists(excelPath);
                return ResponseEntity.badRequest()
                        .body(new ApiError("CREDENTIAL_PROFILE_REQUIRED", "credentialProfile is required").asMap());
            }
            ProjectCredentialService.ResolvedCredential resolved =
                    credentials.resolveForJob(projectId, credentialProfile.trim());
            username = resolved.username();
            password = resolved.passwordPlain();
        }

        try {
            List<ManualTestCase> cases = new ExcelTcReader(true).read(excelPath);
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
                        .body(QualityGateResponses.fromErrors(gateErrors));
            }
        } catch (InvalidExcelTemplateException e) {
            Files.deleteIfExists(excelPath);
            throw e;
        }

        String jobId = "exec_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        AuthoringEngine engine = store.authoringEngineForProject(projectId);
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
        job.setAuthoringEngine(engine);
        Path requestPath = AuthoringJobRequestFiles.requestPath(
                store.projectDiskRoot(projectId),
                delivery.portal.engine.InProcessJobContext.wire(JobRecord.JobKind.EXECUTE),
                jobId);
        AuthoringJobRequestFiles.write(requestPath, engine);
        if (runner != null && "private".equalsIgnoreCase(runner.trim())) {
            job.setRequirePrivateRunner(true);
        }
        store.saveJob(job);
        if (!job.isRequirePrivateRunner()) {
            worker.submit(jobId);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("jobId", jobId);
        body.put("status", JobRecord.Status.QUEUED.name());
        body.put("plainCaseId", caseId);
        body.put("source", "plain-case");
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(body);
    }

    static Map<String, Object> toJson(Map<String, Object> row) {
        Map<String, Object> m = new LinkedHashMap<>();
        String id = String.valueOf(row.getOrDefault("id", ""));
        m.put("id", id);
        m.put("caseId", id); // FE probes caseId | id | plainCaseId
        m.put("plainCaseId", id);
        m.put("title", row.getOrDefault("title", ""));
        m.put("lines", row.getOrDefault("lines", List.of()));
        m.put("createdAt", row.getOrDefault("createdAt", ""));
        m.put("updatedAt", row.getOrDefault("updatedAt", ""));
        return m;
    }

    private static ResponseEntity<?> internalError(Exception e) {
        String msg = e.getMessage() == null ? "Internal error" : e.getMessage();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError("INTERNAL_ERROR", msg).asMap());
    }

}
