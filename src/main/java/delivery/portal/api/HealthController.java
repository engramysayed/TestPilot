package delivery.portal.api;

import delivery.portal.DeliveryPortalProperties;
import delivery.portal.service.PortalStore;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class HealthController {
    private final DeliveryPortalProperties props;
    private final PortalStore store;
    private final Environment env;

    public HealthController(DeliveryPortalProperties props, PortalStore store, Environment env) {
        this.props = props;
        this.store = store;
        this.env = env;
    }

    @GetMapping("/api/health")
    public Map<String, Object> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "UP");
        body.put("installMode", env.getProperty("delivery.install.mode", "shared"));
        body.put("production", Boolean.parseBoolean(env.getProperty("delivery.install.production", "false")));
        return body;
    }

    @GetMapping("/api/ready")
    public ResponseEntity<Map<String, Object>> ready() {
        Map<String, Object> body = new LinkedHashMap<>();
        Path root = Path.of(props.getStoreRoot());
        boolean writable = false;
        try {
            Files.createDirectories(root);
            Path probe = root.resolve(".ready-probe");
            Files.writeString(probe, "ok");
            Files.deleteIfExists(probe);
            writable = true;
        } catch (Exception ignored) {
            writable = false;
        }
        Map<String, Integer> jobs = store.jobStatusCounts();
        body.put("storeWritable", writable);
        body.put("jobs", jobs);
        body.put("status", writable ? "READY" : "NOT_READY");
        if (!writable) {
            return ResponseEntity.status(503).body(body);
        }
        return ResponseEntity.ok(body);
    }
}
