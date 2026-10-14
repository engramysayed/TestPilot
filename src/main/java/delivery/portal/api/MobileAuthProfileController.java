package delivery.portal.api;

import delivery.mobile.MobileProperties;
import delivery.mobile.auth.AuthProfileRevision;
import delivery.portal.security.CurrentUserService;
import delivery.portal.service.MobileAuthProfileService;
import delivery.portal.service.PortalStore;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Portal REST for mobile auth profiles (M3-06 Final).
 * Paths locked to FE Assumed pack 08. Flag {@code delivery.mobile.enabled} → 404 when off.
 * OWNER/ADMIN mutate; MEMBER read-only. No raw secrets in request or response.
 */
@RestController
@RequestMapping("/api/projects")
public class MobileAuthProfileController {
    private final MobileProperties mobileProperties;
    private final PortalStore store;
    private final CurrentUserService currentUser;
    private final MobileAuthProfileService profiles;

    public MobileAuthProfileController(MobileProperties mobileProperties, PortalStore store,
                                       CurrentUserService currentUser, MobileAuthProfileService profiles) {
        this.mobileProperties = mobileProperties;
        this.store = store;
        this.currentUser = currentUser;
        this.profiles = profiles;
    }

    @GetMapping("/{projectId}/mobile/auth-profiles")
    public ResponseEntity<?> list(@PathVariable String projectId) {
        ResponseEntity<?> gate = gate(projectId, false);
        if (gate != null) {
            return gate;
        }
        Long uid = currentUser.requireUserId();
        List<Map<String, Object>> rows = profiles.listSummaries(projectId, uid);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("projectId", projectId);
        out.put("profiles", rows);
        out.put("canEdit", store.canOperate(projectId, uid));
        return ResponseEntity.ok(out);
    }

    @PostMapping(value = "/{projectId}/mobile/auth-profiles", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> create(@PathVariable String projectId,
                                    @RequestBody(required = false) Map<String, Object> body) {
        ResponseEntity<?> gate = gate(projectId, true);
        if (gate != null) {
            return gate;
        }
        try {
            AuthProfileRevision rev = profiles.create(projectId, currentUser.requireUserId(), body);
            return ResponseEntity.status(HttpStatus.CREATED).body(rev);
        } catch (ResponseStatusException e) {
            return statusError(e);
        }
    }

    @GetMapping("/{projectId}/mobile/auth-profiles/{profileId}")
    public ResponseEntity<?> get(@PathVariable String projectId, @PathVariable String profileId) {
        ResponseEntity<?> gate = gate(projectId, false);
        if (gate != null) {
            return gate;
        }
        try {
            Long uid = currentUser.requireUserId();
            return ResponseEntity.ok(profiles.getProfile(projectId, profileId, uid, store.canOperate(projectId, uid)));
        } catch (ResponseStatusException e) {
            return statusError(e);
        }
    }

    @GetMapping("/{projectId}/mobile/auth-profiles/{profileId}/revisions/{revisionId}")
    public ResponseEntity<?> getRevision(@PathVariable String projectId,
                                         @PathVariable String profileId,
                                         @PathVariable String revisionId) {
        ResponseEntity<?> gate = gate(projectId, false);
        if (gate != null) {
            return gate;
        }
        try {
            return ResponseEntity.ok(
                    profiles.getRevision(projectId, profileId, revisionId, currentUser.requireUserId()));
        } catch (ResponseStatusException e) {
            return statusError(e);
        }
    }

    @PostMapping(value = "/{projectId}/mobile/auth-profiles/{profileId}/revisions",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> publishRevision(@PathVariable String projectId,
                                             @PathVariable String profileId,
                                             @RequestBody(required = false) Map<String, Object> body) {
        ResponseEntity<?> gate = gate(projectId, true);
        if (gate != null) {
            return gate;
        }
        try {
            AuthProfileRevision rev =
                    profiles.publishEdit(projectId, profileId, currentUser.requireUserId(), body);
            return ResponseEntity.status(HttpStatus.CREATED).body(rev);
        } catch (ResponseStatusException e) {
            return statusError(e);
        }
    }

    private ResponseEntity<?> gate(String projectId, boolean mutate) {
        if (!mobileProperties.isEnabled()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiError("NOT_FOUND", "Mobile is disabled").asMap());
        }
        Long uid = currentUser.requireUserId();
        if (mutate) {
            return ProjectAccess.denyUnlessOperable(store, projectId, uid);
        }
        return ProjectAccess.denyUnlessReadable(store, projectId, uid);
    }

    private static ResponseEntity<?> statusError(ResponseStatusException e) {
        int code = e.getStatusCode().value();
        String err = code == 404 ? "NOT_FOUND"
                : code == 409 ? "REVISION_CONFLICT"
                : code == 403 ? "ROLE_REQUIRED"
                : "BAD_REQUEST";
        if (code == 409) {
            err = "REVISION_CONFLICT";
        }
        return ResponseEntity.status(e.getStatusCode())
                .body(new ApiError(err, e.getReason() == null ? "error" : e.getReason()).asMap());
    }
}
