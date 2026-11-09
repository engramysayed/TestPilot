package delivery.portal.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

/** Pins a structured plan snapshot onto an execute job (A3). */
@Service
public class StructuredPlanJobBootstrap {
    public static final String SNAPSHOT = "structured-plan-snapshot.json";

    private final PortalStore store;
    private final StructuredPlanService plans;
    private final ObjectMapper mapper = new ObjectMapper();

    public StructuredPlanJobBootstrap(PortalStore store, StructuredPlanService plans) {
        this.store = store;
        this.plans = plans;
    }

    public void attachPlanSnapshot(String projectId, String jobId, String structuredPlanId) {
        if (structuredPlanId == null || structuredPlanId.isBlank()) {
            return;
        }
        try {
            Optional<Map<String, Object>> plan = plans.get(projectId, structuredPlanId.trim());
            if (plan.isEmpty()) {
                return;
            }
            Path jobDir = store.projectDiskRoot(projectId).resolve("execute-runs").resolve(jobId.trim());
            Files.createDirectories(jobDir);
            Map<String, Object> snap = plan.get();
            snap.put("pinnedAt", java.time.Instant.now().toString());
            snap.put("jobId", jobId);
            mapper.writerWithDefaultPrettyPrinter().writeValue(jobDir.resolve(SNAPSHOT).toFile(), snap);
        } catch (Exception ignored) {
        }
    }
}
