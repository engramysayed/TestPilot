package delivery.portal.persistence.credits;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** One billable service. The price applies to every account. */
@Entity
@Table(name = "service_prices")
public class ServicePrice {
    @Id
    @Column(nullable = false, length = 32)
    private String code;

    @Column(nullable = false, length = 80)
    private String label;

    @Column(nullable = false)
    private long cost;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public long getCost() { return cost; }
    public void setCost(long cost) { this.cost = cost; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
