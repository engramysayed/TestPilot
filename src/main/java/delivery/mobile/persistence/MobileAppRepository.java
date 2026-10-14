package delivery.mobile.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MobileAppRepository extends JpaRepository<MobileAppEntity, Long> {
    Optional<MobileAppEntity> findByAppId(String appId);
    List<MobileAppEntity> findByTenantIdAndProjectId(String tenantId, String projectId);
}
