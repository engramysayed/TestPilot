package delivery.portal.api.v1;

import delivery.identity.WorkspaceDirectory;
import delivery.job.RateLimitPolicy;
import delivery.portal.api.ApiError;
import delivery.portal.model.JobRecord;
import delivery.portal.security.PrivateRunnerFilter;
import delivery.portal.service.PortalStore;
import delivery.runner.PrivateRunnerArtifacts;
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

import java.nio.file.Files;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/runners")
public class PrivateRunnerApiController {
    private final PortalStore store;
    private final RateLimitPolicy rateLimits = new RateLimitPolicy();

    public PrivateRunnerApiController(PortalStore store) {
        this.store = store;
    }

    public record CompleteRequest(
            String status,
            Integer passedCount,
            Integer todoCount,
            String message,
            String inputSnapshotHash,
            String providerAllowlistSnapshot
    ) {
    }

    @PostMapping("/heartbeat")
    public ResponseEntity<?> heartbeat() throws Exception {
        var runner = requireRunner();
        if (runner == null) {
            return unauthorized();
        }
        if (!rateLimit(runner)) {
            return tooMany();
        }
        store.directory().touchRunnerHeartbeat(runner.id(), runner.tenant(), Instant.now());
        return ResponseEntity.ok(Map.of(
                "runnerId", runner.id(),
                "tenantId", runner.tenant().value(),
                "heartbeat", Instant.now().toString()
        ));
    }

    @PostMapping("/claim")
    public ResponseEntity<?> claim() throws Exception {
        var runner = requireRunner();
        if (runner == null) {
            return unauthorized();
        }
        if (!rateLimit(runner)) {
            return tooMany();
        }
        Optional<delivery.job.DurableJobClaim.Lease> lease = store.claimNextForRunner(runner);
        if (lease.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        JobRecord job = store.getJob(lease.get().jobId()).orElseThrow();
        Map<String, Object> body = claimBody(job, lease.get());
        return ResponseEntity.ok(body);
    }

    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<?> job(@PathVariable("jobId") String jobId) throws Exception {
        var runner = requireRunner();
        if (runner == null) {
            return unauthorized();
        }
        JobRecord job = store.getJob(jobId).orElse(null);
        if (job == null || !runner.tenant().value().equals(job.getTenantId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiError("NOT_FOUND", "Unknown job").asMap());
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("jobId", job.getJobId());
        body.put("status", job.getStatus().name());
        body.put("cancelRequested", store.isCancelRequested(jobId)
                || job.getStatus() == JobRecord.Status.CANCELLING
                || job.getStatus() == JobRecord.Status.CANCELLED);
        body.put("libraryRevisionId", job.getLibraryRevisionId());
        body.put("environmentRevisionId", job.getEnvironmentRevisionId());
        body.put("inputSnapshotHash", job.getInputSnapshotHash());
        body.put("providerAllowlistSnapshot", job.getProviderAllowlistSnapshot());
        body.put("attemptId", job.getAttemptId());
        body.put("claimStage", job.getClaimStage() == null ? "" : job.getClaimStage());
        return ResponseEntity.ok(body);
    }

    public record StageRequest(String stage) {
    }

    @PostMapping("/jobs/{jobId}/stage")
    public ResponseEntity<?> stage(
            @PathVariable("jobId") String jobId,
            @RequestBody(required = false) StageRequest body
    ) throws Exception {
        var runner = requireRunner();
        if (runner == null) {
            return unauthorized();
        }
        if (body == null || body.stage() == null || body.stage().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(new ApiError("BAD_REQUEST", "stage is required").asMap());
        }
        try {
            String claimStage = store.markRunnerStage(runner, jobId, body.stage());
            return ResponseEntity.ok(Map.of("jobId", jobId, "claimStage", claimStage));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiError("NOT_FOUND", e.getMessage()).asMap());
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ApiError("FORBIDDEN", e.getMessage()).asMap());
        }
    }

    @GetMapping("/jobs/{jobId}/input")
    public ResponseEntity<byte[]> input(@PathVariable("jobId") String jobId) throws Exception {
        var runner = requireRunner();
        if (runner == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        JobRecord job = store.getJob(jobId).orElse(null);
        if (job == null || !runner.tenant().value().equals(job.getTenantId())
                || !runner.id().equals(job.getWorkerId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        byte[] excel = job.getExcelPath() != null && Files.isRegularFile(job.getExcelPath())
                ? Files.readAllBytes(job.getExcelPath()) : new byte[0];
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("jobId", job.getJobId());
        meta.put("projectId", job.getProjectId());
        meta.put("baseUrl", job.getBaseUrl());
        meta.put("username", job.getUsername() == null ? "" : job.getUsername());
        meta.put("password", job.getPassword() == null ? "" : job.getPassword());
        meta.put("libraryRevisionId", job.getLibraryRevisionId());
        meta.put("environmentRevisionId", job.getEnvironmentRevisionId());
        meta.put("inputSnapshotHash", job.getInputSnapshotHash());
        meta.put("providerAllowlistSnapshot", job.getProviderAllowlistSnapshot());
        meta.put("authoringEngine", job.getAuthoringEngine().wireValue());
        meta.put("precisionEnabled", store.precisionConfigForJob(job).enabled());
        meta.put("precisionMaxCalls", store.precisionConfigForJob(job).maxCallsPerJob());
        meta.put("mode", job.getMode());
        meta.put("jobKind", job.getJobKind().name());
        meta.put("tenantId", job.getTenantId());
        meta.put("attemptId", job.getAttemptId());
        byte[] json = new org.json.JSONObject(meta).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(bos)) {
            zos.putNextEntry(new java.util.zip.ZipEntry("job.json"));
            zos.write(json);
            zos.closeEntry();
            zos.putNextEntry(new java.util.zip.ZipEntry("suite.xlsx"));
            zos.write(excel);
            zos.closeEntry();
        }
        return ResponseEntity.ok()
                .header("Content-Type", "application/zip")
                .body(bos.toByteArray());
    }

    @PostMapping("/jobs/{jobId}/lease")
    public ResponseEntity<?> lease(@PathVariable("jobId") String jobId) throws Exception {
        var runner = requireRunner();
        if (runner == null) {
            return unauthorized();
        }
        boolean ok = store.heartbeatRunnerLease(runner, jobId);
        JobRecord job = store.getJob(jobId).orElse(null);
        if (job == null || !runner.tenant().value().equals(job.getTenantId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiError("NOT_FOUND", "Unknown job").asMap());
        }
        boolean cancelRequested = store.isCancelRequested(jobId)
                || job.getStatus() == JobRecord.Status.CANCELLING
                || job.getStatus() == JobRecord.Status.CANCELLED;
        if (!ok && !cancelRequested) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ApiError("LEASE_LOST", "lease expired or not owned").asMap());
        }
        return ResponseEntity.ok(Map.of(
                "jobId", jobId,
                "cancelRequested", cancelRequested
        ));
    }

