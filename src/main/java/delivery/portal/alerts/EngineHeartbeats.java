package delivery.portal.alerts;

import java.time.Instant;
import java.util.List;

/** Source of engine heartbeats for the AL-1 dead-man rule (portal side: WorkspaceDirectory runner enrollments). */
@FunctionalInterface
public interface EngineHeartbeats {
    record Engine(String id, String label, Instant enrolledAt, Instant lastHeartbeat, boolean revoked) {
    }

    List<Engine> engines() throws Exception;
}
