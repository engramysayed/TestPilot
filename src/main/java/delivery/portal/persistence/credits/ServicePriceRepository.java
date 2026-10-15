package delivery.portal.persistence.credits;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ServicePriceRepository extends JpaRepository<ServicePrice, String> {
    List<ServicePrice> findAllByOrderBySortOrderAsc();
}
