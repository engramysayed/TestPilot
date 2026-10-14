package delivery.portal.api;

import delivery.portal.model.JobRecord;
import delivery.portal.persistence.PortalUser;
import delivery.portal.roles.PermissionEnforcer;
import delivery.portal.security.CurrentUserService;
import delivery.portal.service.PortalStore;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Account-scoped feature job start (global pages). Optional projectId pin.
 * Path: POST /api/account/jobs
 */
@RestController
@RequestMapping("/api/account")
public class AccountJobsController {
    private final CurrentUserService currentUser;
    private final PermissionEnforcer enforcer;
    private final PortalStore store;

    public AccountJobsController(CurrentUserService currentUser, PermissionEnforcer enforcer,
                                 PortalStore store) {
        this.currentUser = currentUser;
        this.enforcer = enforcer;
        this.store = store;
    }

    public record AccountJobRequest(String kind, String projectId, Map<String, Object> payload) {
    }

    @PostMapping("/jobs")
    public ResponseEntity<?> start(@RequestBody AccountJobRequest body) {
        PortalUser user = currentUser.requireUser();
        if (body == null || body.kind() == null || body.kind().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(new ApiError("BAD_REQUEST", "kind is required").asMap());
        }
        String kind = body.kind().trim().toUpperCase(Locale.ROOT);
        enforcer.requireFeatureJob(user, kind);

        String projectId = body.projectId() == null || body.projectId().isBlank()
                ? null
                : body.projectId().trim();
        if (projectId != null) {
            ResponseEntity<?> denied = ProjectAccess.denyUnlessOperable(store, projectId, user.getId());
            if (denied != null) {
                return denied;
            }
        }

        JobRecord.JobKind jobKind = mapKind(kind);
        String jobId = "acct_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        JobRecord job = new JobRecord(
                jobId,
                projectId,
                user.getId(),
                "NEW",
                java.nio.file.Path.of("."),
                "",
                "",
                "",
                false,
                jobKind
        );
        job.setAccountScoped(true);
        job.setMessage("Account-scoped " + kind + " accepted");
        store.saveJob(job);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("jobId", jobId);
        out.put("kind", kind);
        out.put("projectId", projectId);
        out.put("accountScoped", true);
        out.put("status", job.getStatus().name());
        return ResponseEntity.status(HttpStatus.CREATED).body(out);
    }

    private static JobRecord.JobKind mapKind(String kind) {
        return switch (kind) {
            case "GENERATE", "GENERATE_BATCH" -> JobRecord.JobKind.GENERATE_BATCH;
            case "EXECUTE" -> JobRecord.JobKind.EXECUTE;
            case "AUTOMATE", "CONVERT" -> JobRecord.JobKind.CONVERT;
            case "HUNT" -> JobRecord.JobKind.HUNT;
            default -> throw new IllegalArgumentException("Unsupported kind: " + kind);
        };
    }
}
