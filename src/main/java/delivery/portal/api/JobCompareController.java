package delivery.portal.api;

import delivery.ir.TcDraft;
import delivery.ir.TcDraftStatus;
import delivery.job.Intermittency;
import delivery.job.RerunSupport;
import delivery.job.RunCompare;
import delivery.portal.model.JobRecord;
import delivery.portal.security.CurrentUserService;
import delivery.portal.service.PortalStore;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/jobs")
public class JobCompareController {
    private final PortalStore store;
    private final CurrentUserService currentUser;

    public JobCompareController(PortalStore store, CurrentUserService currentUser) {
        this.store = store;
        this.currentUser = currentUser;
    }

    @GetMapping("/{jobId}/compare")
    public ResponseEntity<?> compare(
            @PathVariable("jobId") String jobId,
            @RequestParam("other") String otherId
    ) {
        Long uid = currentUser.requireUserId();
        JobRecord left = store.getOwnedJob(jobId, uid).orElse(null);
        JobRecord right = store.getOwnedJob(otherId, uid).orElse(null);
        if (left == null || right == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiError("NOT_FOUND", "Unknown job").asMap());
        }
        List<RunCompare.Step> leftSteps = RunCompare.fromDrafts(store.recordedDrafts(left));
        List<RunCompare.Step> rightSteps = RunCompare.fromDrafts(store.recordedDrafts(right));
        RunCompare.Divergence divergence = RunCompare.firstMeaningful(leftSteps, rightSteps);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("leftJobId", left.getJobId());
        body.put("rightJobId", right.getJobId());
        body.put("pinsMatch", RerunSupport.hashPinnedInputs(left).equals(RerunSupport.hashPinnedInputs(right)));
        body.put("leftPins", pins(left));
        body.put("rightPins", pins(right));
        body.put("leftPreserved", true);
        body.put("divergence", divergence == null ? null : divergenceMap(divergence));
        return ResponseEntity.ok(body);
    }

    @GetMapping("/{jobId}/attempts")
    public ResponseEntity<?> attempts(@PathVariable("jobId") String jobId) {
        Long uid = currentUser.requireUserId();
        JobRecord job = store.getOwnedJob(jobId, uid).orElse(null);
        if (job == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiError("NOT_FOUND", "Unknown job").asMap());
        }
        List<JobRecord> family = store.attemptFamily(job);
        String rootId = family.isEmpty() ? job.getJobId() : family.get(0).getJobId();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (JobRecord attempt : family) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("jobId", attempt.getJobId());
            row.put("parentJobId", attempt.getParentJobId());
            row.put("status", attempt.getStatus().name());
            row.put("passedCount", attempt.getPassedCount());
            row.put("todoCount", attempt.getTodoCount());
            row.put("createdAt", attempt.getCreatedAt() == null ? "" : attempt.getCreatedAt().toString());
            row.putAll(pins(attempt));
            rows.add(row);
        }
        return ResponseEntity.ok(Map.of("rootJobId", rootId, "attempts", rows));
    }

    @GetMapping("/{jobId}/intermittency")
    public ResponseEntity<?> intermittency(@PathVariable("jobId") String jobId) {
        Long uid = currentUser.requireUserId();
        JobRecord job = store.getOwnedJob(jobId, uid).orElse(null);
        if (job == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiError("NOT_FOUND", "Unknown job").asMap());
        }
        String pin = RerunSupport.hashPinnedInputs(job);
        List<JobRecord> comparable = new ArrayList<>();
        for (JobRecord attempt : store.attemptFamily(job)) {
            if (!pin.equals(RerunSupport.hashPinnedInputs(attempt))) {
                continue;
            }
            if (!JobRecord.isTerminal(attempt.getStatus())) {
                continue;
            }
            comparable.add(attempt);
        }
        List<Intermittency.Outcome> outcomes = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        for (JobRecord attempt : comparable) {
            ids.add(attempt.getJobId());
            outcomes.add(new Intermittency.Outcome(attempt.getJobId(), passed(attempt)));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sampleSize", outcomes.size());
        body.put("minSample", Intermittency.MIN_SAMPLE);
        body.put("verdict", Intermittency.verdict(outcomes).name());
        body.put("comparableJobIds", ids);
        body.put("cases", caseVerdicts(comparable));
        return ResponseEntity.ok(body);
    }

    private static Map<String, Object> pins(JobRecord job) {
        Map<String, Object> pins = new LinkedHashMap<>();
        pins.put("libraryRevisionId", job.getLibraryRevisionId());
        pins.put("environmentRevisionId", job.getEnvironmentRevisionId());
        pins.put("providerAllowlist", job.getProviderAllowlistSnapshot());
        pins.put("inputSnapshotHash", job.getInputSnapshotHash());
        return pins;
    }

    private static Map<String, Object> divergenceMap(RunCompare.Divergence d) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("index", d.index());
        out.put("field", d.field());
        out.put("left", stepMap(d.left()));
        out.put("right", stepMap(d.right()));
        return out;
    }

    private static Map<String, Object> stepMap(RunCompare.Step step) {
        if (step == null) {
            return Map.of();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("caseId", step.caseId());
        out.put("name", step.name());
        out.put("expected", step.expected());
        out.put("observed", step.observed());
        return out;
    }

    private List<Map<String, Object>> caseVerdicts(List<JobRecord> comparable) {
        Map<String, List<Intermittency.Outcome>> byCase = new LinkedHashMap<>();
        for (JobRecord attempt : comparable) {
            List<TcDraft> drafts = store.recordedDrafts(attempt);
            if (drafts.isEmpty()) {
                continue;
            }
            for (TcDraft draft : drafts) {
                byCase.computeIfAbsent(draft.tcId(), k -> new ArrayList<>())
                        .add(new Intermittency.Outcome(draft.tcId(), draft.status() == TcDraftStatus.PASSED
                                || draft.status() == TcDraftStatus.REUSED));
            }
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (var entry : byCase.entrySet()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("caseId", entry.getKey());
            row.put("sampleSize", entry.getValue().size());
            row.put("verdict", Intermittency.verdict(entry.getValue()).name());
            rows.add(row);
        }
        return rows;
    }

    private static boolean passed(JobRecord job) {
        return job.getStatus() == JobRecord.Status.COMPLETED && job.getTodoCount() == 0;
    }
}
