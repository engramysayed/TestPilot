package delivery.mobile.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MobileDeviceRepository extends JpaRepository<MobileDeviceEntity, Long> {
    Optional<MobileDeviceEntity> findByDeviceId(String deviceId);
    List<MobileDeviceEntity> findByTenantId(String tenantId);
    Optional<MobileDeviceEntity> findByHostIdAndSerialFingerprint(String hostId, String serialFingerprint);
}
