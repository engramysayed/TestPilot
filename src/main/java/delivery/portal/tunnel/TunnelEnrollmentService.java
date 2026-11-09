package delivery.portal.tunnel;

import delivery.portal.service.PortalStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A5 cloud tunnel enrollment when {@code delivery.tunnel.enabled=true}.
 * {@link TunnelRelayService} brokers HTTP; CLI agent polls and executes locally.
 * <p>
 * Enrollment is reusable: when the caller presents the current {@code token} + {@code agentSecret}
 * for the project, the same credentials are returned instead of minting new ones, so
 * {@code keel tunnel start} restarts keep a stable public relay URL.
 */
@Service
@ConditionalOnProperty(name = "delivery.tunnel.enabled", havingValue = "true")
public class TunnelEnrollmentService {

    private final Map<String, Enrollment> sessions = new ConcurrentHashMap<>();
    private final PortalStore store;
    private final TunnelEnrollmentDiskStore disk = new TunnelEnrollmentDiskStore();

    @Value("${delivery.tunnel.public-base-url:}")
    private String publicBaseUrl;

    private final TunnelRelayService relay;

    public TunnelEnrollmentService(
            PortalStore store,
            @org.springframework.beans.factory.annotation.Autowired(required = false) TunnelRelayService relay
    ) {
        this.store = store;
        this.relay = relay;
    }

    public Map<String, Object> enroll(String projectId, String requestedBy) {
        return enroll(projectId, requestedBy, null, null);
    }

    public Map<String, Object> enroll(String projectId, String requestedBy, String reuseToken, String reuseSecret) {
        String rt = reuseToken == null ? "" : reuseToken.trim();
        String rs = reuseSecret == null ? "" : reuseSecret.trim();
        if (!rt.isBlank() && !rs.isBlank() && TunnelAgentAuth.verify(store, projectId, rt, rs)) {
            disk.ensureIndex(store.storeRoot(), rt, projectId);
            String st = disk.latest(store.projectDiskRoot(projectId))
                    .map(d -> String.valueOf(d.getOrDefault("status", "pending")))
                    .orElse("pending");
            sessions.putIfAbsent(rt, new Enrollment(projectId, requestedBy, Instant.now(), st));
            return response(rt, rs, projectId, st, true);
        }
        disk.latest(store.projectDiskRoot(projectId))
                .map(d -> String.valueOf(d.getOrDefault("token", "")).trim())
                .filter(t -> !t.isBlank())
                .ifPresent(old -> {
                    sessions.remove(old);
                    if (relay != null) {
                        relay.forget(old);
                    }
                });
        String token = UUID.randomUUID().toString().replace("-", "");
        String agentSecret = UUID.randomUUID().toString().replace("-", "");
        Enrollment e = new Enrollment(projectId, requestedBy, Instant.now(), "pending");
        sessions.put(token, e);
        disk.save(store.projectDiskRoot(projectId), store.storeRoot(), token, projectId, requestedBy, agentSecret, e.status());
        return response(token, agentSecret, projectId, e.status(), false);
    }

    private Map<String, Object> response(String token, String agentSecret, String projectId, String status, boolean reused) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("token", token);
        out.put("agentSecret", agentSecret);
        out.put("projectId", projectId);
        out.put("status", status);
        out.put("reused", reused);
        out.put("publicBaseUrl", publicBaseUrl == null ? "" : publicBaseUrl.trim());
        out.put("message", "Run: keel tunnel start --project " + projectId);
        return out;
    }

    public Map<String, Object> status(String token) {
        Enrollment e = sessions.get(token);
        if (e == null) {
            // Survives portal restarts: fall back to the on-disk enrollment for this token.
            Optional<String> pid = TunnelTokenIndex.projectIdForToken(store.storeRoot(), token);
            if (pid.isPresent()) {
                Optional<Map<String, Object>> doc = disk.latest(store.projectDiskRoot(pid.get()));
                if (doc.isPresent() && token.equals(String.valueOf(doc.get().getOrDefault("token", "")))) {
                    return Map.of(
                            "token", token,
                            "projectId", pid.get(),
                            "status", String.valueOf(doc.get().getOrDefault("status", "pending")),
                            "enrolledAt", String.valueOf(doc.get().getOrDefault("enrolledAt", ""))
                    );
                }
            }
            return Map.of("token", token, "status", "unknown");
        }
        return Map.of(
                "token", token,
                "projectId", e.projectId(),
                "status", e.status(),
                "enrolledAt", e.enrolledAt().toString()
        );
    }

    private record Enrollment(String projectId, String requestedBy, Instant enrolledAt, String status) {
    }
}
