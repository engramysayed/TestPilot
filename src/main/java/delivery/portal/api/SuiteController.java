package delivery.portal.api;

import delivery.portal.roles.PermissionEnforcer;
import delivery.portal.security.CurrentUserService;
import delivery.portal.service.PortalStore;
import delivery.portal.service.ProjectCredentialService;
import delivery.portal.service.SuiteDiskStore;
import delivery.portal.service.SuiteService;
import delivery.portal.service.SuiteRunService;
import delivery.portal.service.SuiteTestCaseLibraryService;
import delivery.portal.service.credits.CreditService;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase 3 suites / schedule / alerts / monitor / suite-run APIs.
 */
@RestController
@RequestMapping("/api/projects/{projectId}")
public class SuiteController {
    private final PortalStore store;
    private final CurrentUserService currentUser;
    private final SuiteService suites;
    private final ProjectCredentialService credentials;
    private final PermissionEnforcer permissionEnforcer;
    private final CreditService credits;
    private final SuiteTestCaseLibraryService testCaseLibrary;
    private final SuiteRunService suiteRuns;

    public SuiteController(
            PortalStore store,
            CurrentUserService currentUser,
            SuiteService suites,
            ProjectCredentialService credentials,
            PermissionEnforcer permissionEnforcer,
            CreditService credits,
            SuiteTestCaseLibraryService testCaseLibrary,
            SuiteRunService suiteRuns
    ) {
        this.store = store;
        this.currentUser = currentUser;
        this.suites = suites;
        this.credentials = credentials;
        this.permissionEnforcer = permissionEnforcer;
        this.credits = credits;
        this.testCaseLibrary = testCaseLibrary;
        this.suiteRuns = suiteRuns;
    }

    public record CreateSuiteRequest(String name, List<String> tags, List<String> tcIds) {
    }

    public record UpdateSuiteRequest(String name, List<String> tags, List<String> tcIds, String structuredPlanId) {
    }

    /**
     * @param timezone          IANA zone for cron, e.g. "Africa/Cairo" (blank = delivery.suites.schedule-zone)
     * @param credentialProfile login profile scheduled runs use (blank = keep / first profile by name)
     */
    public record ScheduleRequest(Boolean enabled, String kind, String cron, Integer intervalMinutes,
                                  String timezone, String credentialProfile) {
        public ScheduleRequest(Boolean enabled, String kind, String cron, Integer intervalMinutes) {
            this(enabled, kind, cron, intervalMinutes, null, null);
        }
    }

    public record AlertBindRequest(String alertChannelId, String alertMinSeverity) {
    }

    public record PromoteRequest(String target, String fromJobId) {
    }

    public record SuiteRunRequest(String trigger, String credentialProfile, String runner) {
    }

    @GetMapping("/test-case-library")
    public ResponseEntity<?> listTestCaseLibrary(
            @PathVariable("projectId") String projectId,
            @RequestParam(value = "q", required = false) String query,
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "limit", required = false) Integer limit
    ) {
        ResponseEntity<?> denied = ProjectAccess.denyUnlessReadable(store, projectId, currentUser.requireUserId());
        if (denied != null) {
            return denied;
        }
        try {
            return ResponseEntity.ok(testCaseLibrary.list(projectId, query, cursor, limit));
        } catch (Exception e) {
            return internalError(e);
        }
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
                    body == null ? null : body.tcIds(),
                    body == null ? null : body.structuredPlanId()));
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
                    body == null ? null : body.intervalMinutes(),
                    body == null ? null : body.timezone(),
                    body == null ? null : body.credentialProfile());
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
        String trigger = body == null || body.trigger() == null ? null : body.trigger();
        String credentialProfile = body == null ? null : body.credentialProfile();
        String runner = body == null ? null : body.runner();
        return suiteRuns.startRun(projectId, suiteId, ownerId, trigger, credentialProfile, runner);
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
