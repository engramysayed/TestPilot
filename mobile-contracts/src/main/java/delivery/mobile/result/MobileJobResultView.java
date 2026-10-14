package delivery.mobile.result;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Aggregated job/attempt mobile result section for Execute UI (M2-07).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MobileJobResultView(
        String jobId,
        String attemptId,
        String projectId,
        CaseOutcomeView aggregateOutcome,
        RuntimeEvidence runtime,
        List<MobileCaseResultView> cases,
        int passedCount,
        int failedCount,
        int blockedCount,
        int todoCount,
        int interruptedCount,
        long timingMs
) {
    public MobileJobResultView {
        jobId = jobId == null ? "" : jobId;
        attemptId = attemptId == null ? "" : attemptId;
        projectId = projectId == null ? "" : projectId;
        aggregateOutcome = aggregateOutcome == null ? CaseOutcomeView.TODO : aggregateOutcome;
        runtime = runtime == null ? RuntimeEvidence.empty() : runtime;
        cases = cases == null ? List.of() : List.copyOf(cases);
        if (timingMs < 0) {
            timingMs = 0;
        }
    }

    public static MobileJobResultView of(
            String jobId, String attemptId, String projectId,
            RuntimeEvidence runtime, List<MobileCaseResultView> cases, long timingMs) {
        List<MobileCaseResultView> list = cases == null ? List.of() : cases;
        int passed = 0, failed = 0, blocked = 0, todo = 0, interrupted = 0;
        for (MobileCaseResultView c : list) {
            switch (c.outcome()) {
                case PASSED -> passed++;
                case FAILED -> failed++;
                case BLOCKED -> blocked++;
                case TODO, EXCLUDED_SIMULATED -> todo++;
                case INTERRUPTED_UNCERTAIN -> interrupted++;
            }
        }
        // Contract preference: INTERRUPTED_UNCERTAIN > FAILED > BLOCKED > PASSED (empty → TODO)
        CaseOutcomeView agg;
        if (list.isEmpty()) {
            agg = CaseOutcomeView.TODO;
        } else if (interrupted > 0) {
            agg = CaseOutcomeView.INTERRUPTED_UNCERTAIN;
        } else if (failed > 0) {
            agg = CaseOutcomeView.FAILED;
        } else if (blocked > 0) {
            agg = CaseOutcomeView.BLOCKED;
        } else if (passed > 0) {
            agg = CaseOutcomeView.PASSED;
        } else {
            agg = CaseOutcomeView.TODO;
        }
        return new MobileJobResultView(
                jobId, attemptId, projectId, agg, runtime, list,
                passed, failed, blocked, todo, interrupted, timingMs);
    }
}
