package delivery.portal.api;

import delivery.job.FailureClassifier;
import delivery.portal.model.JobRecord;
import delivery.portal.security.CurrentUserService;
import delivery.portal.service.PortalStore;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/jobs")
public class JobTriageController {
    private final PortalStore store;
    private final CurrentUserService currentUser;

    public JobTriageController(PortalStore store, CurrentUserService currentUser) {
        this.store = store;
        this.currentUser = currentUser;
    }

    public record ClassificationRequest(String kind) {
    }

    @PostMapping("/{jobId}/classification")
    public ResponseEntity<?> classify(
            @PathVariable("jobId") String jobId,
            @RequestBody(required = false) ClassificationRequest body
    ) throws Exception {
        Long uid = currentUser.requireUserId();
        JobRecord job = store.getOwnedJob(jobId, uid).orElse(null);
        if (job == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiError("NOT_FOUND", "Unknown job").asMap());
        }
        ResponseEntity<?> denied = ProjectAccess.denyUnlessOperable(store, job.getProjectId(), uid);
        if (denied != null) {
            return denied;
        }
        if (body == null || body.kind() == null || body.kind().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(new ApiError("BAD_REQUEST", "kind is required").asMap());
        }
        FailureClassifier.Kind kind;
        try {
            kind = FailureClassifier.Kind.valueOf(body.kind().trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(new ApiError("BAD_REQUEST", "kind must be ASSERTION, LOCATOR, PROVIDER, or INFRASTRUCTURE").asMap());
        }
        FailureClassifier.Classification saved = store.saveFailureClassification(job, kind);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("jobId", jobId);
        out.put("failureClassSuggested", saved.suggested().name());
        out.put("failureClass", saved.effective().name());
        out.put("failureClassUserCorrected", saved.userCorrected());
        return ResponseEntity.ok(out);
    }
}