    @PostMapping("/jobs/{jobId}/artifacts")
    public ResponseEntity<?> artifacts(
            @PathVariable("jobId") String jobId,
            @RequestHeader(value = PrivateRunnerArtifacts.SIGNATURE_HEADER, required = false) String signature,
            @RequestBody byte[] body
    ) throws Exception {
        PrivateRunnerFilter.RunnerToken token = runnerToken();
        if (token == null) {
            return unauthorized();
        }
        JobRecord job = store.getJob(jobId).orElse(null);
        if (job == null || !token.enrollment().tenant().value().equals(job.getTenantId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiError("NOT_FOUND", "Unknown job").asMap());
        }
        if (!PrivateRunnerArtifacts.matches(token.token(), jobId, job.getAttemptId(), body, signature)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiError("BAD_SIGNATURE", "artifact signature rejected").asMap());
        }
        store.storeRunnerArtifact(token.enrollment(), jobId, body);
        return ResponseEntity.accepted().body(Map.of("jobId", jobId, "stored", true));
    }

    @PostMapping("/jobs/{jobId}/complete")
    public ResponseEntity<?> complete(
            @PathVariable("jobId") String jobId,
            @RequestBody(required = false) CompleteRequest body
    ) throws Exception {
        var runner = requireRunner();
        if (runner == null) {
            return unauthorized();
        }
        JobRecord job = store.getJob(jobId).orElse(null);
        if (job == null || !runner.tenant().value().equals(job.getTenantId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiError("NOT_FOUND", "Unknown job").asMap());
        }
        if (body != null && body.providerAllowlistSnapshot() != null && !body.providerAllowlistSnapshot().isBlank()
                && !body.providerAllowlistSnapshot().equals(job.getProviderAllowlistSnapshot())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ApiError("PROVIDER_POLICY", "runner cannot change the frozen provider allowlist").asMap());
        }
        JobRecord.Status status = JobRecord.Status.COMPLETED;
        if (body != null && body.status() != null && !body.status().isBlank()) {
            status = JobRecord.Status.valueOf(body.status().trim().toUpperCase());
        }
        try {
            store.completeFromRunner(
                    runner,
                    jobId,
                    status,
                    body == null || body.passedCount() == null ? 0 : body.passedCount(),
                    body == null || body.todoCount() == null ? 0 : body.todoCount(),
                    body == null ? "" : body.message(),
                    body == null ? null : body.inputSnapshotHash());
        } catch (IllegalStateException e) {
            if ("FROZEN_INPUT_MISMATCH".equals(e.getMessage())) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(new ApiError("FROZEN_INPUT_MISMATCH", "pinned inputs changed").asMap());
            }
            throw e;
        }
        JobRecord updated = store.getJob(jobId).orElseThrow();
        return ResponseEntity.ok(Map.of("jobId", jobId, "status", updated.getStatus().name()));
    }

    private Map<String, Object> claimBody(JobRecord job, delivery.job.DurableJobClaim.Lease lease) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("jobId", job.getJobId());
        body.put("attemptId", lease.attemptId());
        body.put("projectId", job.getProjectId());
        body.put("jobKind", job.getJobKind().name());
        body.put("libraryRevisionId", job.getLibraryRevisionId());
        body.put("environmentRevisionId", job.getEnvironmentRevisionId());
        body.put("inputSnapshotHash", job.getInputSnapshotHash());
        body.put("providerAllowlistSnapshot", job.getProviderAllowlistSnapshot());
        body.put("leaseUntil", job.getLeaseUntil() == null ? "" : job.getLeaseUntil().toString());
        return body;
    }

    private boolean rateLimit(WorkspaceDirectory.RunnerEnrollment runner) {
        return rateLimits.allow(runner.tenant().value(), Instant.now());
    }

    private static WorkspaceDirectory.RunnerEnrollment requireRunner() {
        PrivateRunnerFilter.RunnerToken token = runnerToken();
        return token == null ? null : token.enrollment();
    }

    private static PrivateRunnerFilter.RunnerToken runnerToken() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof PrivateRunnerFilter.RunnerToken token) {
            return token;
        }
        return null;
    }

    private static ResponseEntity<Map<String, String>> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiError("UNAUTHORIZED", "runner token required").asMap());
    }

    private static ResponseEntity<Map<String, String>> tooMany() {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(new ApiError("RATE_LIMIT", PublicJobApiController.RATE_LIMIT_NOTE).asMap());
    }
}
