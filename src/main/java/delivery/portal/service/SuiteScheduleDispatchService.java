package delivery.portal.service;

import delivery.job.SuiteRunContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class SuiteScheduleDispatchService {

    private static final Logger log = LoggerFactory.getLogger(SuiteScheduleDispatchService.class);

    private final SuiteService suites;
    private final SuiteRunService suiteRuns;
    private final ProjectCredentialService credentials;

    public SuiteScheduleDispatchService(
            SuiteService suites,
            SuiteRunService suiteRuns,
            ProjectCredentialService credentials
    ) {
        this.suites = suites;
        this.suiteRuns = suiteRuns;
        this.credentials = credentials;
    }

    public int dispatchDue(int withinSeconds) {
        Instant now = Instant.now();
        int fired = 0;
        List<Map<String, Object>> candidates;
        try {
            candidates = suites.listDueForAllProjects(withinSeconds);
        } catch (Exception e) {
            log.warn("SCHEDULE_DISPATCH: list due failed: {}", e.getMessage());
            return 0;
        }
        for (Map<String, Object> row : candidates) {
            String projectId = String.valueOf(row.get("projectId"));
            String suiteId = String.valueOf(row.get("suiteId"));
            Long ownerId = row.get("ownerUserId") instanceof Number n ? n.longValue() : null;
            if (ownerId == null) {
                continue;
            }
            try {
                Optional<Map<String, Object>> claimed = suites.tryClaimDue(projectId, suiteId, now, withinSeconds);
                if (claimed.isEmpty()) {
                    continue;
                }
                // Slot reserved above. SuiteRunService picks the schedule's credential profile, guards overlap,
                // persists the suite context for the worker thread, and records + alerts a refused start.
                ResponseEntity<?> res = suiteRuns.startRun(
                        projectId, suiteId, ownerId, "schedule", null, null, true);
                if (res.getStatusCode().is2xxSuccessful()) {
                    fired++;
                    log.info("SCHEDULE_DISPATCH: queued suite {} project {}", suiteId, projectId);
                } else {
                    log.warn("SCHEDULE_DISPATCH: start refused {} {} — HTTP {}", projectId, suiteId,
                            res.getStatusCode().value());
                }
            } catch (Exception e) {
                try {
                    suites.recordScheduleFailure(projectId, suiteId, "DISPATCH_ERROR",
                            e.getMessage() == null ? "dispatch failed" : e.getMessage());
                } catch (Exception ignored) {
                }
                log.warn("SCHEDULE_DISPATCH: error {} {} — {}", projectId, suiteId, e.getMessage());
            }
        }
        return fired;
    }
}
