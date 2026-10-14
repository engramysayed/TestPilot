package delivery.portal.api;

import delivery.portal.DeliveryPortalProperties;
import delivery.portal.security.CurrentUserService;
import delivery.portal.service.HuntTriageService;
import delivery.portal.service.PortalStore;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Hunt-bug triage queue (Backend API gaps). AE shapes are source of truth.
 * Flag {@code delivery.hunt.triage.enabled} defaults false. CONFIRMED ≠ promote.
 */
@RestController
@RequestMapping("/api/projects")
public class HuntTriageController {
    private final DeliveryPortalProperties props;
    private final PortalStore store;
    private final CurrentUserService currentUser;
    private final HuntTriageService triage;

    public HuntTriageController(DeliveryPortalProperties props, PortalStore store,
                                CurrentUserService currentUser, HuntTriageService triage) {
        this.props = props;
        this.store = store;
        this.currentUser = currentUser;
        this.triage = triage;
    }

    public record StatusBody(String status, String note) {
    }

    @GetMapping("/{projectId}/hunt/{jobId}/bugs")
    public ResponseEntity<?> listBugs(@PathVariable String projectId, @PathVariable String jobId) {
        ResponseEntity<?> gate = gate(projectId, false);
        if (gate != null) {
            return gate;
        }
        try {
            Long uid = currentUser.requireUserId();
            triage.requireHuntJob(projectId, jobId, uid);
            List<Map<String, Object>> bugs = triage.listBugs(projectId, jobId);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("jobId", jobId);
            out.put("projectId", projectId);
            out.put("bugs", bugs);
            return ResponseEntity.ok(out);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiError("NOT_FOUND", e.getMessage()).asMap());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiError("TRIAGE_ERROR", e.getMessage()).asMap());
        }
    }

    @GetMapping("/{projectId}/hunt/{jobId}/summary")
    public ResponseEntity<?> summary(@PathVariable String projectId, @PathVariable String jobId) {
        ResponseEntity<?> gate = gate(projectId, false);
        if (gate != null) {
            return gate;
        }
        try {
            Long uid = currentUser.requireUserId();
            triage.requireHuntJob(projectId, jobId, uid);
            return ResponseEntity.ok(triage.summary(projectId, jobId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiError("NOT_FOUND", e.getMessage()).asMap());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiError("TRIAGE_ERROR", e.getMessage()).asMap());
        }
    }

    @PatchMapping(value = "/{projectId}/hunt/{jobId}/bugs/{bugId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> patchBug(@PathVariable String projectId, @PathVariable String jobId,
                                      @PathVariable String bugId,
                                      @RequestBody(required = false) StatusBody body) {
        ResponseEntity<?> gate = gate(projectId, true);
        if (gate != null) {
            return gate;
        }
        if (body == null || body.status() == null || body.status().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(new ApiError("BAD_REQUEST", "status is required").asMap());
        }
        try {
            Long uid = currentUser.requireUserId();
            triage.requireHuntJob(projectId, jobId, uid);
            Map<String, Object> updated = triage.patchStatus(projectId, jobId, bugId, body.status(), body.note());
            if (updated == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ApiError("NOT_FOUND", "Unknown bug").asMap());
            }
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            String msg = e.getMessage() == null ? "" : e.getMessage();
            if (msg.startsWith("status must")) {
                return ResponseEntity.badRequest()
                        .body(new ApiError("BAD_REQUEST", msg).asMap());
            }
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiError("NOT_FOUND", msg).asMap());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiError("TRIAGE_ERROR", e.getMessage()).asMap());
        }
    }

    @PostMapping("/{projectId}/hunt/{jobId}/bugs/{bugId}/promote")
    public ResponseEntity<?> promote(@PathVariable String projectId, @PathVariable String jobId,
                                     @PathVariable String bugId) {
        ResponseEntity<?> gate = gate(projectId, true);
        if (gate != null) {
            return gate;
        }
        try {
            Long uid = currentUser.requireUserId();
            triage.requireHuntJob(projectId, jobId, uid);
            String email = currentUser.requireUser().getEmail();
            Map<String, Object> out = triage.promote(projectId, jobId, bugId, email);
            if (out == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ApiError("NOT_FOUND", "Unknown bug").asMap());
            }
            return ResponseEntity.status(HttpStatus.CREATED).body(out);
        } catch (IllegalStateException e) {
            if ("NOT_CONFIRMED".equals(e.getMessage())) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(new ApiError("NOT_CONFIRMED",
                                "Mark the finding confirmed before promoting it to a test case draft").asMap());
            }
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ApiError("CONFLICT", e.getMessage()).asMap());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiError("NOT_FOUND", e.getMessage()).asMap());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiError("TRIAGE_ERROR", e.getMessage()).asMap());
        }
    }

    /** Optional project-wide alias; each row includes jobId. Primary path remains job-scoped. */
    @GetMapping("/{projectId}/hunt-bugs")
    public ResponseEntity<?> listProjectBugs(@PathVariable String projectId,
                                             @RequestParam(value = "status", required = false) String status) {
        ResponseEntity<?> gate = gate(projectId, false);
        if (gate != null) {
            return gate;
        }
        try {
            List<Map<String, Object>> bugs = triage.listProjectBugs(projectId, status);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("projectId", projectId);
            out.put("bugs", bugs);
            return ResponseEntity.ok(out);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiError("TRIAGE_ERROR", e.getMessage()).asMap());
        }
    }

    private ResponseEntity<?> gate(String projectId, boolean mutate) {
        if (!props.isHuntTriageEnabled()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiError("NOT_FOUND", "Hunt triage is disabled").asMap());
        }
        Long uid = currentUser.requireUserId();
        return mutate
                ? ProjectAccess.denyUnlessOperable(store, projectId, uid)
                : ProjectAccess.denyUnlessReadable(store, projectId, uid);
    }
}
