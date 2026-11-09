package delivery.portal.service.credits;

import delivery.job.RetryCategory;
import delivery.portal.jobs.JobDiagnostics;
import delivery.portal.model.JobRecord;
import delivery.portal.persistence.JobEntity;
import delivery.portal.persistence.JobRepository;
import delivery.portal.persistence.PortalUser;
import delivery.portal.persistence.PortalUserRepository;
import delivery.portal.persistence.ProjectEntity;
import delivery.portal.persistence.ProjectRepository;
import delivery.portal.persistence.credits.CreditLedgerEntry;
import delivery.portal.persistence.credits.RefundRequest;
import delivery.portal.persistence.credits.RefundRequestRepository;
import delivery.portal.service.PortalStore;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Refund requests for charged jobs that failed.
 * <ul>
 *   <li><b>AUTO</b>: {@link #sweep()} opens a PENDING request for every charged job that FAILED for a
 *       platform reason ({@link RetryCategory#INFRA}: browser crash, network, lease lost / interrupted,
 *       model provider unavailable, out of disk/memory).</li>
 *   <li><b>USER</b>: the job owner can ask for any failed charged job from the run page, with a note.</li>
 * </ul>
 * The account owner reviews each request in Admin → Refunds with the full failure evidence and approves
 * (full or partial, writes {@code JOB_REFUND}) or rejects it with a note the user sees.
 */
@Service
public class RefundRequestService {

    private static final Logger log = LoggerFactory.getLogger(RefundRequestService.class);

    private final RefundRequestRepository requests;
    private final CreditService credits;
    private final JobRepository jobs;
    private final PortalStore store;
    private final PortalUserRepository users;
    private final ProjectRepository projects;

    @Value("${delivery.credits.refunds.sweep-lookback-hours:72}")
    private long lookbackHours = 72;

    @Value("${delivery.credits.refunds.auto-open:true}")
    private boolean autoOpen = true;

    public RefundRequestService(
            RefundRequestRepository requests,
            CreditService credits,
            JobRepository jobs,
            PortalStore store,
            PortalUserRepository users,
            ProjectRepository projects
    ) {
        this.requests = requests;
        this.credits = credits;
        this.jobs = jobs;
        this.store = store;
        this.users = users;
        this.projects = projects;
    }

    // ------------------------------------------------------------------ automatic

    /** Platform-failure categories that open a refund request without the user asking. */
    public static boolean platformFailure(RetryCategory c) {
        return c == RetryCategory.INFRA;
    }

    public static RetryCategory classify(String error, String message) {
        String e = error == null ? "" : error;
        String m = message == null ? "" : message;
        return RetryCategory.classify((e + " " + m).trim());
    }

    @Scheduled(initialDelayString = "${delivery.credits.refunds.sweep-ms:120000}",
            fixedDelayString = "${delivery.credits.refunds.sweep-ms:120000}")
    public void sweep() {
        if (!autoOpen) {
            return;
        }
        Instant since = Instant.now().minus(Duration.ofHours(Math.max(1, lookbackHours)));
        int opened = 0;
        for (JobEntity e : jobs.findByStatusAndCompletedAtAfter(JobRecord.Status.FAILED.name(), since)) {
            try {
                if (requests.findByJobId(e.getJobId()).isPresent()) {
                    continue;
                }
                RetryCategory cat = classify(e.getError(), e.getMessage());
                if (!platformFailure(cat)) {
                    continue;
                }
                if (credits.chargedForJob(e.getJobId()) <= 0L) {
                    continue;
                }
                if (open(e.getJobId(), RefundRequest.Source.AUTO, null).isPresent()) {
                    opened++;
                }
            } catch (RuntimeException ex) {
                log.warn("Refund sweep skipped job {}: {}", e.getJobId(), ex.getMessage());
            }
        }
        if (opened > 0) {
            log.info("Opened {} automatic refund request(s) for platform failures", opened);
        }
    }

    // ------------------------------------------------------------------ user

    /** Result of a user asking for a refund. */
    public record UserRequestOutcome(boolean ok, String code, String message, Map<String, Object> request) {
    }

    @Transactional
    public UserRequestOutcome requestByOwner(String jobId, Long userId, String note) {
        Optional<JobRecord> job = store.getJob(jobId);
        if (job.isEmpty() || job.get().getOwnerUserId() == null || !job.get().getOwnerUserId().equals(userId)) {
            return new UserRequestOutcome(false, "NOT_FOUND", "Run not found.", null);
        }
        JobRecord j = job.get();
        if (j.getStatus() != JobRecord.Status.FAILED && j.getStatus() != JobRecord.Status.COMPLETED_WITH_BLOCK) {
            return new UserRequestOutcome(false, "NOT_FAILED", "Only failed runs can be refunded.", null);
        }
        if (credits.chargedForJob(jobId) <= 0L) {
            return new UserRequestOutcome(false, "NOT_CHARGED", "No credits were charged for this run.", null);
        }
        Optional<RefundRequest> existing = requests.findByJobId(jobId);
        if (existing.isPresent()) {
            RefundRequest r = existing.get();
            if (note != null && !note.isBlank() && (r.getUserNote() == null || r.getUserNote().isBlank())
                    && RefundRequest.Status.PENDING.name().equals(r.getStatus())) {
                r.setUserNote(clip(note, 1000));
                requests.save(r);
            }
            return new UserRequestOutcome(true, "EXISTS", "A refund request already exists for this run.", userView(r));
        }
        return open(jobId, RefundRequest.Source.USER, note)
                .map(r -> new UserRequestOutcome(true, "OPENED", "Refund requested. The account owner will review it.", userView(r)))
                .orElse(new UserRequestOutcome(false, "ERROR", "Could not open a refund request.", null));
    }

    @Transactional(readOnly = true)
    public Optional<Map<String, Object>> forJob(String jobId, Long userId) {
        return requests.findByJobId(jobId)
                .filter(r -> r.getUserId() != null && r.getUserId().equals(userId))
                .map(this::userView);
    }

    // ------------------------------------------------------------------ admin

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(String status) {
        List<RefundRequest> rows = status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)
                ? requests.findAllByOrderByCreatedAtDesc()
                : requests.findByStatusOrderByCreatedAtDesc(status.trim().toUpperCase(Locale.ROOT));
        List<Map<String, Object>> out = new ArrayList<>();
        for (RefundRequest r : rows) {
            out.add(summary(r));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> counts() {
        Map<String, Object> m = new LinkedHashMap<>();
        for (RefundRequest.Status s : RefundRequest.Status.values()) {
            m.put(s.name(), requests.countByStatus(s.name()));
        }
        return m;
    }

    @Transactional(readOnly = true)
    public Optional<Map<String, Object>> detail(Long id) {
        return requests.findById(id).map(this::adminDetail);
    }

    @Transactional
    public Map<String, Object> approve(Long id, Long adminId, Long amountOrNull, String adminNote) {
        RefundRequest r = requests.findById(id).orElseThrow(() -> new IllegalArgumentException("Unknown refund request " + id));
        if (!RefundRequest.Status.PENDING.name().equals(r.getStatus())) {
            throw new IllegalStateException("Request is already " + r.getStatus());
        }
        long charged = credits.chargedForJob(r.getJobId());
        long amount = amountOrNull == null ? charged : amountOrNull;
        String note = "Refund for failed run " + r.getJobId()
                + (adminNote == null || adminNote.isBlank() ? "" : " — " + adminNote.trim());
        CreditLedgerEntry entry = credits.refundForJob(r.getUserId(), r.getJobId(), r.getJobKind(), amount, adminId, note);
        r.setStatus(RefundRequest.Status.APPROVED.name());
        r.setRefundedAmount(entry.getDelta());
        r.setChargedAmount(charged);
        r.setRefundLedgerId(entry.getId());
        r.setDecidedBy(adminId);
        r.setDecidedAt(Instant.now());
        r.setAdminNote(clip(adminNote, 1000));
        requests.save(r);
        return adminDetail(r);
    }

    @Transactional
    public Map<String, Object> reject(Long id, Long adminId, String adminNote) {
        RefundRequest r = requests.findById(id).orElseThrow(() -> new IllegalArgumentException("Unknown refund request " + id));
        if (!RefundRequest.Status.PENDING.name().equals(r.getStatus())) {
            throw new IllegalStateException("Request is already " + r.getStatus());
        }
        if (adminNote == null || adminNote.isBlank()) {
            throw new IllegalArgumentException("Tell the user why the refund was rejected.");
        }
        r.setStatus(RefundRequest.Status.REJECTED.name());
        r.setDecidedBy(adminId);
        r.setDecidedAt(Instant.now());
        r.setAdminNote(clip(adminNote, 1000));
        requests.save(r);
        return adminDetail(r);
    }

    // ------------------------------------------------------------------ internals

    Optional<RefundRequest> open(String jobId, RefundRequest.Source source, String userNote) {
        Optional<JobRecord> jobOpt = store.getJob(jobId);
        if (jobOpt.isEmpty()) {
            return Optional.empty();
        }
        JobRecord job = jobOpt.get();
        long charged = credits.chargedForJob(jobId);
        RetryCategory cat = classify(job.getError(), job.getMessage());
        RefundRequest r = new RefundRequest();
        r.setJobId(jobId);
        r.setProjectId(job.getProjectId());
        r.setUserId(job.getOwnerUserId());
        r.setJobKind(job.getJobKind() == null ? null : job.getJobKind().name());
        r.setChargedAmount(charged);
        r.setSource(source.name());
        r.setFailureCategory(cat.name());
        r.setFailureError(clip(job.getError(), 1024));
        r.setFailureMessage(clip(job.getMessage(), 1024));
        r.setUserNote(clip(userNote, 1000));
        String ev = evidence(job).toString();
        r.setEvidenceJson(ev.length() > 32000 ? ev.substring(0, 32000) : ev);
        try {
            return Optional.of(requests.save(r));
        } catch (DataIntegrityViolationException race) {
            return requests.findByJobId(jobId);
        }
    }

    /** Snapshot of everything an admin needs to judge the refund, frozen at request time. */
    JSONObject evidence(JobRecord job) {
        JSONObject o = new JSONObject();
        o.put("jobId", job.getJobId());
        o.put("jobKind", job.getJobKind() == null ? "" : job.getJobKind().name());
        o.put("status", job.getStatus() == null ? "" : job.getStatus().name());
        o.put("mode", nz(job.getMode()));
        o.put("baseUrl", nz(job.getBaseUrl()));
        o.put("createdAt", job.getCreatedAt() == null ? "" : job.getCreatedAt().toString());
        o.put("completedAt", job.getCompletedAt() == null ? "" : job.getCompletedAt().toString());
        if (job.getCreatedAt() != null && job.getCompletedAt() != null) {
            o.put("durationSeconds", Duration.between(job.getCreatedAt(), job.getCompletedAt()).toSeconds());
        }
        o.put("passed", job.getPassedCount());
        o.put("todo", job.getTodoCount());
        o.put("progressCurrent", job.getProgressCurrent());
        o.put("progressTotal", job.getProgressTotal());
        o.put("error", nz(job.getError()));
        o.put("message", nz(job.getMessage()));
        o.put("retryCategory", classify(job.getError(), job.getMessage()).name());
        try {
            Map<String, Object> diag = JobDiagnostics.describe(job, false, false);
            o.put("diagnostics", new JSONObject(diag));
        } catch (RuntimeException ignored) {
            // diagnostics are best effort
        }
        return o;
    }

    private Map<String, Object> summary(RefundRequest r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("jobId", r.getJobId());
        m.put("projectId", nz(r.getProjectId()));
        m.put("projectName", projects.findByProjectId(nz(r.getProjectId())).map(ProjectEntity::getName).orElse(""));
        m.put("userId", r.getUserId());
        m.put("userEmail", users.findById(r.getUserId()).map(PortalUser::getEmail).orElse(""));
        m.put("jobKind", nz(r.getJobKind()));
        m.put("chargedAmount", r.getChargedAmount());
        m.put("refundedAmount", r.getRefundedAmount());
        m.put("status", r.getStatus());
        m.put("source", r.getSource());
        m.put("failureCategory", nz(r.getFailureCategory()));
        m.put("failureError", nz(r.getFailureError()));
        m.put("failureMessage", nz(r.getFailureMessage()));
        m.put("userNote", nz(r.getUserNote()));
        m.put("adminNote", nz(r.getAdminNote()));
        m.put("createdAt", r.getCreatedAt() == null ? "" : r.getCreatedAt().toString());
        m.put("decidedAt", r.getDecidedAt() == null ? "" : r.getDecidedAt().toString());
        return m;
    }

    private Map<String, Object> adminDetail(RefundRequest r) {
        Map<String, Object> m = summary(r);
        m.put("decidedBy", r.getDecidedBy() == null ? "" :
                users.findById(r.getDecidedBy()).map(PortalUser::getEmail).orElse(String.valueOf(r.getDecidedBy())));
        Map<String, Object> evidence = Map.of();
        try {
            if (r.getEvidenceJson() != null && !r.getEvidenceJson().isBlank()) {
                evidence = new JSONObject(r.getEvidenceJson()).toMap();
            }
        } catch (RuntimeException ignored) {
            evidence = Map.of("raw", r.getEvidenceJson());
        }
        m.put("evidence", evidence);
        // Live view (job may have been re-run / user balance moved since the request).
        Optional<JobRecord> job = store.getJob(r.getJobId());
        m.put("jobStillExists", job.isPresent());
        job.ifPresent(j -> m.put("currentJobStatus", j.getStatus().name()));
        List<Map<String, Object>> ledger = new ArrayList<>();
        for (CreditLedgerEntry e : credits.ledgerForJob(r.getJobId())) {
            ledger.add(CreditService.ledgerDto(e));
        }
        m.put("ledger", ledger);
        m.put("userBalance", credits.userCreditsDto(r.getUserId()).get("balance"));
        m.put("chargedNow", credits.chargedForJob(r.getJobId()));
        Map<String, String> links = new LinkedHashMap<>();
        links.put("run", "/status?jobId=" + r.getJobId());
        if ("EXECUTE".equals(r.getJobKind())) {
            links.put("evidence", "/evidence?jobId=" + r.getJobId());
            if (r.getProjectId() != null) {
                links.put("failurePack", "/api/projects/" + r.getProjectId() + "/jobs/" + r.getJobId() + "/failure-pack");
            }
        }
        m.put("links", links);
        m.put("recommendation", recommendation(r));
        return m;
    }

    private Map<String, Object> userView(RefundRequest r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("jobId", r.getJobId());
        m.put("status", r.getStatus());
        m.put("source", r.getSource());
        m.put("chargedAmount", r.getChargedAmount());
        m.put("refundedAmount", r.getRefundedAmount());
        m.put("adminNote", nz(r.getAdminNote()));
        m.put("createdAt", r.getCreatedAt() == null ? "" : r.getCreatedAt().toString());
        m.put("decidedAt", r.getDecidedAt() == null ? "" : r.getDecidedAt().toString());
        return m;
    }

    /** Plain-language hint for the admin; the decision stays theirs. */
    static String recommendation(RefundRequest r) {
        String cat = nz(r.getFailureCategory());
        return switch (cat) {
            case "INFRA" -> "Platform failure (browser, network, lease or model provider). Usually refund in full.";
            case "CONFIG" -> "Configuration problem on the customer side (credentials, base URL, missing secret). "
                    + "Usually not refunded; consider a partial refund for first-time setup mistakes.";
            case "RECOVERY" -> "Engine page recovery consumed the run. Review the evidence; partial refund is common.";
            default -> "Test-level failure (locator, assertion, bind). The product did its job; review the user's note.";
        };
    }

    private static String clip(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
