package delivery.portal.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectCredentialRepository extends JpaRepository<ProjectCredentialEntity, Long> {
    List<ProjectCredentialEntity> findByProjectIdOrderByProfileNameAsc(String projectId);
    Optional<ProjectCredentialEntity> findByProjectIdAndProfileName(String projectId, String profileName);
    void deleteByProjectIdAndProfileName(String projectId, String profileName);
}
