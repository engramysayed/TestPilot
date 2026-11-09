package delivery.portal.persistence.credits;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * A request to return the credits charged for a failed job. One per job.
 * Created automatically when a charged job fails for a platform reason (infra/provider/lease), or by the
 * job owner from the run page. The account owner (head admin) approves or rejects it in Admin → Refunds;
 * approval writes an idempotent {@code JOB_REFUND} ledger entry.
 */
@Entity
@Table(name = "refund_requests",
        uniqueConstraints = @UniqueConstraint(name = "uk_refund_requests_job", columnNames = "job_id"),
        indexes = {
                @Index(name = "idx_refund_requests_status_created", columnList = "status,created_at"),
                @Index(name = "idx_refund_requests_user", columnList = "user_id")
        })
public class RefundRequest {

    public enum Status { PENDING, APPROVED, REJECTED }

    public enum Source { AUTO, USER }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_id", nullable = false, length = 64)
    private String jobId;

    @Column(name = "project_id", length = 64)
    private String projectId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "job_kind", length = 32)
    private String jobKind;

    /** Credits charged for the job (sum of its debit ledger entries, positive). */
    @Column(name = "charged_amount", nullable = false)
    private long chargedAmount;

    /** Credits actually returned on approval (may be partial). */
    @Column(name = "refunded_amount", nullable = false)
    private long refundedAmount;

    @Column(nullable = false, length = 16)
    private String status = Status.PENDING.name();

    @Column(nullable = false, length = 8)
    private String source = Source.AUTO.name();

    /** RetryCategory of the failure: INFRA / CONFIG / TEST / RECOVERY. */
    @Column(name = "failure_category", length = 16)
    private String failureCategory;

    @Column(name = "failure_error", length = 1024)
    private String failureError;

    @Column(name = "failure_message", length = 1024)
    private String failureMessage;

    @Column(name = "user_note", length = 1000)
    private String userNote;

    @Column(name = "admin_note", length = 1000)
    private String adminNote;

    @Column(name = "decided_by")
    private Long decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "refund_ledger_id")
    private Long refundLedgerId;

    /** JSON snapshot of the run at request time (timings, counts, stage, providers, debit breakdown…). */
    // varchar, not @Lob: Hibernate 6 maps @Lob String to a PostgreSQL large object (oid).
    @Column(name = "evidence_json", length = 32000)
    private String evidenceJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public String getJobId() { return jobId; }
    public void setJobId(String v) { this.jobId = v; }
    public String getProjectId() { return projectId; }
    public void setProjectId(String v) { this.projectId = v; }
    public Long getUserId() { return userId; }
    public void setUserId(Long v) { this.userId = v; }
    public String getJobKind() { return jobKind; }
    public void setJobKind(String v) { this.jobKind = v; }
    public long getChargedAmount() { return chargedAmount; }
    public void setChargedAmount(long v) { this.chargedAmount = v; }
    public long getRefundedAmount() { return refundedAmount; }
    public void setRefundedAmount(long v) { this.refundedAmount = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public String getSource() { return source; }
    public void setSource(String v) { this.source = v; }
    public String getFailureCategory() { return failureCategory; }
    public void setFailureCategory(String v) { this.failureCategory = v; }
    public String getFailureError() { return failureError; }
    public void setFailureError(String v) { this.failureError = v; }
    public String getFailureMessage() { return failureMessage; }
    public void setFailureMessage(String v) { this.failureMessage = v; }
    public String getUserNote() { return userNote; }
    public void setUserNote(String v) { this.userNote = v; }
    public String getAdminNote() { return adminNote; }
    public void setAdminNote(String v) { this.adminNote = v; }
    public Long getDecidedBy() { return decidedBy; }
    public void setDecidedBy(Long v) { this.decidedBy = v; }
    public Instant getDecidedAt() { return decidedAt; }
    public void setDecidedAt(Instant v) { this.decidedAt = v; }
    public Long getRefundLedgerId() { return refundLedgerId; }
    public void setRefundLedgerId(Long v) { this.refundLedgerId = v; }
    public String getEvidenceJson() { return evidenceJson; }
    public void setEvidenceJson(String v) { this.evidenceJson = v; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant v) { this.createdAt = v; }
}
