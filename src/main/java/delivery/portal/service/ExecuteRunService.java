package delivery.portal.service;

import delivery.ir.TcDraft;
import delivery.ir.TcDraftStatus;
import delivery.ir.TcDraftStore;
import delivery.portal.model.JobRecord;
import delivery.store.ProjectStore;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ExecuteRunService {
    private static final DateTimeFormatter WHEN = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());

    private final PortalStore store;

    public ExecuteRunService(PortalStore store) {
        this.store = store;
    }

    public Optional<JobRecord> requireOwnedExecuteJob(String jobId, Long ownerUserId) {
        return store.getOwnedJob(jobId, ownerUserId)
                .filter(j -> j.getJobKind() == JobRecord.JobKind.EXECUTE);
    }

    /**
     * Hard-delete an execute job the caller owns: DB + memory + on-disk execute-runs folder.
     * Refuses QUEUED/RUNNING (cancel first).
     *
     * @return empty if not found / not owned / not EXECUTE;
     *         Optional.of("ACTIVE") if still running;
     *         Optional.of("OK") on success
     */
    public Optional<String> deleteOwnedExecuteRun(String jobId, Long ownerUserId) throws Exception {
        Optional<JobRecord> owned = requireOwnedExecuteJob(jobId, ownerUserId);
        if (owned.isEmpty()) {
            return Optional.empty();
        }
        JobRecord job = owned.get();
        if (job.getStatus() == JobRecord.Status.QUEUED || job.getStatus() == JobRecord.Status.RUNNING) {
            return Optional.of("ACTIVE");
        }
        Path root = runRoot(job);
        store.deleteOwnedJobRecord(jobId, ownerUserId);
        if (Files.isDirectory(root)) {
            ProjectStore.deleteRecursive(root);
        }
        return Optional.of("OK");
    }

    public List<Map<String, Object>> listTcs(String jobId) throws Exception {
        JobRecord job = store.getJob(jobId).orElseThrow(() -> new IllegalArgumentException("Unknown job"));
        List<Map<String, Object>> out = new ArrayList<>();
        for (TcDraft d : readDrafts(jobId)) {
            out.add(summary(job, d));
        }
        return out;
    }

    public Map<String, Object> getTc(String jobId, String tcId) throws Exception {
        JobRecord job = store.getJob(jobId).orElseThrow(() -> new IllegalArgumentException("Unknown job"));
        TcDraftStore drafts = draftStore(job);
        if (!Files.isDirectory(drafts.irDir())) {
            throw new IllegalArgumentException("No IR store for execute run");
        }
        TcDraft d = drafts.read(tcId);
        Map<String, Object> detail = summary(job, d);
        detail.put("stepsText", d.stepsText());
        detail.put("expectedResult", d.expectedResult());
        detail.put("lastPageUrl", d.lastPageUrl());
        detail.put("runAtLabel", runAtLabel(job));
        List<Map<String, Object>> timeline = ProjectTcService.buildTimeline(job.getProjectId(), d);
        attachExistingScreenshots(jobId, d.tcId(), timeline);
        detail.put("timeline", timeline);
        return detail;
    }

    public Optional<Path> resolveScreenshot(String jobId, String tcId, String fileName) {
        if (fileName == null || fileName.isBlank()
                || fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            return Optional.empty();
        }
        Optional<JobRecord> job = store.getJob(jobId);
        if (job.isEmpty() || job.get().getJobKind() != JobRecord.JobKind.EXECUTE) {
            return Optional.empty();
        }
        Path file = runRoot(job.get()).resolve("evidence").resolve(tcId).resolve(fileName);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        return Optional.of(file);
    }

    List<TcDraft> readDraftsForJob(String jobId) throws Exception {
        return readDrafts(jobId);
    }

    Map<String, Object> failReportRow(JobRecord job, TcDraft d) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("tcId", d.tcId());
        row.put("title", d.title());
        row.put("qaStatus", qaStatus(d.status()));
        row.put("failureReason", d.failureReason());
        row.put("failureReasonUser", FailureReasonHumanizer.forUser(d.failureReason()));
        row.put("blockerStepIndex", d.blockerStepIndex());
        row.put("blockerIntent", d.blockerIntent());
        Path evidenceDir = tcEvidenceDir(job, d.tcId());
        BugReportService.readEvidenceStatus(evidenceDir, "visual-assert.json")
                .ifPresent(status -> row.put("visualAssertStatus", status));
        BugReportService.readEvidenceStatus(evidenceDir, "design-compare.json")
                .ifPresent(status -> row.put("designCompareStatus", status));
        row.put("screenshotUrls", listScreenshotUrls(job.getJobId(), d.tcId()));
        return row;
    }

    private List<TcDraft> readDrafts(String jobId) throws Exception {
        JobRecord job = store.getJob(jobId).orElseThrow(() -> new IllegalArgumentException("Unknown job"));
        TcDraftStore drafts = draftStore(job);
        if (!Files.isDirectory(drafts.irDir())) {
            return List.of();
        }
        return drafts.readAll();
    }

    private TcDraftStore draftStore(JobRecord job) {
        return new TcDraftStore(runRoot(job));
    }

    private Path runRoot(JobRecord job) {
        return store.filesystemStoreFor(job.getProjectId()).projectRoot(job.getProjectId())
                .resolve("execute-runs").resolve(job.getJobId());
    }

    private Map<String, Object> summary(JobRecord job, TcDraft d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tcId", d.tcId());
        m.put("title", d.title());
        m.put("status", d.status().name());
        m.put("qaStatus", qaStatus(d.status()));
        m.put("provenCount", d.provenSteps().size());
        m.put("blockerStepIndex", d.blockerStepIndex());
        m.put("blockerIntent", d.blockerIntent());
        m.put("failureReason", d.failureReason());
        m.put("failureReasonUser", FailureReasonHumanizer.forUser(d.failureReason()));
        m.put("needsLoginBeforeMethod", d.needsLoginBeforeMethod());
        m.put("expandable", true);
        BugReportService.readEvidenceStatus(tcEvidenceDir(job, d.tcId()), "design-compare.json")
                .ifPresent(status -> m.put("designCompareStatus", status));
        return m;
    }

    boolean includeInBugReport(JobRecord job, TcDraft d) {
        if ("FAIL".equals(qaStatus(d.status()))) {
            return true;
        }
        return BugReportService.readEvidenceStatus(tcEvidenceDir(job, d.tcId()), "design-compare.json")
                .filter("MISMATCH"::equals)
                .isPresent();
    }

    private Path tcEvidenceDir(JobRecord job, String tcId) {
        return runRoot(job).resolve("evidence").resolve(tcId);
    }

    List<String> listScreenshotUrls(String jobId, String tcId) {
        Optional<JobRecord> job = store.getJob(jobId);
        if (job.isEmpty()) {
            return List.of();
        }
        Path dir = tcEvidenceDir(job.get(), tcId);
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        List<String> urls = new ArrayList<>();
        try (var stream = Files.list(dir)) {
            stream.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> {
                        String lower = name.toLowerCase();
                        return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg");
                    })
                    .sorted()
                    .forEach(fileName -> {
                        if (resolveScreenshot(jobId, tcId, fileName).isPresent()) {
                            urls.add("/api/execute-runs/" + jobId
                                    + "/tcs/" + tcId + "/screenshots/" + fileName + "?v=" + fileName);
                        }
                    });
        } catch (Exception e) {
            return List.of();
        }
        return urls;
    }

    static String qaStatus(TcDraftStatus status) {
        if (status == TcDraftStatus.PASSED || status == TcDraftStatus.REUSED) {
            return "PASS";
        }
        return "FAIL";
    }

    private void attachExistingScreenshots(String jobId, String tcId, List<Map<String, Object>> timeline) {
        for (Map<String, Object> row : timeline) {
            String fileName = null;
            Object shot = row.get("screenshot");
            if (shot != null && !shot.toString().isBlank()) {
                fileName = shot.toString();
            } else if ("failed".equals(row.get("state"))) {
                fileName = "failure.png";
            }
            row.remove("screenshotUrl");
            if (fileName == null) {
                continue;
            }
            if (resolveScreenshot(jobId, tcId, fileName).isPresent()) {
                row.put("screenshot", fileName);
                row.put("screenshotUrl", "/api/execute-runs/" + jobId
                        + "/tcs/" + tcId + "/screenshots/" + fileName + "?v=" + fileName);
            } else {
                row.remove("screenshot");
            }
        }
    }

    private String runAtLabel(JobRecord job) {
        Instant when = job.runAt();
        if (when == null) {
            when = store.getJob(job.getJobId()).map(JobRecord::runAt).orElse(null);
        }
        return when == null ? "Unknown" : WHEN.format(when);
    }
}
