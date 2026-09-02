package delivery.portal.service;

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
import java.util.HashMap;
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

    public DashboardService(ProjectRepository projects, JobRepository jobs) {
        this.projects = projects;
        this.jobs = jobs;
    }

    public Map<String, Object> statsFor(Long ownerUserId) {
        List<ProjectEntity> projectList = projects.findByOwnerUserIdOrderByIdDesc(ownerUserId);
        List<JobEntity> allJobs = jobs.findByOwnerUserIdOrderByCreatedAtDesc(ownerUserId);
        List<JobEntity> convertJobs = allJobs.stream()
                .filter(j -> JobRecord.parseJobKind(j.getJobKind()) == JobRecord.JobKind.CONVERT)
                .toList();
        int passed = convertJobs.stream().mapToInt(JobEntity::getPassedCount).sum();
        int todo = convertJobs.stream().mapToInt(JobEntity::getTodoCount).sum();
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
        int passRate = (passed + todo) > 0 ? Math.round(100f * passed / (passed + todo)) : 0;

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
        // chronological for chart
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
            Map<String, Object> row = new HashMap<>();
            row.put("jobId", j.getJobId());
            row.put("projectId", j.getProjectId());
            row.put("projectName", projectNames.getOrDefault(j.getProjectId(), j.getProjectId()));
            row.put("mode", j.getMode());
            row.put("status", j.getStatus());
            row.put("passedCount", j.getPassedCount());
            row.put("todoCount", j.getTodoCount());
            Instant when = j.getCompletedAt() != null ? j.getCompletedAt() : j.getCreatedAt();
            row.put("createdAt", when == null ? "" : WHEN.format(when));
            row.put("day", when == null ? "" : DAY.format(when));
            row.put("downloadable", JobRecord.isDownloadable(JobRecord.parseJobKind(j.getJobKind()), j.getStatus()));
            recentJobs.add(row);
        }

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
        map.put("statusCompleted", statusCompleted);
        map.put("statusBlocked", statusBlocked);
        map.put("statusFailed", statusFailed);
        map.put("statusRunning", statusRunning);
        map.put("statusQueued", statusQueued);
        map.put("chartLabels", labels);
        map.put("chartPassed", passedSeries);
        map.put("chartTodo", todoSeries);
        map.put("recentJobs", recentJobs);
        map.put("projectOptions", projectList.stream()
                .filter(p -> !p.isArchived())
                .map(p -> Map.<String, Object>of("projectId", p.getProjectId(), "name", p.getName()))
                .collect(Collectors.toList()));
        return map;
    }
}
