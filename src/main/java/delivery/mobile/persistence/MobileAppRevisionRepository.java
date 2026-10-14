package delivery.mobile.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MobileAppRevisionRepository extends JpaRepository<MobileAppRevisionEntity, Long> {
    Optional<MobileAppRevisionEntity> findByRevisionId(String revisionId);
    List<MobileAppRevisionEntity> findByAppId(String appId);
    List<MobileAppRevisionEntity> findByTenantIdAndProjectId(String tenantId, String projectId);
}
