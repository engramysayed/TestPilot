package delivery.portal.api;

import delivery.portal.model.ProjectRecord;
import delivery.portal.record.BrowserRecordingService;
import delivery.portal.record.RecordedBuildService;
import delivery.portal.record.RecordingLog;
import delivery.portal.roles.PermissionEnforcer;
import delivery.portal.security.CurrentUserService;
import delivery.portal.service.PortalStore;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/projects/{projectId}/recordings")
public class RecordingController {
    private final PortalStore store;
    private final CurrentUserService currentUser;
    private final PermissionEnforcer permissionEnforcer;
    private final BrowserRecordingService recordings;
    private final RecordedBuildService builds;

    public RecordingController(
            PortalStore store,
            CurrentUserService currentUser,
            PermissionEnforcer permissionEnforcer,
            BrowserRecordingService recordings,
            RecordedBuildService builds
    ) {
        this.store = store;
        this.currentUser = currentUser;
        this.permissionEnforcer = permissionEnforcer;
        this.recordings = recordings;
        this.builds = builds;
    }

    public record StartBody(String url, Boolean useProjectSite) {
    }

    public record SaveBody(String name, String expected, Boolean signIn, Boolean build) {
    }

    @PostMapping
    public ResponseEntity<?> start(
            @PathVariable("projectId") String projectId,
            @RequestBody(required = false) StartBody body
    ) {
        Gate gate = gate(projectId);
        if (gate.denied != null) {
            return gate.denied;
        }
        boolean useProjectSite = body == null || body.useProjectSite() == null
                ? body == null || body.url() == null || body.url().isBlank()
                : Boolean.TRUE.equals(body.useProjectSite());
        String requested = body == null || body.url() == null ? "" : body.url();
        BrowserRecordingService.View view = recordings.start(
                ownerKey(), projectId, gate.project.getBaseUrl(), requested, useProjectSite);
        return ResponseEntity.ok(toMap(view));
    }

    @GetMapping("/{sessionId}")
    public ResponseEntity<?> poll(
            @PathVariable("projectId") String projectId,
            @PathVariable("sessionId") String sessionId
    ) {
        Gate gate = gate(projectId);
        if (gate.denied != null) {
            return gate.denied;
        }
        return ResponseEntity.ok(toMap(recordings.poll(ownerKey(), projectId, sessionId)));
    }

    @PostMapping("/{sessionId}/stop")
    public ResponseEntity<?> stop(
            @PathVariable("projectId") String projectId,
            @PathVariable("sessionId") String sessionId
    ) {
        Gate gate = gate(projectId);
        if (gate.denied != null) {
            return gate.denied;
        }
        return ResponseEntity.ok(toMap(recordings.stop(ownerKey(), projectId, sessionId)));
    }

    @PostMapping("/{sessionId}/save")
    public ResponseEntity<?> save(
            @PathVariable("projectId") String projectId,
            @PathVariable("sessionId") String sessionId,
            @RequestBody(required = false) SaveBody body
    ) throws Exception {
        Gate gate = gate(projectId);
        if (gate.denied != null) {
            return gate.denied;
        }
        String name = body == null ? "" : body.name();
        String expected = body == null ? "" : body.expected();
        boolean signIn = body != null && Boolean.TRUE.equals(body.signIn());
        boolean build = body != null && Boolean.TRUE.equals(body.build());
        Map<String, Object> saved = recordings.save(
                ownerKey(), projectId, sessionId, name, expected, signIn, gate.project.getBaseUrl());
        @SuppressWarnings("unchecked")
        List<RecordingLog.Capture> captures = (List<RecordingLog.Capture>) saved.remove("captures");
        if (build) {
            try {
                String jobId = builds.start(projectId, String.valueOf(saved.get("tcId")), captures);
                saved.put("jobId", jobId);
                saved.put("buildMessage", "Building the framework from these locators. Chrome stays closed.");
            } catch (RuntimeException e) {
                saved.put("buildMessage", e.getMessage() == null
                        ? "The test case is saved. The framework was not started."
                        : e.getMessage());
            }
        }
        return ResponseEntity.ok(saved);
    }

    private Gate gate(String projectId) {
        Long userId = currentUser.requireUserId();
        permissionEnforcer.requireFeatureJob(currentUser.requireUser(), "GENERATE");
        ResponseEntity<?> denied = ProjectAccess.denyUnlessOperable(store, projectId, userId);
        if (denied != null) {
            return new Gate(null, denied);
        }
        ProjectRecord project = store.getOwnedProject(projectId, userId).orElseThrow();
        if (project.isArchived()) {
            return new Gate(null, ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ApiError("PROJECT_ARCHIVED",
                            "This project is archived. Restore it before recording.").asMap()));
        }
        if ("ANDROID".equals(project.getPlatform())) {
            return new Gate(null, ResponseEntity.badRequest()
                    .body(new ApiError("BAD_REQUEST",
                            "Recording opens a website in Chrome. This project is set up as a mobile app.").asMap()));
        }
        return new Gate(project, null);
    }

    private String ownerKey() {
        return String.valueOf(currentUser.requireUserId());
    }

    private static Map<String, Object> toMap(BrowserRecordingService.View view) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sessionId", view.sessionId());
        body.put("status", view.status());
        body.put("url", view.url() == null ? "" : view.url());
        body.put("steps", view.steps() == null ? List.of() : view.steps());
        body.put("message", view.message() == null ? "" : view.message());
        body.put("locatedCount", view.locatedCount());
        body.put("checkCount", view.checkCount());
        return body;
    }

    private record Gate(ProjectRecord project, ResponseEntity<?> denied) {
    }
}
