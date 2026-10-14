package delivery.portal.alerts;

import delivery.portal.service.PortalStore;
import org.springframework.stereotype.Component;

import java.util.List;

/** Reads {@code lastHeartbeat} per enrolled engine, as written by claim / lease extension / v1 heartbeat. */
@Component
public class PortalEngineHeartbeats implements EngineHeartbeats {
    private final PortalStore store;

    public PortalEngineHeartbeats(PortalStore store) {
        this.store = store;
    }

    @Override
    public List<Engine> engines() throws Exception {
        return store.directory().allRunners().stream()
                .map(r -> new Engine(r.id(), r.label(), r.enrolledAt(), r.lastHeartbeat(), r.revokedAt() != null))
                .toList();
    }
}
