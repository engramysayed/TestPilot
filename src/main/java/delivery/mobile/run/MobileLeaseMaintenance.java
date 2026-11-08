package delivery.mobile.run;

import delivery.mobile.MobileProperties;
import delivery.mobile.appium.SupervisedAppiumLifecycle;
import delivery.mobile.lease.DeviceLeaseGuard;
import delivery.mobile.lease.LeaseState;
import delivery.mobile.persistence.MobileDeviceLeaseEntity;
import delivery.mobile.persistence.MobileDeviceLeaseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

/**
 * Frees devices that nothing will ever release (previously {@link DeviceLeaseGuard#reconcile} had no caller):
 * <ul>
 *   <li>RESERVED without a session and expired → device AVAILABLE (abandoned admission).</li>
 *   <li>ACTIVE and expired (no live run renews it) → QUARANTINED; its owned Appium process is stopped.</li>
 *   <li>QUARANTINED / RELEASING older than the grace period, with no owned Appium process alive in this JVM →
 *       proof that the old session/process cannot act → device AVAILABLE.</li>
 * </ul>
 * Leases owned by a live {@link MobileRunOrchestrator} run are never touched here (the orchestrator renews them).
 */
@Component
public class MobileLeaseMaintenance {

    private static final Logger log = LoggerFactory.getLogger(MobileLeaseMaintenance.class);
    static final Duration QUARANTINE_GRACE = Duration.ofMinutes(2);

    private final MobileProperties props;
    private final MobileDeviceLeaseRepository leaseRepo;
    private final DeviceLeaseGuard guard;
    private final SupervisedAppiumLifecycle appium;
    private final MobileRunOrchestrator runs;

    public MobileLeaseMaintenance(MobileProperties props,
                                  MobileDeviceLeaseRepository leaseRepo,
                                  DeviceLeaseGuard guard,
                                  SupervisedAppiumLifecycle appium,
                                  MobileRunOrchestrator runs) {
        this.props = props;
        this.leaseRepo = leaseRepo;
        this.guard = guard;
        this.appium = appium;
        this.runs = runs;
    }

    @Scheduled(initialDelayString = "${delivery.mobile.lease-maintenance-ms:30000}",
            fixedDelayString = "${delivery.mobile.lease-maintenance-ms:30000}")
    public void run() {
        if (!props.isEnabled()) {
            return;
        }
        int freed = sweep(Instant.now());
        if (freed > 0) {
            log.info("Mobile lease maintenance changed {} lease(s)", freed);
        }
    }

    int sweep(Instant now) {
        Set<String> live = runs.liveLeaseIds();
        int changed = 0;
        // Only RESERVED/ACTIVE leases ever need reconciliation; FREE/closed ones are a waste
        // of a scan (used to load the whole lease table every tick).
        java.util.List<String> live2 = java.util.List.of(LeaseState.RESERVED.name(), LeaseState.ACTIVE.name());
        for (MobileDeviceLeaseEntity lease : leaseRepo.findByStateIn(live2)) {
            String id = lease.getLeaseId();
            if (live.contains(id)) {
                continue;
            }
            String state = lease.getState();
            boolean expired = lease.getExpiresAt() != null && !now.isBefore(lease.getExpiresAt());
            try {
                if ((LeaseState.RESERVED.name().equals(state) || LeaseState.ACTIVE.name().equals(state)) && expired) {
                    if (LeaseState.ACTIVE.name().equals(state)) {
                        // stop the owned Appium process first; quarantine marks the device
                        appium.quarantine(id, lease.getFenceToken(), "lease expired without heartbeat");
                    } else {
                        guard.reconcile(id, null);
                    }
                    changed++;
                    continue;
                }
                boolean parked = LeaseState.QUARANTINED.name().equals(state) || LeaseState.RELEASING.name().equals(state);
                Instant since = lease.getHeartbeatAt() == null ? lease.getCreatedAt() : lease.getHeartbeatAt();
                if (parked && since != null && now.isAfter(since.plus(QUARANTINE_GRACE))) {
                    boolean processGone = appium.getBinding(id) == null;
                    if (!processGone) {
                        appium.quarantine(id, lease.getFenceToken(), "reconcile: stopping owned Appium process");
                        processGone = appium.getBinding(id) == null;
                    }
                    if (processGone) {
                        guard.reconcile(id, DeviceLeaseGuard.QuarantineProof.cleared());
                        changed++;
                    }
                }
            } catch (RuntimeException e) {
                log.debug("Lease {} maintenance skipped: {}", id, e.getMessage());
            }
        }
        return changed;
    }
}
