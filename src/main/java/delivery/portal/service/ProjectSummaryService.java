package delivery.portal.service;

import delivery.ir.TcDraftStatus;
import delivery.portal.model.JobRecord;
import delivery.portal.persistence.JobEntity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Project Summary tab: mini strip facts + recent activity feed.
 */
@Service
public class ProjectSummaryService {
    public static final int RECENT_JOB_LIMIT = 10;

    private final PortalStore store;
    private final GeneratedWorkbookService workbooks;
    private final ProjectArtifactService artifacts;
    private final ProjectTcService projectTcs;

    public ProjectSummaryService(PortalStore store,
                                 GeneratedWorkbookService workbooks,
                                 ProjectArtifactService artifacts,
                                 ProjectTcService projectTcs) {
        this.store = store;
        this.workbooks = workbooks;
        this.artifacts = artifacts;
        this.projectTcs = projectTcs;
    }

    public Map<String, Object> build(String projectId, Long ownerUserId) throws Exception {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("strip", buildStrip(projectId));
        out.put("recentJobs", listRecentJobs(projectId, ownerUserId));
        return out;
    }

    private Map<String, Object> buildStrip(String projectId) throws Exception {
        Map<String, Object> strip = new LinkedHashMap<>();
        strip.put("libraryCaseCount", libraryCaseCount(projectId));
        strip.put("latestPackage", latestPackageLabel(projectId));
        Map<String, Object> prove = proveSnapshot(projectId);
        strip.put("prove", prove);
        strip.put("jobRunning", store.hasRunningJob(projectId));
        return strip;
    }

    private int libraryCaseCount(String projectId) {
        try {
            if (!workbooks.hasWorkbook(projectId)) {
                return 0;
            }
            return workbooks.readCases(projectId).size();
        } catch (Exception e) {
            return 0;
        }
    }

    private String latestPackageLabel(String projectId) throws Exception {
        var tree = artifacts.buildTree(store.projectDiskRoot(projectId));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> packages = (List<Map<String, Object>>) tree.getOrDefault("packages", List.of());
        if (packages == null || packages.isEmpty()) {
            return "none";
        }
        String best = null;
        int bestNum = -1;
        for (Map<String, Object> p : packages) {
            String label = String.valueOf(p.getOrDefault("label", ""));
            if (label.matches("v\\d+")) {
                int n = Integer.parseInt(label.substring(1));
                if (n > bestNum) {
                    bestNum = n;
                    best = label;
                }
            }
        }
        return best == null ? "none" : best;
    }

    private Map<String, Object> proveSnapshot(String projectId) throws Exception {
        int passed = 0;
        int blocked = 0;
        int reused = 0;
        for (Map<String, Object> row : projectTcs.listTcs(projectId)) {
            String status = String.valueOf(row.getOrDefault("status", ""));
            if (TcDraftStatus.PASSED.name().equals(status)) {
                passed++;
            } else if (TcDraftStatus.REUSED.name().equals(status)) {
                reused++;
            } else if (TcDraftStatus.PARTIAL.name().equals(status)
                    || TcDraftStatus.TODO.name().equals(status)
                    || "FAILED".equals(status)) {
                blocked++;
            } else {
                blocked++;
            }
        }
        Map<String, Object> prove = new LinkedHashMap<>();
        prove.put("passed", passed);
        prove.put("blocked", blocked);
        prove.put("reused", reused);
        prove.put("total", passed + blocked + reused);
        String lastRun = projectTcs.conversionRunLabel(projectId);
        prove.put("lastRunAt", "Unknown".equals(lastRun) && (passed + blocked + reused) == 0 ? null : lastRun);
        return prove;
    }

    private List<Map<String, Object>> listRecentJobs(String projectId, Long ownerUserId) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (JobEntity e : store.listJobEntities(ownerUserId)) {
            if (!projectId.equals(e.getProjectId())) {
                continue;
            }
            out.add(jobRow(e));
            if (out.size() >= RECENT_JOB_LIMIT) {
                break;
            }
        }
        return out;
    }

    private Map<String, Object> jobRow(JobEntity e) {
        var live = store.getJob(e.getJobId());
        String status = live.map(j -> j.getStatus().name()).orElse(e.getStatus());
        int passed = live.map(JobRecord::getPassedCount).orElse(e.getPassedCount());
        int todo = live.map(JobRecord::getTodoCount).orElse(e.getTodoCount());
        String message = live.map(j -> j.getMessage() == null ? "" : j.getMessage())
                .orElse(e.getMessage() == null ? "" : e.getMessage());
        String kind = e.getJobKind() == null ? JobRecord.JobKind.CONVERT.name() : e.getJobKind();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("jobId", e.getJobId());
        m.put("jobKind", kind);
        m.put("kindLabel", kindLabel(kind));
        m.put("status", status);
        m.put("passedCount", passed);
        m.put("todoCount", todo);
        m.put("message", truncate(message, 120));
        m.put("createdAt", e.getCreatedAt() == null ? "" : e.getCreatedAt().toString());
        m.put("createdAtLabel", formatWhen(e.getCreatedAt()));
        return m;
    }

    static String kindLabel(String kind) {
        if (kind == null) {
            return "Automate";
        }
        return switch (kind.toUpperCase(Locale.ROOT)) {
            case "EXECUTE" -> "Execute";
            case "GENERATE_BATCH" -> "Generate";
            case "GENERATE_COMPARE" -> "Compare";
            case "HUNT" -> "Bug Hunter";
            default -> "Automate";
        };
    }

    private static String truncate(String s, int max) {
        if (s == null || s.length() <= max) {
            return s == null ? "" : s;
        }
        return s.substring(0, max - 1) + "…";
    }

    private static String formatWhen(java.time.Instant instant) {
        if (instant == null) {
            return "—";
        }
        return java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                .withZone(java.time.ZoneId.systemDefault())
                .format(instant);
    }
}
