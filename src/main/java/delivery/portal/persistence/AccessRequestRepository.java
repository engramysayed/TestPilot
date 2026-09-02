package delivery.portal.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AccessRequestRepository extends JpaRepository<AccessRequest, Long> {
    List<AccessRequest> findByStatusOrderByCreatedAtDesc(AccessRequest.Status status);

    Optional<AccessRequest> findFirstByEmailIgnoreCaseAndStatus(String email, AccessRequest.Status status);

    boolean existsByEmailIgnoreCaseAndStatus(String email, AccessRequest.Status status);
}
