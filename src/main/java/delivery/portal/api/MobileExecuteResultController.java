package delivery.portal.api;

import delivery.mobile.MobileProperties;
import delivery.mobile.result.MobileCaseResultView;
import delivery.mobile.result.MobileJobResultView;
import delivery.portal.security.CurrentUserService;
import delivery.portal.service.MobileExecuteResultService;
import delivery.portal.service.PortalStore;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * FE bind APIs for mobile Execute result views (M2-07).
 * Paths locked: GET .../mobile-result and .../mobile-result/cases/{caseId}.
 * Gated by {@code delivery.mobile.enabled} (default false → 404).
 */
@RestController
@RequestMapping("/api/projects")
public class MobileExecuteResultController {
    private final MobileProperties mobileProperties;
    private final PortalStore store;
    private final CurrentUserService currentUser;
    private final MobileExecuteResultService results;

    public MobileExecuteResultController(MobileProperties mobileProperties, PortalStore store,
                                         CurrentUserService currentUser, MobileExecuteResultService results) {
        this.mobileProperties = mobileProperties;
        this.store = store;
        this.currentUser = currentUser;
        this.results = results;
    }

    @GetMapping("/{projectId}/jobs/{jobId}/mobile-result")
    public ResponseEntity<?> getJobResult(@PathVariable String projectId, @PathVariable String jobId) {
        ResponseEntity<?> gate = gate(projectId);
        if (gate != null) {
            return gate;
        }
        try {
            MobileJobResultView view = results.requireJobView(projectId, jobId, currentUser.requireUserId());
            return ResponseEntity.ok(view);
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode())
                    .body(new ApiError(e.getStatusCode().value() == 404 ? "NOT_FOUND" : "ERROR",
                            e.getReason() == null ? "error" : e.getReason()).asMap());
        }
    }

    @GetMapping("/{projectId}/jobs/{jobId}/mobile-result/cases/{caseId}")
    public ResponseEntity<?> getCaseResult(@PathVariable String projectId,
                                           @PathVariable String jobId,
                                           @PathVariable String caseId) {
        ResponseEntity<?> gate = gate(projectId);
        if (gate != null) {
            return gate;
        }
        try {
            MobileCaseResultView view =
                    results.requireCaseView(projectId, jobId, caseId, currentUser.requireUserId());
            return ResponseEntity.ok(view);
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode())
                    .body(new ApiError(e.getStatusCode().value() == 404 ? "NOT_FOUND" : "ERROR",
                            e.getReason() == null ? "error" : e.getReason()).asMap());
        }
    }

    private ResponseEntity<?> gate(String projectId) {
        if (!mobileProperties.isEnabled()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiError("NOT_FOUND", "Mobile is disabled").asMap());
        }
        return ProjectAccess.denyUnlessReadable(store, projectId, currentUser.requireUserId());
    }
}
