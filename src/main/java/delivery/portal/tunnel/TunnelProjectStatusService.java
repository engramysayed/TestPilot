package delivery.portal.tunnel;

import delivery.portal.service.PortalStore;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class TunnelProjectStatusService {

    private final PortalStore store;
    private final Environment env;
    private final TunnelJobBaseUrlService jobBaseUrl;
    private final TunnelEnrollmentDiskStore disk = new TunnelEnrollmentDiskStore();

    public TunnelProjectStatusService(
            PortalStore store,
            Environment env,
            TunnelJobBaseUrlService jobBaseUrl
    ) {
        this.store = store;
        this.env = env;
        this.jobBaseUrl = jobBaseUrl;
    }

    public Map<String, Object> badge(String projectId) {
        Map<String, Object> out = new LinkedHashMap<>();
        boolean enabled = env.getProperty("delivery.tunnel.enabled", Boolean.class, Boolean.FALSE);
        out.put("enabled", enabled);
        if (!enabled) {
            out.put("status", "disabled");
            out.put("label", "Tunnel inactive");
            out.put("hint", tunnelHint("disabled"));
            return out;
        }
        TunnelHostPolicy.Evaluation iso = TunnelHostPolicy.evaluate(
                env.getProperty("delivery.public-base-url", ""),
                env.getProperty("delivery.tunnel.public-base-url", ""));
        out.put("tunnelHost", iso.tunnelHost());
        out.put("isolation", iso.misconfigured() ? "misconfigured" : iso.devLoopback() ? "dev-loopback" : "dedicated-host");
        if (iso.misconfigured()) {
            out.put("status", "misconfigured");
            out.put("label", "Tunnel unavailable");
            out.put("hint", tunnelHint("misconfigured"));
            return out;
        }
        Optional<Map<String, Object>> latest = disk.latest(store.projectDiskRoot(projectId));
        if (latest.isEmpty()) {
            out.put("status", "none");
            out.put("label", "Tunnel not connected");
            out.put("hint", tunnelHint("none"));
            return out;
        }
        Map<String, Object> e = latest.get();
        String st = TunnelEnrollmentDiskStore.effectiveStatus(e, jobBaseUrl.heartbeatTtl());
        out.put("status", st);
        out.put("token", e.getOrDefault("token", ""));
        out.put("listenUrl", e.getOrDefault("listenUrl", ""));
        jobBaseUrl.relayBaseUrl(projectId).ifPresent(u -> out.put("executeBaseUrl", u));
        out.put("lastHeartbeatAt", e.getOrDefault("lastHeartbeatAt", ""));
        out.put("label", "active".equalsIgnoreCase(st)
                ? "Tunnel active"
                : "stale".equalsIgnoreCase(st)
                ? "Tunnel offline"
                : "Tunnel " + st);
        if ("active".equalsIgnoreCase(st)) {
            out.put("hint", "Execute runs use the tunnel relay instead of the project URL.");
        } else {
            out.put("hint", tunnelHint(st));
        }
        return out;
    }

    /** Short hint for Execute UI when runs fall back to the project URL (no JVM jargon). */
    public static String tunnelHint(String status) {
        if (status == null) {
            return "";
        }
        return switch (status.trim().toLowerCase()) {
            case "disabled" -> "Your admin can turn on the private app tunnel for localhost and VPN apps.";
            case "none" -> "On your machine, run keel tunnel start for this project — see the Guide.";
            case "stale" -> "No recent agent heartbeat — runs use the project URL until the tunnel is back.";
            case "misconfigured" -> "Ask your admin to set a dedicated tunnel web address (separate from Keel).";
            default -> "";
        };
    }
}
