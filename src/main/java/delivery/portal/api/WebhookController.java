package delivery.portal.api;

import delivery.job.WebhookDestinationStore;
import delivery.portal.security.CurrentUserService;
import delivery.portal.service.PortalStore;
import org.json.JSONObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/projects/{projectId}/webhook")
public class WebhookController {
    private final PortalStore store;
    private final CurrentUserService currentUser;

    public WebhookController(PortalStore store, CurrentUserService currentUser) {
        this.store = store;
        this.currentUser = currentUser;
    }

    public record WebhookRequest(String url, String secret) {
    }

    @GetMapping
    public ResponseEntity<?> get(@PathVariable("projectId") String projectId) throws Exception {
        Long uid = currentUser.requireUserId();
        ResponseEntity<?> denied = ProjectAccess.denyUnlessReadable(store, projectId, uid);
        if (denied != null) {
            return denied;
        }
        JSONObject row = new WebhookDestinationStore(store.webhookFile(projectId)).describe();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("enabled", row.optBoolean("enabled"));
        body.put("url", row.optString("url"));
        body.put("secretConfigured", row.optBoolean("secretConfigured"));
        return ResponseEntity.ok(body);
    }

    @PutMapping
    public ResponseEntity<?> put(
            @PathVariable("projectId") String projectId,
            @RequestBody(required = false) WebhookRequest body
    ) throws Exception {
        Long uid = currentUser.requireUserId();
        ResponseEntity<?> denied = ProjectAccess.denyUnlessOperable(store, projectId, uid);
        if (denied != null) {
            return denied;
        }
        if (body == null) {
            return ResponseEntity.badRequest()
                    .body(new ApiError("BAD_REQUEST", "url and secret are required").asMap());
        }
        try {
            new WebhookDestinationStore(store.webhookFile(projectId)).put(body.url(), body.secret());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(new ApiError("BAD_REQUEST", e.getMessage()).asMap());
        }
        return get(projectId);
    }

    @DeleteMapping
    public ResponseEntity<?> delete(@PathVariable("projectId") String projectId) throws Exception {
        Long uid = currentUser.requireUserId();
        ResponseEntity<?> denied = ProjectAccess.denyUnlessOperable(store, projectId, uid);
        if (denied != null) {
            return denied;
        }
        new WebhookDestinationStore(store.webhookFile(projectId)).clear();
        return ResponseEntity.ok(Map.of("enabled", false, "url", "", "secretConfigured", false));
    }
}
