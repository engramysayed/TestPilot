package delivery.portal.api;

import delivery.portal.service.PortalStore;
import delivery.portal.tunnel.TunnelAgentAuth;
import delivery.portal.tunnel.TunnelRelayService;
import delivery.portal.tunnel.TunnelTokenIndex;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/tunnel/agent")
@ConditionalOnProperty(name = "delivery.tunnel.enabled", havingValue = "true")
public class TunnelAgentController {

    private final PortalStore store;
    private final TunnelRelayService relay;

    public TunnelAgentController(PortalStore store, TunnelRelayService relay) {
        this.store = store;
        this.relay = relay;
    }

    public record PollBody(String token, String agentSecret, String projectId, Integer waitMs) {
    }

    public record CompleteBody(
            String token,
            String agentSecret,
            String projectId,
            String requestId,
            Integer status,
            String bodyBase64,
            Map<String, String> headers,
            /** Preferred: every value per header (e.g. several Set-Cookie). Falls back to {@code headers}. */
            Map<String, java.util.List<String>> multiHeaders
    ) {
    }

    @PostMapping(path = "/poll", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> poll(@RequestBody(required = false) PollBody body) throws Exception {
        if (body == null || !authorized(body.token(), body.agentSecret(), body.projectId())) {
            return ResponseEntity.status(401).body(new ApiError("UNAUTHORIZED", "Invalid tunnel agent").asMap());
        }
        int wait = body.waitMs() == null ? 25_000 : Math.min(60_000, Math.max(1_000, body.waitMs()));
        Optional<TunnelRelayService.RelayRequest> req =
                relay.poll(body.token().trim(), Duration.ofMillis(wait));
        if (req.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(req.get().toAgentJson());
    }

    @PostMapping(path = "/complete", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> complete(@RequestBody(required = false) CompleteBody body) {
        if (body == null || !authorized(body.token(), body.agentSecret(), body.projectId())) {
            return ResponseEntity.status(401).body(new ApiError("UNAUTHORIZED", "Invalid tunnel agent").asMap());
        }
        String reqId = body.requestId() == null ? "" : body.requestId().trim();
        if (reqId.isBlank()) {
            return ResponseEntity.badRequest().body(new ApiError("MISSING_REQUEST_ID", "requestId required").asMap());
        }
        int status = body.status() == null ? 502 : body.status();
        byte[] raw = decodeBase64(body.bodyBase64());
        boolean ok;
        if (body.multiHeaders() != null && !body.multiHeaders().isEmpty()) {
            ok = relay.completeMulti(reqId, status, raw, body.multiHeaders());
        } else {
            Map<String, String> headers = body.headers() == null ? Map.of() : body.headers();
            ok = relay.complete(reqId, status, raw, headers);
        }
        if (!ok) {
            return ResponseEntity.status(404).body(new ApiError("NOT_FOUND", "Unknown requestId").asMap());
        }
        return ResponseEntity.ok(Map.of("ok", true));
    }

    private boolean authorized(String token, String agentSecret, String projectId) {
        if (token == null || token.isBlank() || agentSecret == null || agentSecret.isBlank()) {
            return false;
        }
        String pid = projectId == null ? "" : projectId.trim();
        if (pid.isBlank()) {
            pid = TunnelTokenIndex.projectIdForToken(store.storeRoot(), token.trim()).orElse("");
        }
        if (pid.isBlank()) {
            return false;
        }
        return TunnelAgentAuth.verify(store, pid, token.trim(), agentSecret.trim());
    }

    private static byte[] decodeBase64(String b64) {
        if (b64 == null || b64.isBlank()) {
            return new byte[0];
        }
        return Base64.getDecoder().decode(b64.trim());
    }
}
