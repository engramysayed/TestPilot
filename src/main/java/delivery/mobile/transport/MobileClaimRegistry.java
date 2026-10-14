package delivery.mobile.transport;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** In-process registry of mobile claim snapshots keyed by jobId (attempt-scoped). */
@Component
public class MobileClaimRegistry {
    private final ConcurrentHashMap<String, MobileClaimSnapshot> byJob = new ConcurrentHashMap<>();

    public void put(MobileClaimSnapshot snapshot) {
        if (snapshot == null || snapshot.jobId() == null) {
            return;
        }
        byJob.put(snapshot.jobId(), snapshot);
    }

    public Optional<MobileClaimSnapshot> get(String jobId) {
        if (jobId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(byJob.get(jobId));
    }

    public void remove(String jobId) {
        if (jobId != null) {
            byJob.remove(jobId);
        }
    }

    public MobileClaimSnapshot requireSameTenant(String jobId, String tenantId) {
        MobileClaimSnapshot snap = get(jobId).orElse(null);
        if (snap == null || tenantId == null || !tenantId.equals(snap.tenantId())) {
            throw new MobileTransportException(MobileTransportErrorCode.CROSS_TENANT, "Unknown job");
        }
        return snap;
    }
}
