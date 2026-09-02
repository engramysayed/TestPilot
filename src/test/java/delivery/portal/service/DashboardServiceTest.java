package delivery.portal.service;

import delivery.portal.persistence.JobEntity;
import delivery.portal.persistence.JobRepository;
import delivery.portal.persistence.ProjectEntity;
import delivery.portal.persistence.ProjectRepository;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DashboardServiceTest {
    private static final Long OWNER = 42L;

    @Test
    public void statsFor_excludesArchivedFromActiveCount() {
        ProjectEntity active = project("p-active", "Active", false);
        ProjectEntity archived = project("p-archived", "Archived", true);

        ProjectRepository projects = mock(ProjectRepository.class);
        JobRepository jobs = mock(JobRepository.class);
        when(projects.findByOwnerUserIdOrderByIdDesc(OWNER)).thenReturn(List.of(active, archived));
        when(jobs.findByOwnerUserIdOrderByCreatedAtDesc(OWNER)).thenReturn(List.of());

        Map<String, Object> stats = new DashboardService(projects, jobs).statsFor(OWNER);

        Assert.assertEquals(stats.get("activeProjectCount"), 1);
        Assert.assertEquals(stats.get("projectCount"), 2);
        Assert.assertEquals(stats.get("hasProjects"), true);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> options = (List<Map<String, Object>>) stats.get("projectOptions");
        Assert.assertEquals(options.size(), 1);
        Assert.assertEquals(options.get(0).get("projectId"), "p-active");
    }

    @Test
    public void statsFor_countsRunningJobs() {
        JobEntity queued = job("j1", "QUEUED");
        JobEntity running = job("j2", "RUNNING");
        JobEntity completed = job("j3", "COMPLETED");

        ProjectRepository projects = mock(ProjectRepository.class);
        JobRepository jobs = mock(JobRepository.class);
        when(projects.findByOwnerUserIdOrderByIdDesc(OWNER)).thenReturn(List.of());
        when(jobs.findByOwnerUserIdOrderByCreatedAtDesc(OWNER)).thenReturn(List.of(queued, running, completed));

        Map<String, Object> stats = new DashboardService(projects, jobs).statsFor(OWNER);

        Assert.assertEquals(stats.get("runningJobs"), 2);
        Assert.assertEquals(stats.get("jobCount"), 3);
        Assert.assertEquals(stats.get("hasJobs"), true);
    }

    private static ProjectEntity project(String id, String name, boolean archived) {
        ProjectEntity p = new ProjectEntity();
        p.setProjectId(id);
        p.setName(name);
        p.setOwnerUserId(OWNER);
        p.setArchived(archived);
        return p;
    }

    private static JobEntity job(String jobId, String status) {
        JobEntity j = new JobEntity();
        j.setJobId(jobId);
        j.setProjectId("p1");
        j.setOwnerUserId(OWNER);
        j.setMode("dry-run");
        j.setStatus(status);
        return j;
    }
}
