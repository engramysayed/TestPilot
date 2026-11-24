package delivery.portal.api;

import delivery.mobile.MobilePortalStore;
import delivery.mobile.MobileProperties;
import delivery.mobile.model.MobileAppRevisionRecord;
import delivery.mobile.run.MobileDeviceRegistration;
import delivery.mobile.run.MobileRunOrchestrator;
import delivery.portal.model.ProjectRecord;
import delivery.portal.security.CurrentUserService;
import delivery.portal.service.PortalStore;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Mobile Execute for users: discover devices, start / watch / cancel a run. */
@RestController
@RequestMapping("/api/projects/{projectId}/mobile")
public class MobileRunController {

    private final MobileProperties mobileProps;
    private final PortalStore store;
    private final CurrentUserService currentUser;
    private final MobileRunOrchestrator runs;
    private final MobileDeviceRegistration registration;
    private final MobilePortalStore mobileStore;
    private final delivery.mobile.automate.MobileAutomateConversion walkPlans;

    public MobileRunController(MobileProperties mobileProps, PortalStore store, CurrentUserService currentUser,
                               MobileRunOrchestrator runs, MobileDeviceRegistration registration,
                               MobilePortalStore mobileStore,
                               delivery.mobile.automate.MobileAutomateConversion walkPlans) {
        this.mobileProps = mobileProps;
        this.store = store;
        this.currentUser = currentUser;
        this.runs = runs;
        this.registration = registration;
        this.mobileStore = mobileStore;
        this.walkPlans = walkPlans;
    }

    public record StartBody(String deviceId, List<String> tcIds, String appRevisionId) {
        public StartBody(String deviceId, List<String> tcIds) {
            this(deviceId, tcIds, null);
        }
    }

