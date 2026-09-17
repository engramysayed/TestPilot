package delivery.portal.api.v1;

import delivery.identity.TenantId;
import delivery.identity.WorkspaceDirectory;
import delivery.identity.WorkspaceRole;
import delivery.job.IdempotencyStore;
import delivery.job.RateLimitPolicy;
import delivery.job.WebhookSigner;
import delivery.portal.api.ApiError;
import delivery.portal.model.JobRecord;
import delivery.portal.model.ProjectRecord;
import delivery.portal.security.ServiceIdentityFilter;
import delivery.portal.service.GeneratedWorkbookService;
import delivery.portal.service.PortalStore;
import delivery.portal.service.ProjectCredentialService;
import delivery.portal.worker.ExecuteWorker;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class PublicJobApiController {
    public static final String IDEMPOTENCY_HEADER = "Idempotency-Key";
    public static final String RATE_LIMIT_NOTE = "60 requests per tenant per minute";

    private final PortalStore store;
    private final GeneratedWorkbookService workbooks;
    private final ProjectCredentialService credentials;
    private final ExecuteWorker executeWorker;
    private final RateLimitPolicy rateLimits = new RateLimitPolicy();

    public PublicJobApiController(
            PortalStore store,
            GeneratedWorkbookService workbooks,
            ProjectCredentialService credentials,
            ExecuteWorker executeWorker
    ) {
        this.store = store;
        this.workbooks = workbooks;
        this.credentials = credentials;
        this.executeWorker = executeWorker;
    }

    public record SubmitRequest(String kind, String credentialProfile, String environmentRevisionId) {
    }

    @GetMapping("/whoami")
    public ResponseEntity<?> whoami() {
        WorkspaceDirectory.ServiceIdentity svc = requireService();
        if (svc == null) {
            return unauthorized();
        }
        return ResponseEntity.ok(Map.of(
                "serviceId", svc.id(),
                "tenantId", svc.tenant().value(),
                "role", svc.role().name(),
                "label", svc.label(),
                "rateLimit", RATE_LIMIT_NOTE
        ));
    }

    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<?> getJob(@PathVariable("jobId") String jobId) {
        WorkspaceDirectory.ServiceIdentity svc = requireService();
        if (svc == null) {
            return unauthorized();
        }
        if (!rateLimit(svc.tenant())) {
            return tooMany(svc.tenant());
        }
        JobRecord job = store.getJob(jobId).orElse(null);
        if (job == null || !svc.tenant().value().equals(job.getTenantId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiError("NOT_FOUND", "Unknown job").asMap());
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("jobId", job.getJobId());
        body.put("status", job.getStatus().name());
        body.put("jobKind", job.getJobKind().name());
        body.put("parentJobId", job.getParentJobId());
        body.put("libraryRevisionId", job.getLibraryRevisionId());
        body.put("environmentRevisionId", job.getEnvironmentRevisionId());
        body.put("message", job.getMessage() == null ? "" : job.getMessage());
        return ResponseEntity.ok(body);
    }

    @PostMapping("/jobs/{jobId}/cancel")
    public ResponseEntity<?> cancel(@PathVariable("jobId") String jobId) {
        WorkspaceDirectory.ServiceIdentity svc = requireService();
        if (svc == null) {
            return unauthorized();
        }
        if (svc.role() == WorkspaceRole.MEMBER) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ApiError("ROLE_REQUIRED", "Member role cannot mutate, execute, or export").asMap());
        }
        JobRecord job = store.getJob(jobId).orElse(null);
        if (job == null || !svc.tenant().value().equals(job.getTenantId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiError("NOT_FOUND", "Unknown job").asMap());
        }
        store.requestCancel(jobId);
        return ResponseEntity.accepted().body(Map.of("jobId", jobId, "cancelRequested", true));
    }

    @PostMapping("/projects/{projectId}/jobs")
    public ResponseEntity<?> submit(
            @PathVariable("projectId") String projectId,
            @RequestHeader(value = IDEMPOTENCY_HEADER, required = false) String idempotencyKey,
            @RequestBody(required = false) SubmitRequest body
    ) throws Exception {
        WorkspaceDirectory.ServiceIdentity svc = requireService();
        if (svc == null) {
            return unauthorized();
        }
        if (!rateLimit(svc.tenant())) {
            return tooMany(svc.tenant());
        }
        if (svc.role() == WorkspaceRole.MEMBER) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ApiError("ROLE_REQUIRED", "Member role cannot mutate, execute, or export").asMap());
        }
        ProjectRecord project = store.getProject(projectId).orElse(null);
        if (project == null || !svc.tenant().value().equals(project.getTenantId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiError("NOT_FOUND", "Unknown project").asMap());
        }
        String bodyHash = WebhookSigner.digestBody((body == null ? "" : body.toString()) + "|" + projectId);
        Path idemFile = delivery.identity.ScopePaths.tenantRoot(store.directory().storeRoot(), svc.tenant())
                .resolve("idempotency.json");
        IdempotencyStore keys = new IdempotencyStore(idemFile);
        Path excelPath;
        try {
            excelPath = delivery.portal.service.JobWorkbookResolver.resolve(
                    workbooks, projectId, true, null, null);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ApiError("NO_GENERATED_WORKBOOK",
                            "No generated workbook for this project — generate TCs first").asMap());
        }
        String username = "";
        String password = "";
        String profile = body == null ? null : body.credentialProfile();
        if (!credentials.list(projectId).isEmpty()) {
            if (profile == null || profile.isBlank()) {
                return ResponseEntity.badRequest()
                        .body(new ApiError("CREDENTIAL_PROFILE_REQUIRED", "credentialProfile is required").asMap());
            }
            var resolved = credentials.resolveForJob(projectId, profile.trim());
            username = resolved.username();
            password = resolved.passwordPlain();
        }
        String jobId = "job_ci_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            try {
                IdempotencyStore.Entry recorded = keys.putOrGet(idempotencyKey, bodyHash, jobId);
                if (!jobId.equals(recorded.jobId())) {
                    return ResponseEntity.ok(Map.of("jobId", recorded.jobId(), "idempotentReplay", true));
                }
            } catch (IdempotencyStore.Conflict e) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(new ApiError("IDEMPOTENCY_CONFLICT", e.getMessage()).asMap());
            }
        }
        String baseUrl = project.getBaseUrl();
        JobRecord job = new JobRecord(
                jobId,
                projectId,
                project.getOwnerUserId(),
                "EXECUTE",
                excelPath,
                baseUrl == null ? "" : baseUrl,
                username,
                password,
                false,
                JobRecord.JobKind.EXECUTE);
        job.setTenantId(project.getTenantId());
        if (body != null && body.environmentRevisionId() != null) {
            job.setEnvironmentRevisionId(body.environmentRevisionId());
        }
        store.saveJob(job);
        executeWorker.submit(jobId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                "jobId", jobId,
                "status", JobRecord.Status.QUEUED.name(),
                "rateLimit", RATE_LIMIT_NOTE
        ));
    }

    private boolean rateLimit(TenantId tenant) {
        return rateLimits.allow(tenant.value(), Instant.now());
    }

    private static WorkspaceDirectory.ServiceIdentity requireService() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof ServiceIdentityFilter.ServiceToken token) {
            return token.identity();
        }
        return null;
    }

    private static ResponseEntity<Map<String, String>> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiError("UNAUTHORIZED", "service identity required").asMap());
    }

    private static ResponseEntity<Map<String, String>> tooMany(TenantId tenant) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(new ApiError("RATE_LIMITED", RATE_LIMIT_NOTE).asMap());
    }
}
