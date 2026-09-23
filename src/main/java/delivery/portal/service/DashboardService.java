package delivery.portal.service;

import delivery.job.ResultIntegrity;
import delivery.portal.model.JobRecord;
import delivery.portal.persistence.JobEntity;
import delivery.portal.persistence.JobRepository;
import delivery.portal.persistence.ProjectEntity;
import delivery.portal.persistence.ProjectRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class DashboardService {
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            .withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());

    private final ProjectRepository projects;
    private final JobRepository jobs;
    private final ExecuteRunService executeRunService;
    private final JobLinkEnricher jobLinks;

    public DashboardService(ProjectRepository projects, JobRepository jobs,
                            ExecuteRunService executeRunService, JobLinkEnricher jobLinks) {
        this.projects = projects;
        this.jobs = jobs;
        this.executeRunService = executeRunService;
        this.jobLinks = jobLinks;
    }

    public List<Map<String, Object>> attentionFor(Long ownerUserId) {
        List<JobEntity> allJobs = jobs.findByOwnerUserIdOrderByCreatedAtDesc(ownerUserId);
        Map<String, String> projectNames = projectNames(ownerUserId);
        List<ScoredAttention> scored = new ArrayList<>();
        for (JobEntity job : allJobs) {
            int priority = attentionPriority(job);
            if (priority < 0) {
                continue;
            }
            scored.add(new ScoredAttention(priority, job));
        }
        scored.sort(Comparator
                .comparingInt((ScoredAttention s) -> s.priority).reversed()
                .thenComparing(s -> s.job.getCreatedAt(), Comparator.nullsLast(Comparator.reverseOrder())));
        List<Map<String, Object>> items = new ArrayList<>();
        for (ScoredAttention entry : scored) {
            if (items.size() >= 5) {
                break;
            }
            items.add(toAttentionItem(entry.job, projectNames));
        }
        return items;
    }

    public Map<String, Object> statsFor(Long ownerUserId) {
        List<ProjectEntity> projectList = projects.findByOwnerUserIdOrderByIdDesc(ownerUserId);
        List<JobEntity> allJobs = jobs.findByOwnerUserIdOrderByCreatedAtDesc(ownerUserId);
        List<JobEntity> convertJobs = allJobs.stream()
                .filter(j -> JobRecord.parseJobKind(j.getJobKind()) == JobRecord.JobKind.CONVERT)
                .toList();
        int passed = 0;
        int todo = 0;
        java.util.List<ResultIntegrity.JobSlice> slices = new ArrayList<>();
        for (JobEntity j : convertJobs) {
            int jobPassed = j.getPassedCount();
            int jobTodo = j.getTodoCount();
            passed += jobPassed;
            todo += jobTodo;
            if (ResultIntegrity.simulatedMessage(j.getMessage())) {
                slices.add(ResultIntegrity.JobSlice.simulated(jobPassed, jobTodo));
            } else if (jobPassed <= 0 && jobTodo > 0 && "COMPLETED".equals(j.getStatus())) {
                slices.add(ResultIntegrity.JobSlice.unchecked(jobTodo));
            } else {
                slices.add(ResultIntegrity.JobSlice.proven(jobPassed, jobTodo));
            }
        }
        ResultIntegrity.Summary integrity = ResultIntegrity.summarize(
                slices.toArray(ResultIntegrity.JobSlice[]::new));
        int passRate = integrity.passRate();
        long completed = convertJobs.stream()
                .filter(j -> JobRecord.isDownloadable(JobRecord.JobKind.CONVERT, j.getStatus()))
                .count();
        long failed = convertJobs.stream().filter(j -> "FAILED".equals(j.getStatus())).count();
        int activeProjectCount = (int) projectList.stream().filter(p -> !p.isArchived()).count();
        long runningJobs = allJobs.stream()
                .filter(j -> "QUEUED".equals(j.getStatus()) || "RUNNING".equals(j.getStatus()))
                .count();

        long executeJobs = allJobs.stream()
                .filter(j -> JobRecord.parseJobKind(j.getJobKind()) == JobRecord.JobKind.EXECUTE)
                .count();
        long executeRunning = allJobs.stream()
                .filter(j -> JobRecord.parseJobKind(j.getJobKind()) == JobRecord.JobKind.EXECUTE)
                .filter(j -> "QUEUED".equals(j.getStatus()) || "RUNNING".equals(j.getStatus()))
                .count();
        int statusCompleted = 0;
        int statusBlocked = 0;
        int statusFailed = 0;
        int statusRunning = 0;
        int statusQueued = 0;
        for (JobEntity j : allJobs) {
            switch (j.getStatus()) {
                case "COMPLETED" -> statusCompleted++;
                case "COMPLETED_WITH_BLOCK" -> statusBlocked++;
                case "FAILED" -> statusFailed++;
                case "RUNNING" -> statusRunning++;
                case "QUEUED" -> statusQueued++;
                default -> { /* ignore */ }
            }
        }

        Map<String, int[]> byDay = new java.util.LinkedHashMap<>();
        for (JobEntity j : convertJobs) {
            Instant when = j.getCompletedAt() != null ? j.getCompletedAt() : j.getCreatedAt();
            if (when == null) {
                continue;
            }
            String day = DAY.format(when);
            byDay.putIfAbsent(day, new int[]{0, 0});
            int[] pair = byDay.get(day);
            pair[0] += j.getPassedCount();
            pair[1] += j.getTodoCount();
        }
        List<String> labels = new ArrayList<>(byDay.keySet());
        java.util.Collections.reverse(labels);
        List<Integer> passedSeries = new ArrayList<>();
        List<Integer> todoSeries = new ArrayList<>();
        for (String day : labels) {
            int[] pair = byDay.get(day);
            passedSeries.add(pair[0]);
            todoSeries.add(pair[1]);
        }

        Map<String, String> projectNames = projectList.stream()
                .collect(Collectors.toMap(ProjectEntity::getProjectId, ProjectEntity::getName, (a, b) -> a));

        List<Map<String, Object>> recentJobs = new ArrayList<>();
        for (JobEntity j : convertJobs) {
            recentJobs.add(recentRunRow(j, projectNames));
        }

        List<Map<String, Object>> recentRuns = allJobs.stream()
                .limit(10)
                .map(j -> recentRunRow(j, projectNames))
                .toList();

        List<Map<String, Object>> attention = attentionFor(ownerUserId);

        Map<String, Object> map = new HashMap<>();
        map.put("projectCount", projectList.size());
        map.put("activeProjectCount", activeProjectCount);
        map.put("jobCount", convertJobs.size());
        map.put("executeJobCount", (int) executeJobs);
        map.put("executeRunning", (int) executeRunning);
        map.put("runningJobs", (int) runningJobs);
        map.put("hasProjects", activeProjectCount > 0);
        map.put("hasJobs", !convertJobs.isEmpty());
        map.put("completedJobs", completed);
        map.put("failedJobs", failed);
        map.put("passedTotal", passed);
        map.put("todoTotal", todo);
        map.put("passRate", passRate);
        map.put("hasProvenSample", integrity.hasProvenSample());
        map.put("provenPassed", integrity.provenPassed());
        map.put("simulatedTotal", integrity.simulated());
        map.put("passRateNote", integrity.denominatorNote());
        map.put("statusCompleted", statusCompleted);
        map.put("statusBlocked", statusBlocked);
        map.put("statusFailed", statusFailed);
        map.put("statusRunning", statusRunning);
        map.put("statusQueued", statusQueued);
        map.put("attentionCount", attention.size());
        map.put("chartLabels", labels);
        map.put("chartPassed", passedSeries);
        map.put("chartTodo", todoSeries);
        map.put("recentJobs", recentJobs);
        map.put("recentRuns", recentRuns);
        map.put("attention", attention);
        map.put("projectOptions", projectList.stream()
                .filter(p -> !p.isArchived())
                .map(p -> Map.<String, Object>of("projectId", p.getProjectId(), "name", p.getName()))
                .collect(Collectors.toList()));
        return map;
    }

    private Map<String, Object> recentRunRow(JobEntity j, Map<String, String> projectNames) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("jobId", j.getJobId());
        row.put("projectId", j.getProjectId());
        row.put("projectName", projectNames.getOrDefault(j.getProjectId(), j.getProjectId()));
        row.put("jobKind", j.getJobKind() == null ? JobRecord.JobKind.CONVERT.name() : j.getJobKind());
        row.put("mode", j.getMode());
        row.put("status", j.getStatus());
        row.put("passedCount", j.getPassedCount());
        row.put("todoCount", j.getTodoCount());
        row.put("progressCurrent", j.getProgressCurrent());
        row.put("progressTotal", j.getProgressTotal());
        Instant when = j.getCompletedAt() != null ? j.getCompletedAt() : j.getCreatedAt();
        row.put("createdAt", when == null ? "" : WHEN.format(when));
        row.put("day", when == null ? "" : DAY.format(when));
        row.put("downloadable", JobRecord.isDownloadable(JobRecord.parseJobKind(j.getJobKind()), j.getStatus()));
        jobLinks.enrich(row, j.getJobId(), JobRecord.parseJobKind(j.getJobKind()), j.getStatus());
        return row;
    }

    private Map<String, String> projectNames(Long ownerUserId) {
        return projects.findByOwnerUserIdOrderByIdDesc(ownerUserId).stream()
                .collect(Collectors.toMap(ProjectEntity::getProjectId, ProjectEntity::getName, (a, b) -> a));
    }

    private int attentionPriority(JobEntity job) {
        JobRecord.JobKind kind = JobRecord.parseJobKind(job.getJobKind());
        String status = job.getStatus();
        if ("FAILED".equals(status)) {
            if (kind == JobRecord.JobKind.EXECUTE || kind == JobRecord.JobKind.CONVERT
                    || kind == JobRecord.JobKind.HUNT) {
                return 100;
            }
            return 80;
        }
        if ("COMPLETED_WITH_BLOCK".equals(status)) {
            return 70;
        }
        if ("QUEUED".equals(status) || "RUNNING".equals(status)) {
            return 40;
        }
        if (kind == JobRecord.JobKind.EXECUTE && "COMPLETED".equals(status)
                && executeRunService.hasDesignCompareAttention(job.getJobId())) {
            return 20;
        }
        return -1;
    }

    private Map<String, Object> toAttentionItem(JobEntity job, Map<String, String> projectNames) {
        JobRecord.JobKind kind = JobRecord.parseJobKind(job.getJobKind());
        String projectName = projectNames.getOrDefault(job.getProjectId(), job.getProjectId());
        Map<String, Object> links = jobLinks.baseRow(job.getJobId(), job.getProjectId(),
                kind.name(), job.getStatus());
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("jobId", job.getJobId());
        item.put("projectId", job.getProjectId());
        item.put("jobKind", kind.name());
        item.put("severity", attentionSeverity(job, kind));
        item.put("title", attentionTitle(job, kind));
        item.put("subtitle", projectName + " · " + job.getJobId());
        item.put("href", attentionHref(job, kind, links));
        return item;
    }

    private static String attentionSeverity(JobEntity job, JobRecord.JobKind kind) {
        if ("FAILED".equals(job.getStatus())) {
            return "error";
        }
        if ("COMPLETED_WITH_BLOCK".equals(job.getStatus())) {
            return "warn";
        }
        if ("QUEUED".equals(job.getStatus()) || "RUNNING".equals(job.getStatus())) {
            return "info";
        }
        if (kind == JobRecord.JobKind.EXECUTE) {
            return "ok";
        }
        return "info";
    }

    private static String attentionTitle(JobEntity job, JobRecord.JobKind kind) {
        String status = job.getStatus();
        if ("FAILED".equals(status) && kind == JobRecord.JobKind.EXECUTE) {
            return "Execute run failed";
        }
        if ("FAILED".equals(status) && kind == JobRecord.JobKind.CONVERT) {
            return "Automate conversion failed";
        }
        if ("FAILED".equals(status) && kind == JobRecord.JobKind.HUNT) {
            return "Bug Hunter run failed";
        }
        if ("FAILED".equals(status)) {
            return "Run failed";
        }
        if ("COMPLETED_WITH_BLOCK".equals(status)) {
            return "Run completed with soft block";
        }
        if ("QUEUED".equals(status) || "RUNNING".equals(status)) {
            return kindLabel(kind) + " run in progress";
        }
        if (kind == JobRecord.JobKind.EXECUTE) {
            return "Design compare mismatch on passed run";
        }
        return "Needs review";
    }

    private static String attentionHref(JobEntity job, JobRecord.JobKind kind, Map<String, Object> links) {
        if (kind == JobRecord.JobKind.EXECUTE) {
            Object evidence = links.get("evidenceUrl");
            if (evidence != null) {
                return evidence.toString();
            }
            Object results = links.get("resultsUrl");
            if (results != null) {
                return results.toString();
            }
        }
        Object statusUrl = links.get("statusUrl");
        return statusUrl == null ? "/status?jobId=" + job.getJobId() : statusUrl.toString();
    }

    private static String kindLabel(JobRecord.JobKind kind) {
        return switch (kind) {
            case EXECUTE -> "Execute";
            case CONVERT -> "Automate";
            case HUNT -> "Bug Hunter";
            case GENERATE_BATCH -> "Generate";
            case GENERATE_COMPARE -> "Compare";
            default -> kind.name();
        };
    }

    private static final class ScoredAttention {
        private final int priority;
        private final JobEntity job;

        private ScoredAttention(int priority, JobEntity job) {
            this.priority = priority;
            this.job = job;
        }
    }
}
