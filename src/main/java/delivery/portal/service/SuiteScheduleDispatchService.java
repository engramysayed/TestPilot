package delivery.portal.service;

import delivery.job.SuiteRunContext;
import delivery.mobile.run.MobileRunOrchestrator;
import delivery.mobile.run.MobileRunnerSupervisor;
import delivery.portal.model.ProjectRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class SuiteScheduleDispatchService {

    private static final Logger log = LoggerFactory.getLogger(SuiteScheduleDispatchService.class);

    private final SuiteService suites;
    private final SuiteRunService suiteRuns;
    private final ProjectCredentialService credentials;
    private final PortalStore portalStore;
    private final MobileRunOrchestrator mobileRuns;
    private final MobileRunnerSupervisor runners;

    @Autowired
    public SuiteScheduleDispatchService(
            SuiteService suites,
            SuiteRunService suiteRuns,
            ProjectCredentialService credentials,
            PortalStore portalStore,
            MobileRunOrchestrator mobileRuns,
            MobileRunnerSupervisor runners
    ) {
        this.suites = suites;
        this.suiteRuns = suiteRuns;
        this.credentials = credentials;
        this.portalStore = portalStore;
        this.mobileRuns = mobileRuns;
        this.runners = runners;
    }

    /** Legacy 3-arg ctor kept for existing test wiring that constructed the service manually. */
    public SuiteScheduleDispatchService(
            SuiteService suites,
            SuiteRunService suiteRuns,
            ProjectCredentialService credentials
    ) {
        this(suites, suiteRuns, credentials, null, null, null);
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
                if (isMobileProject(projectId) && mobileRuns != null) {
                    if (dispatchMobile(projectId, suiteId, ownerId)) {
                        fired++;
                    }
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

    private boolean isMobileProject(String projectId) {
        if (portalStore == null) return false;
        try {
            return portalStore.getProject(projectId)
                    .map(p -> "ANDROID".equalsIgnoreCase(p.getPlatform()))
                    .orElse(false);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Android scheduling — Phase 6a: pinned device only. If the schedule omitted a mobileDeviceId
     * we refuse with DEVICE_REQUIRED. "Any available phone with this profile" lands in Phase 6b.
     */
    private boolean dispatchMobile(String projectId, String suiteId, Long ownerId) {
        try {
            Map<String, Object> schedule = suites.getSchedule(projectId, suiteId);
            String deviceId = schedule == null ? null : asString(schedule.get("mobileDeviceId"));
            String revisionId = schedule == null ? null : asString(schedule.get("mobileAppRevisionId"));
            if (deviceId == null || deviceId.isBlank()) {
                suites.recordScheduleFailure(projectId, suiteId, "DEVICE_REQUIRED",
                        "Mobile schedule needs a pinned phone. Open Suites → Edit → Phone and save.");
                return false;
            }
            ProjectRecord project = portalStore.getProject(projectId).orElse(null);
            if (project == null) {
                suites.recordScheduleFailure(projectId, suiteId, "PROJECT_NOT_FOUND", "Project missing");
                return false;
            }
            // tcIds = all cases registered against the suite (same shape SuiteRunService builds).
            List<String> tcIds = extractTcIds(suites.get(projectId, suiteId));
            if (runners != null) {
                runners.startForRun();
            }
            mobileRuns.start(project.getTenantId(), projectId, ownerId, deviceId, tcIds, revisionId);
            log.info("SCHEDULE_DISPATCH: queued mobile suite {} project {} on {}", suiteId, projectId, deviceId);
            return true;
        } catch (IllegalStateException busy) {
            // Device not AVAILABLE — common transient failure.
            try {
                suites.recordScheduleFailure(projectId, suiteId, "DEVICE_OFFLINE",
                        busy.getMessage() == null ? "Phone unavailable" : busy.getMessage());
            } catch (Exception ignored) {
            }
            log.warn("SCHEDULE_DISPATCH: mobile phone offline {} {}", projectId, suiteId);
            return false;
        } catch (IllegalArgumentException bad) {
            try {
                suites.recordScheduleFailure(projectId, suiteId, "BAD_REQUEST",
                        bad.getMessage() == null ? "Invalid mobile schedule" : bad.getMessage());
            } catch (Exception ignored) {
            }
            return false;
        } catch (Exception e) {
            try {
                suites.recordScheduleFailure(projectId, suiteId, "DISPATCH_ERROR",
                        e.getMessage() == null ? "mobile dispatch failed" : e.getMessage());
            } catch (Exception ignored) {
            }
            log.warn("SCHEDULE_DISPATCH: mobile error {} {} — {}", projectId, suiteId, e.getMessage());
            return false;
        }
    }

    private static String asString(Object v) { return v == null ? "" : String.valueOf(v); }

    @SuppressWarnings("unchecked")
    private static List<String> extractTcIds(Map<String, Object> suiteRow) {
        if (suiteRow == null) return List.of();
        Object cases = suiteRow.get("tcIds");
        if (cases instanceof List<?> l) {
            List<String> out = new ArrayList<>();
            for (Object o : l) {
                if (o != null && !asString(o).isBlank()) out.add(asString(o).trim());
            }
            return out;
        }
        return List.of();
    }
}
