package delivery.portal.api.v1;

import delivery.mobile.run.MobileRunOrchestrator;
import delivery.portal.api.ApiError;
import delivery.portal.security.PrivateRunnerFilter;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Mobile runner wire (bearer runner token, see {@link PrivateRunnerFilter}). The runner is
 * {@code delivery.mobile.runner.agent.MobileRunnerAgent} in the mobile-runner module.
 */
@RestController
@RequestMapping("/api/v1/runners/mobile")
public class MobileRunnerApiController {

    private final MobileRunOrchestrator runs;

    public MobileRunnerApiController(MobileRunOrchestrator runs) {
        this.runs = runs;
    }

    @PostMapping(value = "/claim", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> claim() {
        PrivateRunnerFilter.RunnerToken token = token();
        if (token == null) return unauthorized();
        return runs.claim(token.enrollment().tenant().value(), token.enrollment().id())
                .<ResponseEntity<?>>map(p -> ResponseEntity.ok(p.toString()))
                .orElse(ResponseEntity.noContent().build());
    }

    @PostMapping(value = "/runs/{runId}/heartbeat", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> heartbeat(@PathVariable String runId, @RequestBody(required = false) String body) {
        PrivateRunnerFilter.RunnerToken token = token();
        if (token == null) return unauthorized();
        String progress = body == null || body.isBlank() ? "" : new JSONObject(body).optString("progress", "");
        try {
            return ResponseEntity.ok(runs.runnerHeartbeat(runId, token.enrollment().tenant().value(), progress).toString());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(new ApiError("NOT_FOUND", e.getMessage()).asMap());
        }
    }

    @PostMapping(value = "/runs/{runId}/result", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> result(@PathVariable String runId, @RequestBody String body) {
        PrivateRunnerFilter.RunnerToken token = token();
        if (token == null) return unauthorized();
        JSONObject o = new JSONObject(body == null || body.isBlank() ? "{}" : body);
        JSONArray results = o.optJSONArray("results");
        try {
            runs.runnerResult(runId, token.enrollment().tenant().value(), results, o.optString("error", ""));
            return ResponseEntity.ok(new JSONObject().put("ok", true).toString());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(new ApiError("NOT_FOUND", e.getMessage()).asMap());
        }
    }

    private static PrivateRunnerFilter.RunnerToken token() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth instanceof PrivateRunnerFilter.RunnerToken t ? t : null;
    }

    private static ResponseEntity<?> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiError("UNAUTHORIZED", "runner token required").asMap());
    }
}
