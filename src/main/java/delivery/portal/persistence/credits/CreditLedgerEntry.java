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

/** Append-only Keel Credits ledger. Never update/delete in product paths. */
@Entity
@Table(name = "credit_ledger",
        uniqueConstraints = @UniqueConstraint(name = "uk_credit_ledger_idempotency", columnNames = "idempotency_key"),
        indexes = {
                @Index(name = "idx_credit_ledger_user_created", columnList = "user_id,created_at"),
                @Index(name = "idx_credit_ledger_created", columnList = "created_at")
        })
public class CreditLedgerEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private long delta;

    @Column(name = "balance_after", nullable = false)
    private long balanceAfter;

    @Column(nullable = false, length = 64)
    private String reason;

    @Column(name = "job_id", length = 64)
    private String jobId;

    @Column(name = "job_kind", length = 32)
    private String jobKind;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(length = 512)
    private String note;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public long getDelta() { return delta; }
    public void setDelta(long delta) { this.delta = delta; }
    public long getBalanceAfter() { return balanceAfter; }
    public void setBalanceAfter(long balanceAfter) { this.balanceAfter = balanceAfter; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }
    public String getJobKind() { return jobKind; }
    public void setJobKind(String jobKind) { this.jobKind = jobKind; }
    public Long getActorUserId() { return actorUserId; }
    public void setActorUserId(Long actorUserId) { this.actorUserId = actorUserId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