    @PostMapping("/devices/discover")
    public ResponseEntity<?> discover(@PathVariable String projectId) {
        ResponseEntity<?> gate = gate(projectId, true);
        if (gate != null) return gate;
        ProjectRecord project = store.getOwnedProject(projectId, currentUser.requireUserId()).orElseThrow();
        try {
            return ResponseEntity.ok(Map.of("devices", registration.discover(project.getTenantId())));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new ApiError("ADB_UNAVAILABLE", "Could not list devices: " + e.getMessage()).asMap());
        }
    }

    @GetMapping("/walk-plan")
    public ResponseEntity<?> walkPlan(@PathVariable String projectId) {
        ResponseEntity<?> gate = gate(projectId, false);
        if (gate != null) return gate;
        ProjectRecord project = store.getOwnedProject(projectId, currentUser.requireUserId()).orElseThrow();
        if (!"ANDROID".equalsIgnoreCase(project.getPlatform())) {
            return ResponseEntity.ok(Map.of(
                    "phoneState", "NONE", "blocked", false, "phoneName", "",
                    "appLabel", "", "message", "This project is not an Android app."));
        }
        return ResponseEntity.ok(walkPlans.plan(project.getTenantId(), projectId));
    }

    @PostMapping(value = "/runs", consumes = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> start(@PathVariable String projectId, @RequestBody StartBody body) {
        ResponseEntity<?> gate = gate(projectId, true);
        if (gate != null) return gate;
        if (body == null || body.deviceId() == null || body.deviceId().isBlank()) {
            return ResponseEntity.badRequest().body(new ApiError("DEVICE_REQUIRED", "Pick a device").asMap());
        }
        ProjectRecord project = store.getOwnedProject(projectId, currentUser.requireUserId()).orElseThrow();
        try {
            var run = runs.start(project.getTenantId(), projectId, currentUser.requireUserId(),
                    body.deviceId(), body.tcIds(), body.appRevisionId());
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(run.view().toMap());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ApiError("BAD_REQUEST", e.getMessage()).asMap());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError("CONFLICT", e.getMessage()).asMap());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiError("ERROR", e.getMessage()).asMap());
        }
    }

    /** Execute the uploaded workbook only — cases that are not in the file are not run. */
    @PostMapping(value = "/runs", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> startUpload(@PathVariable String projectId,
                                         @org.springframework.web.bind.annotation.RequestParam("deviceId") String deviceId,
                                         @org.springframework.web.bind.annotation.RequestParam("excel")
                                         org.springframework.web.multipart.MultipartFile excel) {
        ResponseEntity<?> gate = gate(projectId, true);
        if (gate != null) return gate;
        if (deviceId == null || deviceId.isBlank()) {
            return ResponseEntity.badRequest().body(new ApiError("DEVICE_REQUIRED", "Pick a device").asMap());
        }
        if (excel == null || excel.isEmpty()) {
            return ResponseEntity.badRequest().body(new ApiError("BAD_REQUEST", "Upload a workbook").asMap());
        }
        ProjectRecord project = store.getOwnedProject(projectId, currentUser.requireUserId()).orElseThrow();
        java.nio.file.Path tmp = null;
        try {
            tmp = java.nio.file.Files.createTempFile("keel-mobile-", ".xlsx");
            try (var in = excel.getInputStream()) {
                java.nio.file.Files.copy(in, tmp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            var cases = new delivery.excel.ExcelTcReader(true).read(tmp);
            var run = runs.startProvided(project.getTenantId(), projectId, currentUser.requireUserId(),
                    deviceId, cases, null);
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(run.view().toMap());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ApiError("BAD_REQUEST", e.getMessage()).asMap());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError("CONFLICT", e.getMessage()).asMap());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiError("ERROR", e.getMessage()).asMap());
        } finally {
            if (tmp != null) {
                try { java.nio.file.Files.deleteIfExists(tmp); } catch (Exception ignored) { }
            }
        }
    }

    @GetMapping("/runs/{runId}")
    public ResponseEntity<?> get(@PathVariable String projectId, @PathVariable String runId) {
        ResponseEntity<?> gate = gate(projectId, false);
        if (gate != null) return gate;
        return runs.view(runId)
                .filter(v -> projectId.equals(v.optString("projectId")))
                .<ResponseEntity<?>>map(v -> ResponseEntity.ok(v.toMap()))
                .orElse(ResponseEntity.status(404).body(new ApiError("NOT_FOUND", "Run not found").asMap()));
    }

    @GetMapping("/apps/revisions")
    @Transactional(readOnly = true)
    public ResponseEntity<?> listRevisions(@PathVariable String projectId) {
        ResponseEntity<?> gate = gate(projectId, false);
        if (gate != null) return gate;
        ProjectRecord project = store.getOwnedProject(projectId, currentUser.requireUserId()).orElseThrow();
        // readOnly=true wraps both calls in one snapshot so the list and the "active" pointer can't
        // disagree when an APK upload commits between them (the client would otherwise see an
        // activeRevisionId that isn't in its rows).
        List<MobileAppRevisionRecord> all = mobileStore.listAppRevisions(project.getTenantId(), projectId);
        String activeId = mobileStore.findActiveAppRevision(project.getTenantId(), projectId)
                .map(MobileAppRevisionRecord::getAppRevisionId)
                .orElse("");
        List<Map<String, Object>> rows = new ArrayList<>();
        for (MobileAppRevisionRecord rev : all) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("appRevisionId", rev.getAppRevisionId());
            row.put("packageName", rev.getPackageName());
            row.put("versionName", rev.getVersionName());
            row.put("versionCode", rev.getVersionCode());
            row.put("verification", rev.getVerification());
            row.put("active", rev.getAppRevisionId() != null && rev.getAppRevisionId().equals(activeId));
            rows.add(row);
        }
        return ResponseEntity.ok(Map.of("revisions", rows, "activeRevisionId", activeId));
    }

    @PostMapping("/runs/{runId}/cancel")
    public ResponseEntity<?> cancel(@PathVariable String projectId, @PathVariable String runId) {
        ResponseEntity<?> gate = gate(projectId, true);
        if (gate != null) return gate;
        runs.cancel(runId);
        return runs.view(runId).<ResponseEntity<?>>map(v -> ResponseEntity.ok(v.toMap()))
                .orElse(ResponseEntity.status(404).body(new ApiError("NOT_FOUND", "Run not found").asMap()));
    }

    private ResponseEntity<?> gate(String projectId, boolean mutate) {
        if (!mobileProps.isEnabled()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiError("NOT_FOUND", "Mobile is disabled").asMap());
        }
        Long uid = currentUser.requireUserId();
        return mutate ? ProjectAccess.denyUnlessOperable(store, projectId, uid)
                : ProjectAccess.denyUnlessReadable(store, projectId, uid);
    }
}
