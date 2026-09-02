package delivery.portal.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface JobRepository extends JpaRepository<JobEntity, Long> {
    Optional<JobEntity> findByJobId(String jobId);
    List<JobEntity> findByOwnerUserIdOrderByCreatedAtDesc(Long ownerUserId);
    long countByOwnerUserId(Long ownerUserId);
    long countByOwnerUserIdAndStatus(Long ownerUserId, String status);
    void deleteByOwnerUserId(Long ownerUserId);

    void deleteByProjectId(String projectId);

    List<JobEntity> findByProjectId(String projectId);
}
