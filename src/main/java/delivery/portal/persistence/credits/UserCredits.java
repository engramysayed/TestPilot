package delivery.portal.persistence.credits;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

/** User-level Keel Credits balance (not project BudgetLedger). */
@Entity
@Table(name = "user_credits")
public class UserCredits {
    @Id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private long balance = 0L;

    @Column(name = "lifetime_granted", nullable = false)
    private long lifetimeGranted = 0L;

    @Column(name = "lifetime_spent", nullable = false)
    private long lifetimeSpent = 0L;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Version
    @Column(nullable = false)
    private long version = 0L;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public long getBalance() { return balance; }
    public void setBalance(long balance) { this.balance = balance; }
    public long getLifetimeGranted() { return lifetimeGranted; }
    public void setLifetimeGranted(long lifetimeGranted) { this.lifetimeGranted = lifetimeGranted; }
    public long getLifetimeSpent() { return lifetimeSpent; }
    public void setLifetimeSpent(long lifetimeSpent) { this.lifetimeSpent = lifetimeSpent; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public long getVersion() { return version; }
}
