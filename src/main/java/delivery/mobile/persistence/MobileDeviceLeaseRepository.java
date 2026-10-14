package delivery.mobile.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MobileDeviceLeaseRepository extends JpaRepository<MobileDeviceLeaseEntity, Long> {
    Optional<MobileDeviceLeaseEntity> findByLeaseId(String leaseId);
    Optional<MobileDeviceLeaseEntity> findByDeviceId(String deviceId);
    List<MobileDeviceLeaseEntity> findByTenantId(String tenantId);
}
