package delivery.portal.api;

import delivery.mobile.MobileProperties;
import delivery.mobile.auth.MobileCaseRequirementsView;
import delivery.portal.security.CurrentUserService;
import delivery.portal.service.MobileCaseRequirementsService;
import delivery.portal.service.PortalStore;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Portal REST for mobile case requirements (M3-06 Final).
 * Paths: GET/PUT .../mobile/cases/{tcId}/requirements; optional list GET .../mobile/cases/requirements.
 * Does not invent a second workbook callBefore store — mobile requirements JSON is separate from
 * {@code ManualTestCase.callBefore} (excel/web).
 */
@RestController
@RequestMapping("/api/projects")
public class MobileCaseRequirementsController {
    private final MobileProperties mobileProperties;
    private final PortalStore store;
    private final CurrentUserService currentUser;
    private final MobileCaseRequirementsService requirements;

    public MobileCaseRequirementsController(MobileProperties mobileProperties, PortalStore store,
                                            CurrentUserService currentUser,
                                            MobileCaseRequirementsService requirements) {
        this.mobileProperties = mobileProperties;
        this.store = store;
        this.currentUser = currentUser;
        this.requirements = requirements;
    }

    @GetMapping("/{projectId}/mobile/cases/requirements")
    public ResponseEntity<?> list(@PathVariable String projectId) {
        ResponseEntity<?> gate = gate(projectId, false);
        if (gate != null) {
            return gate;
        }
        List<MobileCaseRequirementsView> cases =
                requirements.list(projectId, currentUser.requireUserId());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("projectId", projectId);
        out.put("cases", cases);
        out.put("canEdit", store.canOperate(projectId, currentUser.requireUserId()));
        return ResponseEntity.ok(out);
    }

    @GetMapping("/{projectId}/mobile/cases/{tcId}/requirements")
    public ResponseEntity<?> get(@PathVariable String projectId, @PathVariable String tcId) {
        ResponseEntity<?> gate = gate(projectId, false);
        if (gate != null) {
            return gate;
        }
        try {
            return ResponseEntity.ok(requirements.get(projectId, tcId, currentUser.requireUserId()));
        } catch (ResponseStatusException e) {
            return statusError(e);
        }
    }

    @PutMapping(value = "/{projectId}/mobile/cases/{tcId}/requirements",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> put(@PathVariable String projectId,
                                 @PathVariable String tcId,
                                 @RequestBody(required = false) Map<String, Object> body) {
        ResponseEntity<?> gate = gate(projectId, true);
        if (gate != null) {
            return gate;
        }
        try {
            MobileCaseRequirementsView view =
                    requirements.put(projectId, tcId, currentUser.requireUserId(), body);
            return ResponseEntity.ok(view);
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
                : code == 403 ? "ROLE_REQUIRED"
                : "BAD_REQUEST";
        String reason = e.getReason() == null ? "error" : e.getReason();
        if (reason != null && reason.contains("negative login")) {
            err = "NEGATIVE_LOGIN_SETUP";
        }
        return ResponseEntity.status(e.getStatusCode())
                .body(new ApiError(err, reason).asMap());
    }
}
