package delivery.portal.api.v2;

import delivery.identity.WorkspaceDirectory;
import delivery.portal.jobs.DurableJobClaim;
import delivery.job.RateLimitPolicy;
import delivery.portal.model.JobRecord;
import delivery.portal.security.PrivateRunnerFilter;
import delivery.portal.service.PortalStore;
import delivery.portal.service.MobileExecuteResultService;
import delivery.mobile.result.MobileJobResultView;
import delivery.mobile.MobileProperties;
import keel.contract.runner.RunnerDtos;
import keel.contract.runner.RunnerHeaders;
import keel.contract.runner.WireJobKind;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * B06: Runner API v2 on the existing 8080 connector (contract: keel-contract runner-api-v2.yaml).
 * Authentication (runner token + HMAC signature, Â±5 min, nonce replay) happens in {@link PrivateRunnerFilter}.
 * Every job-scoped call is fenced by attempt id + cancel generation + fencing token. Cross-tenant job ids → 404.
 */
@RestController
@RequestMapping(RunnerHeaders.V2_PREFIX)
public class RunnerApiV2Controller {
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9_.-]{1,80}");
    static final int MAX_CHUNKS = 2048;

    private final PortalStore store;
    private final RunnerFencing fencing;
    private final MobileExecuteResultService mobileResults;
    private final MobileProperties mobileProperties;
    private final RateLimitPolicy rateLimits = new RateLimitPolicy();
    private final long chunkMaxBytes;
    private final Path chunkRoot;

    public RunnerApiV2Controller(PortalStore store, RunnerFencing fencing,
                                 MobileExecuteResultService mobileResults,
                                 MobileProperties mobileProperties,
                                 @Value("${keel.runner.v2.chunk-max-bytes:8388608}") long chunkMaxBytes,
                                 @Value("${keel.runner.v2.chunk-dir:}") String chunkDir) {
        this.store = store;
        this.fencing = fencing;
        this.mobileResults = mobileResults;
        this.mobileProperties = mobileProperties;
        this.chunkMaxBytes = chunkMaxBytes;
        this.chunkRoot = chunkDir == null || chunkDir.isBlank()
                ? Path.of(System.getProperty("java.io.tmpdir"), "keel-v2-chunks") : Path.of(chunkDir);
    }

    @PostMapping("/claim")
    public ResponseEntity<?> claim(@RequestBody(required = false) RunnerDtos.ClaimRequest request) throws Exception {
        WorkspaceDirectory.RunnerEnrollment runner = runner();
        if (runner == null) {
            return error(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "runner token required");
        }
        if (!rateLimits.allow(runner.tenant().value(), Instant.now())) {
            return error(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMIT", "slow down");
        }
        java.util.Set<JobRecord.JobKind> kinds = request == null || request.kinds() == null ? null
                : request.kinds().stream().map(k -> JobRecord.JobKind.valueOf(k.name()))
                .collect(java.util.stream.Collectors.toCollection(() -> java.util.EnumSet.noneOf(JobRecord.JobKind.class)));
        Optional<DurableJobClaim.Lease> lease = store.claimNextForRunner(runner, kinds);
        if (lease.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        JobRecord job = store.getJob(lease.get().jobId()).orElseThrow();
        long token = fencing.issue(job.getJobId());
        return ResponseEntity.ok(new RunnerDtos.ClaimResponse(job.getJobId(), lease.get().attemptId(),
                lease.get().cancelGeneration(), token, job.getProjectId(),
                WireJobKind.valueOf(job.getJobKind().name()), job.getLibraryRevisionId(),
                job.getEnvironmentRevisionId(), job.getInputSnapshotHash(), job.getProviderAllowlistSnapshot(),
                job.getLeaseUntil() == null ? null : job.getLeaseUntil().toString()));
    }

    @PostMapping("/jobs/{jobId}/lease")
    public ResponseEntity<?> lease(@PathVariable("jobId") String jobId,
                                   @RequestHeader(value = RunnerHeaders.ATTEMPT, required = false) String attemptId,
                                   @RequestHeader(value = RunnerHeaders.CANCEL_GENERATION, required = false) Long generation,
                                   @RequestHeader(value = RunnerHeaders.FENCING_TOKEN, required = false) Long token)
            throws Exception {
        Fenced f = fence(jobId, attemptId, generation, token, true);
        if (f.error != null) {
            return f.error;
        }
        boolean ok = store.heartbeatRunnerLease(f.runner, jobId, attemptId, generation.intValue());
        JobRecord job = store.getJob(jobId).orElseThrow();
        boolean cancel = cancelRequested(job);
        if (!ok && !cancel) {
            return error(HttpStatus.CONFLICT, "LEASE_LOST", "lease expired or not owned");
        }
        return ResponseEntity.ok(new RunnerDtos.LeaseResponse(jobId, cancel, job.getCancelGeneration(),
                job.getLeaseUntil() == null ? null : job.getLeaseUntil().toString()));
    }

    @GetMapping("/jobs/{jobId}/cancel")
    public ResponseEntity<?> cancel(@PathVariable("jobId") String jobId,
                                    @RequestHeader(value = RunnerHeaders.ATTEMPT, required = false) String attemptId,
                                    @RequestHeader(value = RunnerHeaders.CANCEL_GENERATION, required = false) Long generation,
                                    @RequestHeader(value = RunnerHeaders.FENCING_TOKEN, required = false) Long token)
            throws Exception {
        // Cancel polling must keep working after a cancel bumped the generation, so the generation isn't enforced here.
        Fenced f = fence(jobId, attemptId, generation, token, false);
        if (f.error != null) {
            return f.error;
        }
        JobRecord job = f.job;
        return ResponseEntity.ok(new RunnerDtos.LeaseResponse(jobId, cancelRequested(job), job.getCancelGeneration(),
                job.getLeaseUntil() == null ? null : job.getLeaseUntil().toString()));
    }

    /** B07: frozen job input for the engine (same pack as v1 /input: job.json + suite.xlsx). Fenced; read-only. */
    @GetMapping(value = "/jobs/{jobId}/input")
    public ResponseEntity<?> input(@PathVariable("jobId") String jobId,
                                   @RequestHeader(value = RunnerHeaders.ATTEMPT, required = false) String attemptId,
                                   @RequestHeader(value = RunnerHeaders.CANCEL_GENERATION, required = false) Long generation,
                                   @RequestHeader(value = RunnerHeaders.FENCING_TOKEN, required = false) Long token)
            throws Exception {
        Fenced f = fence(jobId, attemptId, generation, token, false);
        if (f.error != null) {
            return f.error;
        }
        return ResponseEntity.ok().header("Content-Type", "application/zip")
                .body(delivery.portal.api.v1.PrivateRunnerApiController.inputPack(store, f.job));
    }

    @PostMapping("/jobs/{jobId}/stage")
    public ResponseEntity<?> stage(@PathVariable("jobId") String jobId,
                                   @RequestHeader(value = RunnerHeaders.ATTEMPT, required = false) String attemptId,
                                   @RequestHeader(value = RunnerHeaders.CANCEL_GENERATION, required = false) Long generation,
                                   @RequestHeader(value = RunnerHeaders.FENCING_TOKEN, required = false) Long token,
                                   @RequestBody(required = false) RunnerDtos.StageRequest body) throws Exception {
        Fenced f = fence(jobId, attemptId, generation, token, true);
        if (f.error != null) {
            return f.error;
        }
        if (body == null || body.stage() == null) {
            return error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "stage is required");
        }
        try {
            String claimStage = store.markRunnerStage(f.runner, jobId, body.stage().name(), attemptId);
            return ResponseEntity.ok(java.util.Map.of("jobId", jobId, "claimStage", claimStage));
        } catch (IllegalArgumentException e) {
            return error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", e.getMessage());
        }
    }

    @PostMapping("/jobs/{jobId}/progress")
    public ResponseEntity<?> progress(@PathVariable("jobId") String jobId,
                                      @RequestHeader(value = RunnerHeaders.ATTEMPT, required = false) String attemptId,
                                      @RequestHeader(value = RunnerHeaders.CANCEL_GENERATION, required = false) Long generation,
                                      @RequestHeader(value = RunnerHeaders.FENCING_TOKEN, required = false) Long token,
                                      @RequestBody(required = false) RunnerDtos.ProgressRequest body) throws Exception {
        Fenced f = fence(jobId, attemptId, generation, token, true);
        if (f.error != null) {
            return f.error;
        }
        if (body != null && body.message() != null && !body.message().isBlank()) {
            String msg = body.message().length() > 500 ? body.message().substring(0, 500) : body.message();
            f.job.setMessage(msg);
        }
        return ResponseEntity.accepted().body(java.util.Map.of("jobId", jobId));
    }

    @PutMapping("/jobs/{jobId}/artifacts/{artifactId}/chunks/{index}")
    public ResponseEntity<?> chunk(@PathVariable("jobId") String jobId,
                                   @PathVariable("artifactId") String artifactId,
                                   @PathVariable("index") int index,
                                   @RequestParam(value = "last", defaultValue = "false") boolean last,
                                   @RequestHeader(value = RunnerHeaders.ATTEMPT, required = false) String attemptId,
                                   @RequestHeader(value = RunnerHeaders.CANCEL_GENERATION, required = false) Long generation,
                                   @RequestHeader(value = RunnerHeaders.FENCING_TOKEN, required = false) Long token,
                                   @RequestBody(required = false) byte[] body) throws Exception {
        Fenced f = fence(jobId, attemptId, generation, token, true);
        if (f.error != null) {
            return f.error;
        }
        if (!SAFE_ID.matcher(artifactId).matches() || index < 0 || index >= MAX_CHUNKS) {
            return error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "bad artifact id or chunk index");
        }
        byte[] bytes = body == null ? new byte[0] : body;
        if (bytes.length > chunkMaxBytes) {
            return error(HttpStatus.PAYLOAD_TOO_LARGE, "CHUNK_TOO_LARGE", "chunk exceeds " + chunkMaxBytes + " bytes");
        }
        Path dir = chunkRoot.resolve(jobId).resolve(attemptId).resolve(artifactId);
        Files.createDirectories(dir);
        Files.write(dir.resolve(String.format("%05d.part", index)), bytes);
        if (!last) {
            return ResponseEntity.accepted().body(new RunnerDtos.ArtifactChunkResponse(jobId, artifactId, index, true, false));
        }
        List<Path> parts;
        try (Stream<Path> s = Files.list(dir)) {
            parts = s.filter(p -> p.getFileName().toString().endsWith(".part"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString())).toList();
        }
        if (parts.size() != index + 1) {
            return error(HttpStatus.CONFLICT, "CHUNKS_MISSING", "expected " + (index + 1) + " chunks, have " + parts.size());
        }
        ByteArrayOutputStream all = new ByteArrayOutputStream();
        for (Path p : parts) {
            all.write(Files.readAllBytes(p));
        }
        store.storeRunnerArtifact(f.runner, jobId, all.toByteArray(), attemptId);
        deleteTree(dir);
        return ResponseEntity.accepted().body(new RunnerDtos.ArtifactChunkResponse(jobId, artifactId, index, true, true));
    }

    @PostMapping("/jobs/{jobId}/result")
    public ResponseEntity<?> result(@PathVariable("jobId") String jobId,
                                    @RequestHeader(value = RunnerHeaders.ATTEMPT, required = false) String attemptId,
                                    @RequestHeader(value = RunnerHeaders.CANCEL_GENERATION, required = false) Long generation,
                                    @RequestHeader(value = RunnerHeaders.FENCING_TOKEN, required = false) Long token,
                                    @RequestBody(required = false) RunnerDtos.ResultRequest body) throws Exception {
        // A result after a cancel must still land (it records CANCELLED), so the generation isn't enforced here.
        Fenced f = fence(jobId, attemptId, generation, token, false);
        if (f.error != null) {
            return f.error;
        }
        JobRecord job = f.job;
        if (body != null && body.providerAllowlistSnapshot() != null && !body.providerAllowlistSnapshot().isBlank()
                && !body.providerAllowlistSnapshot().equals(job.getProviderAllowlistSnapshot())) {
            return error(HttpStatus.CONFLICT, "PROVIDER_POLICY", "runner cannot change the frozen provider allowlist");
        }
        JobRecord.Status status = JobRecord.Status.COMPLETED;
        if (body != null && body.status() != null && !body.status().isBlank()) {
            try {
                status = JobRecord.Status.valueOf(body.status().trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                return error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "unknown status");
            }
        }
        try {
            store.completeFromRunner(f.runner, jobId, status,
                    body == null || body.passedCount() == null ? 0 : body.passedCount(),
                    body == null || body.todoCount() == null ? 0 : body.todoCount(),
                    body == null ? "" : body.message(),
                    body == null ? null : body.inputSnapshotHash(), attemptId);
        } catch (IllegalStateException e) {
            if ("FROZEN_INPUT_MISMATCH".equals(e.getMessage())) {
                return error(HttpStatus.CONFLICT, "FROZEN_INPUT_MISMATCH", "pinned inputs changed");
            }
            throw e;
        }
        fencing.release(jobId);
        return ResponseEntity.ok(java.util.Map.of("jobId", jobId,
                "status", store.getJob(jobId).orElseThrow().getStatus().name()));
    }


    /**
     * M2-07: ingest published {@link MobileJobResultView} as a job artifact side file.
     * Does not mutate frozen admission inputs / inputSnapshotHash.
     */
    @PutMapping("/jobs/{jobId}/mobile-result")
    public ResponseEntity<?> publishMobileResult(@PathVariable("jobId") String jobId,
                                    @RequestHeader(value = RunnerHeaders.ATTEMPT, required = false) String attemptId,
                                    @RequestHeader(value = RunnerHeaders.CANCEL_GENERATION, required = false) Long generation,
                                    @RequestHeader(value = RunnerHeaders.FENCING_TOKEN, required = false) Long token,
                                    @RequestBody(required = false) MobileJobResultView body) {
        if (mobileProperties == null || !mobileProperties.isEnabled()) {
            return error(HttpStatus.NOT_FOUND, "NOT_FOUND", "Mobile is disabled");
        }
        Fenced f = fence(jobId, attemptId, generation, token, false);
        if (f.error != null) {
            return f.error;
        }
        if (body == null) {
            return error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "body required");
        }
        // Bind path jobId into view; keep attempt from headers when body omits it.
        String att = body.attemptId() == null || body.attemptId().isBlank() ? attemptId : body.attemptId();
        MobileJobResultView normalized = MobileJobResultView.of(
                jobId,
                att,
                f.job.getProjectId(),
                body.runtime(),
                body.cases(),
                body.timingMs());
        try {
            MobileJobResultView stored = mobileResults.publish(
                    f.runner.tenant().value(), f.job.getProjectId(), normalized);
            return ResponseEntity.ok(stored);
        } catch (org.springframework.web.server.ResponseStatusException e) {
            HttpStatus st = HttpStatus.resolve(e.getStatusCode().value());
            if (st == null) {
                st = HttpStatus.BAD_REQUEST;
            }
            return error(st, st == HttpStatus.NOT_FOUND ? "NOT_FOUND" : "BAD_REQUEST",
                    e.getReason() == null ? "error" : e.getReason());
        }
    }

    private record Fenced(WorkspaceDirectory.RunnerEnrollment runner, JobRecord job, ResponseEntity<?> error) {
        static Fenced fail(ResponseEntity<?> e) {
            return new Fenced(null, null, e);
        }
    }

    private Fenced fence(String jobId, String attemptId, Long generation, Long token, boolean enforceGeneration) {
        WorkspaceDirectory.RunnerEnrollment runner = runner();
        if (runner == null) {
            return Fenced.fail(error(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "runner token required"));
        }
        JobRecord job = store.getJob(jobId).orElse(null);
        if (job == null || !runner.tenant().value().equals(job.getTenantId())) {
            return Fenced.fail(error(HttpStatus.NOT_FOUND, "NOT_FOUND", "Unknown job"));
        }
        if (attemptId == null || attemptId.isBlank() || generation == null || token == null) {
            return Fenced.fail(error(HttpStatus.BAD_REQUEST, "FENCING_HEADERS_REQUIRED",
                    RunnerHeaders.ATTEMPT + ", " + RunnerHeaders.CANCEL_GENERATION + " and "
                            + RunnerHeaders.FENCING_TOKEN + " are required"));
        }
        try {
            store.requireRunnerAttempt(runner, job, attemptId);
        } catch (SecurityException e) {
            return Fenced.fail(error(HttpStatus.CONFLICT, "LEASE_LOST", "attempt not owned"));
        } catch (IllegalStateException e) {
            return Fenced.fail(error(HttpStatus.CONFLICT, "LEASE_LOST", "lease expired or job finished"));
        }
        if (!fencing.accept(jobId, token)) {
            return Fenced.fail(error(HttpStatus.CONFLICT, "STALE_FENCING_TOKEN", "fencing token is not current"));
        }
        if (enforceGeneration && generation != job.getCancelGeneration()) {
            return Fenced.fail(error(HttpStatus.CONFLICT, "CANCEL_GENERATION_CHANGED",
                    "cancel generation is " + job.getCancelGeneration()));
        }
        return new Fenced(runner, job, null);
    }

    private boolean cancelRequested(JobRecord job) {
        return store.isCancelRequested(job.getJobId())
                || job.getStatus() == JobRecord.Status.CANCELLING
                || job.getStatus() == JobRecord.Status.CANCELLED;
    }

    private static WorkspaceDirectory.RunnerEnrollment runner() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth instanceof PrivateRunnerFilter.RunnerToken t ? t.enrollment() : null;
    }

    private static ResponseEntity<RunnerDtos.ErrorBody> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new RunnerDtos.ErrorBody(code, message));
    }

    private static void deleteTree(Path dir) {
        try (Stream<Path> s = Files.walk(dir)) {
            s.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (Exception ignored) {
            // temp chunks; best effort
        }
    }
}
