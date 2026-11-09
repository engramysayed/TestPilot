package delivery.portal.persistence.credits;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RefundRequestRepository extends JpaRepository<RefundRequest, Long> {
    Optional<RefundRequest> findByJobId(String jobId);

    List<RefundRequest> findByStatusOrderByCreatedAtDesc(String status);

    List<RefundRequest> findAllByOrderByCreatedAtDesc();

    long countByStatus(String status);
}
