package delivery.portal.worker;

import delivery.portal.service.PortalStore;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Reclaim or mark-uncertain expired RUNNING leases: once at startup and then on a timer.
 * Safe to run periodically because {@link delivery.portal.jobs.LeaseKeeper} renews every live job's lease;
 * only crashed or hung (past max runtime) workers let their lease lapse.
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

    @org.springframework.scheduling.annotation.Scheduled(
            initialDelayString = "${delivery.jobs.lease-reconcile-ms:60000}",
            fixedDelayString = "${delivery.jobs.lease-reconcile-ms:60000}")
    public void periodic() {
        int n = portalStore.reconcileExpiredLeases();
        if (n > 0) {
            org.slf4j.LoggerFactory.getLogger(JobLeaseReconciler.class)
                    .warn("Lease reconciler recovered {} job(s) with expired leases", n);
        }
    }
}
