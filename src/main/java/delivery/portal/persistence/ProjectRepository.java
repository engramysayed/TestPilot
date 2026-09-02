package delivery.portal.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectRepository extends JpaRepository<ProjectEntity, Long> {
    Optional<ProjectEntity> findByProjectId(String projectId);
    List<ProjectEntity> findByOwnerUserIdOrderByIdDesc(Long ownerUserId);
    List<ProjectEntity> findByOwnerUserIdAndArchivedOrderByIdDesc(Long ownerUserId, boolean archived);
    void deleteByOwnerUserId(Long ownerUserId);
}
