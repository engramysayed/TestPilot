package delivery.portal.api;

import delivery.portal.service.PortalStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/ops")
public class AdminOpsController {
    private final PortalStore store;

    public AdminOpsController(PortalStore store) {
        this.store = store;
    }

    @GetMapping("/metrics")
    public Map<String, Object> metrics() {
        Map<String, Object> body = new LinkedHashMap<>();
        Map<String, Integer> counts = store.jobStatusCounts();
        body.put("jobsByStatus", counts);
        body.put("queued", counts.getOrDefault("QUEUED", 0));
        body.put("running", counts.getOrDefault("RUNNING", 0));
        body.put("cancelling", counts.getOrDefault("CANCELLING", 0));
        body.put("interruptedUncertain", counts.getOrDefault("FAILED", 0));
        body.put("runbook", Map.of(
                "stuckLeases", "Restart the portal so JobLeaseReconciler reclaims expired RUNNING leases.",
                "isolationErrors", "Do not treat application-layer navigation checks as worker-network isolation.",
                "storagePressure", "Check delivery-store free space and retention sweeper logs.",
                "subprocessTimeouts", "Inspect ProcessSupervisor deadlines; force-stop CANCELLING jobs if the worker is gone.",
                "budgetExhaustion", "Precision max-calls snapshot is frozen at admit; raise the project cap before the next job."
        ));
        return body;
    }
}
