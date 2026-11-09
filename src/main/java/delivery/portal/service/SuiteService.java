package delivery.portal.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import delivery.portal.alerts.AlertChannel;
import delivery.portal.model.JobRecord;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SuiteService {
    private final PortalStore store;
    private final SuiteDiskStore disk;
    private final List<AlertChannel> channels;

    public SuiteService(PortalStore store, ObjectMapper objectMapper, List<AlertChannel> channels) {
        this.store = store;
        this.disk = new SuiteDiskStore(objectMapper);
        this.channels = channels == null ? List.of() : channels;
    }

    SuiteDiskStore disk() {
        return disk;
    }

    public Path projectRoot(String projectId) {
        return store.projectDiskRoot(projectId);
    }

    public List<Map<String, Object>> list(String projectId, String tag) throws IOException {
        return disk.list(projectRoot(projectId), tag);
    }

    public Map<String, Object> get(String projectId, String suiteId) throws IOException {
        return disk.get(projectRoot(projectId), suiteId)
                .orElseThrow(() -> new SuiteDiskStore.NotFoundException("Suite not found: " + suiteId));
    }

    public Map<String, Object> create(String projectId, String name, List<String> tags, List<String> tcIds)
            throws IOException {
        return disk.create(projectRoot(projectId), name, tags, tcIds);
    }

    public Map<String, Object> update(
            String projectId,
            String suiteId,
            String name,
            List<String> tags,
            List<String> tcIds,
            String structuredPlanId
    ) throws IOException {
        return disk.update(projectRoot(projectId), suiteId, name, tags, tcIds, structuredPlanId);
    }

    public void delete(String projectId, String suiteId) throws IOException {
        disk.delete(projectRoot(projectId), suiteId);
    }

    public Map<String, Object> getSchedule(String projectId, String suiteId) throws IOException {
        Map<String, Object> suite = get(projectId, suiteId);
        Object schedule = suite.get("schedule");
        if (schedule == null) {
            return null;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> m = (Map<String, Object>) schedule;
        return m;
    }

    public Map<String, Object> putSchedule(
            String projectId,
            String suiteId,
            boolean enabled,
            String kind,
            String cron,
            Integer intervalMinutes
    ) throws IOException {
        return disk.putSchedule(projectRoot(projectId), suiteId, enabled, kind, cron, intervalMinutes);
    }

    public Map<String, Object> putSchedule(
            String projectId,
            String suiteId,
            boolean enabled,
            String kind,
            String cron,
            Integer intervalMinutes,
            String timezone,
            String credentialProfile
    ) throws IOException {
        return disk.putSchedule(projectRoot(projectId), suiteId, enabled, kind, cron, intervalMinutes,
                timezone, credentialProfile);
    }

    /** Final state of a suite run (schedule last result, monitor status). */
    public Map<String, Object> recordRunOutcome(String projectId, String suiteId, String jobId, String jobStatus,
                                                int passed, int failed, boolean monitorRun) throws IOException {
        return disk.recordRunOutcome(projectRoot(projectId), suiteId, jobId, jobStatus, passed, failed, monitorRun);
    }

    public Map<String, Object> clearSchedule(String projectId, String suiteId) throws IOException {
        return disk.clearSchedule(projectRoot(projectId), suiteId);
    }

    public Map<String, Object> bindAlert(
            String projectId,
            String suiteId,
            String alertChannelId,
            String alertMinSeverity
    ) throws IOException {
        requireKnownChannel(alertChannelId);
        return disk.bindAlert(projectRoot(projectId), suiteId, alertChannelId, alertMinSeverity);
    }

    public Map<String, Object> clearAlert(String projectId, String suiteId) throws IOException {
        return disk.clearAlert(projectRoot(projectId), suiteId);
    }

    public Map<String, Object> promoteMonitor(
            String projectId,
            String suiteId,
            String target,
            String fromJobId,
            Long ownerUserId
    ) throws IOException {
        if (fromJobId != null && !fromJobId.isBlank()) {
            requireGreenJob(projectId, fromJobId.trim(), ownerUserId);
        }
        return disk.promoteMonitor(projectRoot(projectId), suiteId, target, fromJobId);
    }

    public Map<String, Object> demoteMonitor(String projectId, String suiteId) throws IOException {
        return disk.demoteMonitor(projectRoot(projectId), suiteId);
    }

    public List<Map<String, Object>> listMonitors(String projectId) throws IOException {
        return disk.listMonitors(projectRoot(projectId));
    }

    public List<Map<String, Object>> listAlertChannels() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (AlertChannel ch : channels) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", ch.id());
            row.put("enabled", ch.enabled());
            row.put("minSeverity", ch.minSeverity() == null ? null : ch.minSeverity().name().toLowerCase(Locale.ROOT));
            out.add(row);
        }
        return out;
    }

    public Set<String> knownChannelIds() {
        return channels.stream().map(AlertChannel::id).collect(Collectors.toSet());
    }

    public List<Map<String, Object>> listDue(Long userId, int withinSeconds) throws IOException {
        Instant now = Instant.now();
        List<Map<String, Object>> due = new ArrayList<>();
        for (var project : store.listProjects(userId, true)) {
            String projectId = project.getProjectId();
            if (!store.canOperate(projectId, userId)) {
                continue;
            }
            if (project.isArchived()) {
                continue;
            }
            for (Map<String, Object> suite : disk.list(projectRoot(projectId), null)) {
                if (!disk.isDue(suite, now, withinSeconds)) {
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("projectId", projectId);
                row.put("suiteId", suite.get("suiteId"));
                row.put("schedule", suite.get("schedule"));
                row.put("tcIds", suite.get("tcIds"));
                row.put("alertChannelId", suite.get("alertChannelId"));
                due.add(row);
            }
        }
        return due;
    }

    public Map<String, Object> recordFire(String projectId, String suiteId, String jobId) throws IOException {
        return disk.recordSuiteRunFire(projectRoot(projectId), suiteId, jobId);
    }

    public Optional<Map<String, Object>> tryClaimDue(String projectId, String suiteId, Instant now, int withinSeconds)
            throws IOException {
        return disk.tryClaimDue(projectRoot(projectId), suiteId, now, withinSeconds);
    }

    public void recordScheduleFailure(String projectId, String suiteId, String code, String message) throws IOException {
        disk.recordScheduleFailure(projectRoot(projectId), suiteId, code, message);
    }

    public List<Map<String, Object>> listDueForAllProjects(int withinSeconds) throws IOException {
        Instant now = Instant.now();
        List<Map<String, Object>> due = new ArrayList<>();
        for (var project : store.listProjectsForScheduleDispatch()) {
            if (project.isArchived()) {
                continue;
            }
            String projectId = project.getProjectId();
            for (Map<String, Object> suite : disk.list(projectRoot(projectId), null)) {
                if (disk.isDue(suite, now, withinSeconds)) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("projectId", projectId);
                    row.put("ownerUserId", project.getOwnerUserId());
                    row.put("suiteId", suite.get("suiteId"));
                    row.put("schedule", suite.get("schedule"));
                    row.put("tcIds", suite.get("tcIds"));
                    row.put("alertChannelId", suite.get("alertChannelId"));
                    row.put("alertMinSeverity", suite.get("alertMinSeverity"));
                    row.put("monitor", suite.get("monitor"));
                    due.add(row);
                }
            }
        }
        return due;
    }

    private void requireKnownChannel(String alertChannelId) {
        if (alertChannelId == null || alertChannelId.isBlank()) {
            throw new SuiteDiskStore.BadRequestException("alertChannelId is required");
        }
        String id = alertChannelId.trim();
        boolean known = channels.stream().anyMatch(c -> id.equalsIgnoreCase(c.id()));
        if (!known) {
            throw new SuiteDiskStore.BadRequestException("Unknown alert channel: " + id);
        }
    }

    private void requireGreenJob(String projectId, String jobId, Long ownerUserId) {
        Optional<JobRecord> opt = store.getOwnedJob(jobId, ownerUserId);
        JobRecord job = opt.orElseThrow(() ->
                new SuiteDiskStore.ConflictException("fromJobId not found or not green: " + jobId));
        if (!projectId.equals(job.getProjectId())) {
            throw new SuiteDiskStore.ConflictException("fromJobId does not belong to this project");
        }
        if (!isGreen(job)) {
            throw new SuiteDiskStore.ConflictException("fromJobId is not a green suite run: " + jobId);
        }
    }

    static boolean isGreen(JobRecord job) {
        if (job == null) {
            return false;
        }
        return job.getStatus() == JobRecord.Status.COMPLETED && job.getTodoCount() <= 0;
    }
}
