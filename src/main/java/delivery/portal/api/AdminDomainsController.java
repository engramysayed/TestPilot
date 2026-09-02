package delivery.portal.api;

import delivery.portal.service.AdminDomainService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/domains")
public class AdminDomainsController {
    private static final Logger log = LoggerFactory.getLogger(AdminDomainsController.class);
    private final AdminDomainService domains;

    public AdminDomainsController(AdminDomainService domains) {
        this.domains = domains;
    }

    @GetMapping
    public Map<String, Object> list() throws Exception {
        Map<String, Object> out = new HashMap<>();
        out.put("domains", domains.listDomains());
        return out;
    }

    @PostMapping("/migrate-legacy")
    public Map<String, Object> migrateLegacy() throws Exception {
        List<String> moved = domains.migrateLegacyFlatProjects();
        Map<String, Object> out = new HashMap<>();
        out.put("moved", moved);
        out.put("count", moved.size());
        return out;
    }

    @GetMapping("/{domain}")
    public ResponseEntity<?> get(@PathVariable("domain") String domain) {
        try {
            return ResponseEntity.ok(domains.getDomain(domain));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to load domain {}", domain, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Could not load domain details"));
        }
    }

    @DeleteMapping("/{domain}")
    public ResponseEntity<?> delete(@PathVariable("domain") String domain,
                                    @RequestBody Map<String, String> body) {
        try {
            String confirm = body == null ? "" : body.getOrDefault("confirm", "");
            Map<String, Object> result = domains.deleteDomain(domain, confirm);
            result.put("deleted", true);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to delete domain {}", domain, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Could not delete domain folder"));
        }
    }
}
