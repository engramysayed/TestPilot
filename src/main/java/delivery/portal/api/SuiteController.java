package delivery.portal.api;

import delivery.authoring.AuthoringEngine;
import delivery.excel.ExcelTcReader;
import delivery.excel.GenerateQualityGate;
import delivery.excel.InvalidExcelTemplateException;
import delivery.excel.KeelPathCaseFilter;
import delivery.excel.KeelPathCounts;
import delivery.excel.KeelPathSurfaceGuard;
import delivery.excel.ManualTestCase;
import delivery.excel.TcImportRepair;
import delivery.job.AuthoringJobRequestFiles;
import delivery.portal.model.JobRecord;
import delivery.portal.model.ProjectRecord;
import delivery.portal.roles.PermissionEnforcer;
import delivery.portal.security.CurrentUserService;
import delivery.portal.service.GeneratedWorkbookService;
import delivery.portal.service.JobWorkbookResolver;
import delivery.portal.service.PortalStore;
import delivery.portal.service.ProjectCredentialService;
import delivery.portal.service.SuiteDiskStore;
import delivery.portal.service.SuiteService;
import delivery.portal.service.credits.CreditService;
import delivery.portal.worker.ExecuteWorker;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Phase 3 suites / schedule / alerts / monitor / suite-run APIs.
 */
@RestController
@RequestMapping("/api/projects/{projectId}")
public class SuiteController {
    private final PortalStore store;
    private final CurrentUserService currentUser;
    private final SuiteService suites;
    private final GeneratedWorkbookService workbooks;
    private final ProjectCredentialService credentials;
    private final PermissionEnforcer permissionEnforcer;
    private final ExecuteWorker worker;
    private final CreditService credits;

    public SuiteController(
            PortalStore store,
            CurrentUserService currentUser,
            SuiteService suites,
            GeneratedWorkbookService workbooks,
            ProjectCredentialService credentials,
            PermissionEnforcer permissionEnforcer,
            ExecuteWorker worker,
            CreditService credits
    ) {
        this.store = store;
        this.currentUser = currentUser;
        this.suites = suites;
        this.workbooks = workbooks;
        this.credentials = credentials;
        this.permissionEnforcer = permissionEnforcer;
        this.worker = worker;
        this.credits = credits;
    }

    public record CreateSuiteRequest(String name, List<String> tags, List<String> tcIds) {
    }

    public record UpdateSuiteRequest(String name, List<String> tags, List<String> tcIds) {
    }

    public record ScheduleRequest(Boolean enabled, String kind, String cron, Integer intervalMinutes) {
    }

    public record AlertBindRequest(String alertChannelId, String alertMinSeverity) {
    }

    public record PromoteRequest(String target, String fromJobId) {
    }

    public record SuiteRunRequest(String trigger, String credentialProfile, String runner) {
    }

    @GetMapping("/suites")
    public ResponseEntity<?> list(
            @PathVariable("projectId") String projectId,
            @RequestParam(value = "tag", required = false) String tag
    ) {
        ResponseEntity<?> denied = ProjectAccess.denyUnlessReadable(store, projectId, currentUser.requireUserId());
        if (denied != null) {
            return denied;
        }
        try {
            return ResponseEntity.ok(suites.list(projectId, tag));
        } catch (Exception e) {
            return internalError(e);
        }
    }

    @GetMapping("/suites/{suiteId}")
    public ResponseEntity<?> get(
            @PathVariable("projectId") String projectId,
            @PathVariable("suiteId") String suiteId
    ) {
        ResponseEntity<?> denied = ProjectAccess.denyUnlessReadable(store, projectId, currentUser.requireUserId());
        if (denied != null) {
            return denied;
        }
        try {
            return ResponseEntity.ok(suites.get(projectId, suiteId));
        } catch (SuiteDiskStore.NotFoundException | SuiteDiskStore.BadPathException e) {
            return notFoundOrBad(e);
        } catch (Exception e) {
            return internalError(e);
        }
    }

