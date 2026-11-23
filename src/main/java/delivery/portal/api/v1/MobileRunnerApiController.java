package delivery.portal.api.v1;

import delivery.mobile.run.MobilePrecisionBindService;
import delivery.mobile.run.MobileRunOrchestrator;
import delivery.mobile.runner.precision.PrecisionOutcome;
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

import java.util.ArrayList;
import java.util.List;

/**
 * Mobile runner wire (bearer runner token, see {@link PrivateRunnerFilter}). The runner is
 * {@code delivery.mobile.runner.agent.MobileRunnerAgent} in the mobile-runner module.
 */
@RestController
@RequestMapping("/api/v1/runners/mobile")
public class MobileRunnerApiController {

    private final MobileRunOrchestrator runs;
    private final MobilePrecisionBindService precision;

    public MobileRunnerApiController(MobileRunOrchestrator runs, MobilePrecisionBindService precision) {
        this.runs = runs;
        this.precision = precision;
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

    @PostMapping(value = "/runs/{runId}/cases/{tcId}/steps/{stepIndex}/image",
            consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> stepImage(
            @PathVariable String runId,
            @PathVariable String tcId,
            @PathVariable int stepIndex,
            @RequestBody byte[] body) {
        PrivateRunnerFilter.RunnerToken token = token();
        if (token == null) return unauthorized();
        if (body == null || body.length == 0) {
            return ResponseEntity.badRequest().body(new ApiError("BAD_REQUEST", "Empty image body").asMap());
        }
        try {
            runs.writeStepImage(runId, token.enrollment().tenant().value(), tcId, stepIndex, body);
            return ResponseEntity.ok(new JSONObject().put("ok", true).toString());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(new ApiError("NOT_FOUND", e.getMessage()).asMap());
        } catch (Exception e) {
            return ResponseEntity.status(500).body(new ApiError("ERROR", e.getMessage()).asMap());
        }
    }

    @PostMapping(value = "/runs/{runId}/precision/bind",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> precisionBind(@PathVariable String runId, @RequestBody String body) {
        PrivateRunnerFilter.RunnerToken token = token();
        if (token == null) return unauthorized();
        if (body == null || body.isBlank()) {
            return ResponseEntity.badRequest().body(new ApiError("BAD_REQUEST", "body required").asMap());
        }
        try {
            JSONObject j = new JSONObject(body);
            List<MobilePrecisionBindService.Candidate> candidates = new ArrayList<>();
            JSONArray arr = j.optJSONArray("candidates");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject c = arr.getJSONObject(i);
                    candidates.add(new MobilePrecisionBindService.Candidate(
                            c.optString("id", ""),
                            c.optString("label", ""),
                            c.optString("contextKind", ""),
                            c.optBoolean("userOverride", false)));
                }
            }
            MobilePrecisionBindService.BindRequest req = new MobilePrecisionBindService.BindRequest(
                    j.optString("intent", ""),
                    candidates,
                    j.optString("stepContext", ""),
                    j.optString("snapshotExcerpt", ""));
            PrecisionOutcome out = precision.bind(runId, token.enrollment().tenant().value(), req);
            return ResponseEntity.ok(new JSONObject()
                    .put("code", out.code())
                    .put("chosenId", out.chosenId())
                    .put("usedProvider", out.usedProvider())
                    .put("fallbackReason", out.fallbackReason())
                    .put("countsAsCursor", out.countsAsCursor())
                    .toString());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiError("NOT_FOUND", e.getMessage()).asMap());
        } catch (Exception e) {
            return ResponseEntity.status(500).body(new ApiError("ERROR", e.getMessage()).asMap());
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
