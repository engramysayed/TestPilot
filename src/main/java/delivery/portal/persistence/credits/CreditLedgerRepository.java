package delivery.portal.persistence.credits;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface CreditLedgerRepository extends JpaRepository<CreditLedgerEntry, Long> {

    Optional<CreditLedgerEntry> findByIdempotencyKey(String idempotencyKey);

    List<CreditLedgerEntry> findByUserIdOrderByCreatedAtDesc(Long userId);

    /** Every ledger row for one job (debits, add-on debits, refunds). */
    List<CreditLedgerEntry> findByJobIdOrderByCreatedAtAsc(String jobId);

    @Query("""
            select e from CreditLedgerEntry e
            where e.userId = :userId
              and (:from is null or e.createdAt >= :from)
              and (:to is null or e.createdAt <= :to)
              and (:jobKind is null or e.jobKind = :jobKind)
            order by e.createdAt desc
            """)
    List<CreditLedgerEntry> findFiltered(
            @Param("userId") Long userId,
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("jobKind") String jobKind);
}