    @PostMapping(value = "/suites", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> create(
            @PathVariable("projectId") String projectId,
            @RequestBody(required = false) CreateSuiteRequest body
    ) {
        ResponseEntity<?> denied = ProjectAccess.denyUnlessOperable(store, projectId, currentUser.requireUserId());
        if (denied != null) {
            return denied;
        }
        try {
            Map<String, Object> created = suites.create(
                    projectId,
                    body == null ? null : body.name(),
                    body == null ? null : body.tags(),
                    body == null ? null : body.tcIds());
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (SuiteDiskStore.BadRequestException | SuiteDiskStore.BadPathException e) {
            return bad(e);
        } catch (Exception e) {
            return internalError(e);
        }
    }

    @PutMapping(value = "/suites/{suiteId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> update(
            @PathVariable("projectId") String projectId,
            @PathVariable("suiteId") String suiteId,
            @RequestBody(required = false) UpdateSuiteRequest body
    ) {
        ResponseEntity<?> denied = ProjectAccess.denyUnlessOperable(store, projectId, currentUser.requireUserId());
        if (denied != null) {
            return denied;
        }
        try {
            return ResponseEntity.ok(suites.update(
                    projectId,
                    suiteId,
                    body == null ? null : body.name(),
                    body == null ? null : body.tags(),
                    body == null ? null : body.tcIds()));
        } catch (SuiteDiskStore.NotFoundException e) {
            return notFound(e);
        } catch (SuiteDiskStore.BadRequestException | SuiteDiskStore.BadPathException e) {
            return bad(e);
        } catch (Exception e) {
            return internalError(e);
        }
    }

    @DeleteMapping("/suites/{suiteId}")
    public ResponseEntity<?> delete(
            @PathVariable("projectId") String projectId,
            @PathVariable("suiteId") String suiteId
    ) {
        ResponseEntity<?> denied = ProjectAccess.denyUnlessOperable(store, projectId, currentUser.requireUserId());
        if (denied != null) {
            return denied;
        }
        try {
            suites.delete(projectId, suiteId);
            return ResponseEntity.ok(Map.of("deleted", true, "suiteId", suiteId));
        } catch (SuiteDiskStore.ConflictException e) {
            return conflict(e);
        } catch (SuiteDiskStore.NotFoundException e) {
            return notFound(e);
        } catch (SuiteDiskStore.BadPathException e) {
            return bad(e);
        } catch (Exception e) {
            return internalError(e);
        }
    }

    @GetMapping("/suites/{suiteId}/schedule")
    public ResponseEntity<?> getSchedule(
            @PathVariable("projectId") String projectId,
            @PathVariable("suiteId") String suiteId
    ) {
        ResponseEntity<?> denied = ProjectAccess.denyUnlessReadable(store, projectId, currentUser.requireUserId());
        if (denied != null) {
            return denied;
        }
        try {
            Map<String, Object> schedule = suites.getSchedule(projectId, suiteId);
            if (schedule == null) {
                return ResponseEntity.ok(Map.of());
            }
            return ResponseEntity.ok(schedule);
        } catch (SuiteDiskStore.NotFoundException | SuiteDiskStore.BadPathException e) {
            return notFoundOrBad(e);
        } catch (Exception e) {
            return internalError(e);
        }
    }

    @PutMapping(value = "/suites/{suiteId}/schedule", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> putSchedule(
            @PathVariable("projectId") String projectId,
            @PathVariable("suiteId") String suiteId,
            @RequestBody(required = false) ScheduleRequest body
    ) {
        ResponseEntity<?> denied = ProjectAccess.denyUnlessOperable(store, projectId, currentUser.requireUserId());
        if (denied != null) {
            return denied;
        }
        try {
            boolean enabled = body != null && body.enabled() != null && body.enabled();
            String kind = body == null ? null : body.kind();
            Map<String, Object> updated = suites.putSchedule(
                    projectId,
                    suiteId,
                    enabled,
                    kind,
                    body == null ? null : body.cron(),
                    body == null ? null : body.intervalMinutes());
            return ResponseEntity.ok(updated.get("schedule"));
        } catch (SuiteDiskStore.NotFoundException e) {
            return notFound(e);
        } catch (SuiteDiskStore.BadRequestException | SuiteDiskStore.BadPathException e) {
            return bad(e);
        } catch (Exception e) {
            return internalError(e);
        }
    }

    @DeleteMapping("/suites/{suiteId}/schedule")
    public ResponseEntity<?> deleteSchedule(
            @PathVariable("projectId") String projectId,
            @PathVariable("suiteId") String suiteId
    ) {
        ResponseEntity<?> denied = ProjectAccess.denyUnlessOperable(store, projectId, currentUser.requireUserId());
        if (denied != null) {
            return denied;
        }
        try {
            suites.clearSchedule(projectId, suiteId);
            return ResponseEntity.ok(Map.of("cleared", true));
        } catch (SuiteDiskStore.NotFoundException e) {
            return notFound(e);
        } catch (SuiteDiskStore.BadPathException e) {
            return bad(e);
        } catch (Exception e) {
            return internalError(e);
        }
    }

    @PutMapping(value = "/suites/{suiteId}/alerts", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> putAlerts(
            @PathVariable("projectId") String projectId,
            @PathVariable("suiteId") String suiteId,
            @RequestBody(required = false) AlertBindRequest body
    ) {
        ResponseEntity<?> denied = ProjectAccess.denyUnlessOperable(store, projectId, currentUser.requireUserId());
        if (denied != null) {
            return denied;
        }
        try {
            Map<String, Object> updated = suites.bindAlert(
                    projectId,
                    suiteId,
                    body == null ? null : body.alertChannelId(),
                    body == null ? null : body.alertMinSeverity());
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("alertChannelId", updated.get("alertChannelId"));
            out.put("alertMinSeverity", updated.get("alertMinSeverity"));
            return ResponseEntity.ok(out);
        } catch (SuiteDiskStore.NotFoundException e) {
            return notFound(e);
        } catch (SuiteDiskStore.BadRequestException | SuiteDiskStore.BadPathException e) {
            return bad(e);
        } catch (Exception e) {
            return internalError(e);
        }
    }

    @DeleteMapping("/suites/{suiteId}/alerts")
    public ResponseEntity<?> deleteAlerts(
            @PathVariable("projectId") String projectId,
            @PathVariable("suiteId") String suiteId
    ) {
        ResponseEntity<?> denied = ProjectAccess.denyUnlessOperable(store, projectId, currentUser.requireUserId());
        if (denied != null) {
            return denied;
        }
        try {
            suites.clearAlert(projectId, suiteId);
            return ResponseEntity.ok(Map.of("cleared", true));
        } catch (SuiteDiskStore.NotFoundException e) {
            return notFound(e);
        } catch (SuiteDiskStore.BadPathException e) {
            return bad(e);
        } catch (Exception e) {
            return internalError(e);
        }
    }

    @PostMapping(value = "/suites/{suiteId}/promote-monitor", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> promoteMonitor(
            @PathVariable("projectId") String projectId,
            @PathVariable("suiteId") String suiteId,
            @RequestBody(required = false) PromoteRequest body
    ) {
        Long ownerId = currentUser.requireUserId();
        ResponseEntity<?> denied = ProjectAccess.denyUnlessOperable(store, projectId, ownerId);
        if (denied != null) {
            return denied;
        }
        try {
            Map<String, Object> updated = suites.promoteMonitor(
                    projectId,
                    suiteId,
                    body == null ? null : body.target(),
                    body == null ? null : body.fromJobId(),
                    ownerId);
            return ResponseEntity.ok(updated.get("monitor"));
        } catch (SuiteDiskStore.ConflictException e) {
            return conflict(e);
        } catch (SuiteDiskStore.NotFoundException e) {
            return notFound(e);
        } catch (SuiteDiskStore.BadRequestException | SuiteDiskStore.BadPathException e) {
            return bad(e);
        } catch (Exception e) {
            return internalError(e);
        }
    }

    @DeleteMapping("/suites/{suiteId}/monitor")
    public ResponseEntity<?> demoteMonitor(
            @PathVariable("projectId") String projectId,
            @PathVariable("suiteId") String suiteId
    ) {
        ResponseEntity<?> denied = ProjectAccess.denyUnlessOperable(store, projectId, currentUser.requireUserId());
        if (denied != null) {
            return denied;
        }
        try {
            suites.demoteMonitor(projectId, suiteId);
            return ResponseEntity.ok(Map.of("cleared", true));
        } catch (SuiteDiskStore.NotFoundException e) {
            return notFound(e);
        } catch (SuiteDiskStore.BadPathException e) {
            return bad(e);
        } catch (Exception e) {
            return internalError(e);
        }
    }

    @GetMapping("/monitors")
    public ResponseEntity<?> listMonitors(@PathVariable("projectId") String projectId) {
        ResponseEntity<?> denied = ProjectAccess.denyUnlessReadable(store, projectId, currentUser.requireUserId());
        if (denied != null) {
            return denied;
        }
        try {
            return ResponseEntity.ok(suites.listMonitors(projectId));
        } catch (Exception e) {
            return internalError(e);
        }
    }

    /**
     * Start an EXECUTE job for the suite's tcIds via the same JobWorkbookResolver + ExecuteWorker path
     * as {@code POST .../execute-runs} with useGenerated=true.
     */
    @PostMapping(value = "/suites/{suiteId}/runs", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> startRun(
            @PathVariable("projectId") String projectId,
            @PathVariable("suiteId") String suiteId,
            @RequestBody(required = false) SuiteRunRequest body
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

        Map<String, Object> suite;
        try {
            suite = suites.get(projectId, suiteId);
        } catch (SuiteDiskStore.NotFoundException e) {
            return notFound(e);
        } catch (SuiteDiskStore.BadPathException e) {
            return bad(e);
        }

        @SuppressWarnings("unchecked")
        List<String> tcIds = (List<String>) suite.getOrDefault("tcIds", List.of());
        if (tcIds == null || tcIds.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(new ApiError("BAD_REQUEST", "Suite has no tcIds to run").asMap());
        }

        String username = "";
        String password = "";
        String credentialProfile = body == null ? null : body.credentialProfile();
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

        Path excelPath;
        try {
            excelPath = JobWorkbookResolver.resolve(workbooks, projectId, true, tcIds, null);
        } catch (IllegalStateException e) {
            if ("NO_GENERATED_WORKBOOK".equals(e.getMessage())) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(new ApiError("NO_GENERATED_WORKBOOK",
                                "No generated workbook for this project — generate TCs first").asMap());
            }
            throw e;
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(new ApiError("BAD_REQUEST", e.getMessage()).asMap());
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
        workbooks.headRevisionId(projectId).ifPresent(job::setLibraryRevisionId);
        Path requestPath = AuthoringJobRequestFiles.requestPath(
                store.projectDiskRoot(projectId),
                delivery.portal.engine.InProcessJobContext.wire(JobRecord.JobKind.EXECUTE),
                jobId);
        AuthoringJobRequestFiles.write(requestPath, engine);
        String runner = body == null ? null : body.runner();
        if (runner != null && "private".equalsIgnoreCase(runner.trim())) {
            job.setRequirePrivateRunner(true);
        }

        String trigger = body == null || body.trigger() == null || body.trigger().isBlank()
                ? "manual"
                : body.trigger().trim().toLowerCase();
        // SUITES_MONITOR: charge only when this run is a scheduled/monitor fire (not manual).
        if ("schedule".equals(trigger) || "monitor".equals(trigger)) {
            credits.tryDebitForAddon(
                    ownerId, CreditService.ADDON_SUITES_MONITOR, projectId + ":" + suiteId + ":" + jobId);
        }

        store.saveJob(job);
        if (!job.isRequirePrivateRunner()) {
            worker.submit(jobId);
        }

        if ("schedule".equals(trigger) || "monitor".equals(trigger) || suite.get("schedule") != null) {
            try {
                suites.recordFire(projectId, suiteId, jobId);
            } catch (Exception ignored) {
                // run already accepted; schedule stamp is best-effort
            }
        } else {
            // always stamp lastJobId when a schedule exists so AE/claim loop advances
            Object sched = suite.get("schedule");
            if (sched instanceof Map<?, ?> m && Boolean.TRUE.equals(m.get("enabled"))) {
                try {
                    suites.recordFire(projectId, suiteId, jobId);
                } catch (Exception ignored) {
                }
            }
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("jobId", jobId);
        resp.put("status", JobRecord.Status.QUEUED.name());
        resp.put("suiteId", suiteId);
        resp.put("trigger", trigger);
        resp.put("source", "suite");
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(resp);
    }

    private static ResponseEntity<?> bad(Exception e) {
        return ResponseEntity.badRequest()
                .body(new ApiError("BAD_REQUEST", e.getMessage()).asMap());
    }

    private static ResponseEntity<?> notFound(Exception e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError("NOT_FOUND", e.getMessage()).asMap());
    }

    private static ResponseEntity<?> conflict(Exception e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError("CONFLICT", e.getMessage()).asMap());
    }

    private static ResponseEntity<?> notFoundOrBad(Exception e) {
        if (e instanceof SuiteDiskStore.BadPathException) {
            return bad(e);
        }
        return notFound(e);
    }

    private static ResponseEntity<?> internalError(Exception e) {
        String msg = e.getMessage() == null ? "Internal error" : e.getMessage();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError("INTERNAL_ERROR", msg).asMap());
    }
}
