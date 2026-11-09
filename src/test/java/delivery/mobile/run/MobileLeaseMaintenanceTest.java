package delivery.mobile.run;

import delivery.mobile.MobileProperties;
import delivery.mobile.appium.SupervisedAppiumLifecycle;
import delivery.mobile.lease.DeviceLeaseGuard;
import delivery.mobile.persistence.MobileDeviceLeaseEntity;
import delivery.mobile.persistence.MobileDeviceLeaseRepository;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class MobileLeaseMaintenanceTest {

    private static MobileDeviceLeaseEntity lease(String id, String state, Instant expires, Instant hb) {
        MobileDeviceLeaseEntity e = new MobileDeviceLeaseEntity();
        e.setLeaseId(id);
        e.setState(state);
        e.setExpiresAt(expires);
        e.setHeartbeatAt(hb);
        e.setFenceToken(7);
        e.setCreatedAt(hb);
        return e;
    }

    @Test
    public void freesAbandonedReservedQuarantinesExpiredActiveAndReleasesOldQuarantine() {
        Instant now = Instant.now();
        MobileDeviceLeaseRepository repo = mock(MobileDeviceLeaseRepository.class);
        DeviceLeaseGuard guard = mock(DeviceLeaseGuard.class);
        SupervisedAppiumLifecycle appium = mock(SupervisedAppiumLifecycle.class);
        MobileRunOrchestrator runs = mock(MobileRunOrchestrator.class);
        when(runs.liveLeaseIds()).thenReturn(Set.of("live"));
        when(repo.findAll()).thenReturn(List.of(
                lease("reserved", "RESERVED", now.minusSeconds(5), now.minusSeconds(40)),
                lease("active", "ACTIVE", now.minusSeconds(5), now.minusSeconds(40)),
                lease("quarantined", "QUARANTINED", now.minusSeconds(600), now.minusSeconds(600)),
                lease("fresh-q", "QUARANTINED", now.minusSeconds(10), now.minusSeconds(10)),
                lease("live", "ACTIVE", now.minusSeconds(5), now.minusSeconds(40))));
        when(appium.getBinding(anyString())).thenReturn(null);

        MobileProperties props = new MobileProperties();
        int changed = new MobileLeaseMaintenance(props, repo, guard, appium, runs).sweep(now);

        Assert.assertEquals(changed, 3);
        verify(guard).reconcile(eq("reserved"), any());
        verify(appium).quarantine(eq("active"), anyLong(), anyString());
        verify(guard).reconcile(eq("quarantined"), eq(DeviceLeaseGuard.QuarantineProof.cleared()));
        verify(guard, never()).reconcile(eq("fresh-q"), any());
        verify(appium, never()).quarantine(eq("live"), anyLong(), anyString());
    }
}
