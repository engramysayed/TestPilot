package delivery.portal.tunnel;

import delivery.portal.service.PortalStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

/** When a project tunnel is active, execute jobs use the portal relay URL instead of static baseUrl. */
@Service
public class TunnelJobBaseUrlService {

    private final PortalStore store;
    private final TunnelEnrollmentDiskStore disk = new TunnelEnrollmentDiskStore();

    @Value("${delivery.tunnel.enabled:false}")
    private boolean tunnelEnabled;

    @Value("${delivery.tunnel.public-base-url:}")
    private String publicBaseUrl;

    @Value("${delivery.public-base-url:}")
    private String portalBaseUrl = "";

    /** Heartbeats arrive every 15s from {@code keel tunnel start}; older than this = agent gone. */
    @Value("${delivery.tunnel.heartbeat-ttl-seconds:60}")
    private long heartbeatTtlSeconds = 60;

    public TunnelJobBaseUrlService(PortalStore store) {
        this.store = store;
    }

    public Optional<String> relayBaseUrl(String projectId) {
        if (!tunnelEnabled || projectId == null || projectId.isBlank()) {
            return Optional.empty();
        }
        String pub = publicBaseUrl == null ? "" : publicBaseUrl.trim();
        if (pub.isBlank()) {
            return Optional.empty();
        }
        if (!TunnelHostPolicy.evaluate(portalBaseUrl, pub).relayAllowed()) {
            // Tunnel host == portal host: relay is refused (origin isolation), so never route jobs to it.
            return Optional.empty();
        }
        Optional<Map<String, Object>> latest = disk.latest(store.projectDiskRoot(projectId));
        if (latest.isEmpty()) {
            return Optional.empty();
        }
        Map<String, Object> e = latest.get();
        if (!TunnelEnrollmentDiskStore.isLive(e, heartbeatTtl())) {
            return Optional.empty();
        }
        String token = String.valueOf(e.getOrDefault("token", "")).trim();
        if (token.isBlank()) {
            return Optional.empty();
        }
        String base = pub.endsWith("/") ? pub.substring(0, pub.length() - 1) : pub;
        return Optional.of(base + "/tunnel/p/" + token + "/");
    }

    public java.time.Duration heartbeatTtl() {
        return java.time.Duration.ofSeconds(heartbeatTtlSeconds <= 0 ? 60 : heartbeatTtlSeconds);
    }

    public String effectiveBaseUrl(String projectId, String projectBaseUrl) {
        return relayBaseUrl(projectId).orElse(projectBaseUrl == null ? "" : projectBaseUrl);
    }
}
