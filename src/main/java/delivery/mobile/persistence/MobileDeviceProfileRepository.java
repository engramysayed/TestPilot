package delivery.mobile.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MobileDeviceProfileRepository extends JpaRepository<MobileDeviceProfileEntity, Long> {
    Optional<MobileDeviceProfileEntity> findByProfileRevisionId(String profileRevisionId);
    List<MobileDeviceProfileEntity> findByTenantIdAndProjectId(String tenantId, String projectId);
}
