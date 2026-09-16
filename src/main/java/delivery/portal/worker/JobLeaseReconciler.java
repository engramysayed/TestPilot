package delivery.portal.worker;

import delivery.portal.service.PortalStore;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Reclaim or mark-uncertain expired RUNNING leases after process restart.
 */
@Component
@Order(40)
public class JobLeaseReconciler implements ApplicationRunner {
    private final PortalStore portalStore;

    public JobLeaseReconciler(PortalStore portalStore) {
        this.portalStore = portalStore;
    }

    @Override
    public void run(ApplicationArguments args) {
        portalStore.reconcileExpiredLeases();
    }
}
