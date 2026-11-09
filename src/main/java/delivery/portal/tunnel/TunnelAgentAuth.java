package delivery.portal.tunnel;

import delivery.portal.service.PortalStore;

import java.util.Map;
import java.util.Optional;

public final class TunnelAgentAuth {
    private static final TunnelEnrollmentDiskStore DISK = new TunnelEnrollmentDiskStore();

    private TunnelAgentAuth() {
    }

    public static boolean verify(PortalStore store, String projectId, String token, String agentSecret) {
        if (token == null || token.isBlank() || agentSecret == null || agentSecret.isBlank()) {
            return false;
        }
        Optional<Map<String, Object>> doc = DISK.latest(store.projectDiskRoot(projectId));
        if (doc.isEmpty()) {
            return false;
        }
        Map<String, Object> e = doc.get();
        if (!token.equals(String.valueOf(e.getOrDefault("token", "")))) {
            return false;
        }
        return agentSecret.equals(String.valueOf(e.getOrDefault("agentSecret", "")));
    }
}
