package delivery.portal.service;

import delivery.portal.persistence.JobEntity;
import delivery.portal.persistence.JobRepository;
import delivery.portal.persistence.ProjectEntity;
import delivery.portal.persistence.ProjectRepository;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DashboardServiceTest {
    private static final Long OWNER = 42L;

    private DashboardService service(ProjectRepository projects, JobRepository jobs) {
        ExecuteRunService executeRunService = mock(ExecuteRunService.class);
        JobLinkEnricher jobLinks = mock(JobLinkEnricher.class);
        doAnswer(inv -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> row = inv.getArgument(0);
            row.putIfAbsent("statusUrl", "/status?jobId=" + inv.getArgument(1));
            return null;
        }).when(jobLinks).enrich(any(), anyString(), any(), anyString());
        return new DashboardService(projects, jobs, executeRunService, jobLinks);
    }

    @Test
    public void statsFor_excludesArchivedFromActiveCount() {
        ProjectEntity active = project("p-active", "Active", false);
        ProjectEntity archived = project("p-archived", "Archived", true);

        ProjectRepository projects = mock(ProjectRepository.class);
        JobRepository jobs = mock(JobRepository.class);
        when(projects.findByOwnerUserIdOrderByIdDesc(OWNER)).thenReturn(List.of(active, archived));
        when(jobs.findByOwnerUserIdOrderByCreatedAtDesc(OWNER)).thenReturn(List.of());

        Map<String, Object> stats = service(projects, jobs).statsFor(OWNER);

        Assert.assertEquals(stats.get("activeProjectCount"), 1);
        Assert.assertEquals(stats.get("projectCount"), 2);
        Assert.assertEquals(stats.get("hasProjects"), true);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> options = (List<Map<String, Object>>) stats.get("projectOptions");
        Assert.assertEquals(options.size(), 1);
        Assert.assertEquals(options.get(0).get("projectId"), "p-active");
    }

    @Test
    public void statsFor_simulatedPassesDoNotInflatePassRate() {
        JobEntity simulated = job("j-sim", "COMPLETED");
        simulated.setJobKind("CONVERT");
        simulated.setPassedCount(10);
        simulated.setTodoCount(0);
        simulated.setMessage("dry-run completed");
        JobEntity live = job("j-live", "COMPLETED");
        live.setJobKind("CONVERT");
        live.setPassedCount(1);
        live.setTodoCount(1);
        live.setMessage("proven on live browser");

        ProjectRepository projects = mock(ProjectRepository.class);
        JobRepository jobs = mock(JobRepository.class);
        when(projects.findByOwnerUserIdOrderByIdDesc(OWNER)).thenReturn(List.of());
        when(jobs.findByOwnerUserIdOrderByCreatedAtDesc(OWNER)).thenReturn(List.of(simulated, live));

        Map<String, Object> stats = service(projects, jobs).statsFor(OWNER);

        Assert.assertEquals(stats.get("passRate"), 50);
        Assert.assertEquals(stats.get("simulatedTotal"), 10);
        Assert.assertEquals(stats.get("provenPassed"), 1);
        Assert.assertTrue(String.valueOf(stats.get("passRateNote")).toLowerCase().contains("simulated"));
    }

    @Test
    public void statsFor_emptyDashboardDoesNotTreatZeroOverZeroAsAMeasuredRate() {
        ProjectRepository projects = mock(ProjectRepository.class);
        JobRepository jobs = mock(JobRepository.class);
        when(projects.findByOwnerUserIdOrderByIdDesc(OWNER)).thenReturn(List.of());
        when(jobs.findByOwnerUserIdOrderByCreatedAtDesc(OWNER)).thenReturn(List.of());

        Map<String, Object> stats = service(projects, jobs).statsFor(OWNER);

        Assert.assertEquals(stats.get("hasProvenSample"), false);
        Assert.assertEquals(stats.get("passRateNote"), "No proven cases yet");
        Assert.assertFalse(String.valueOf(stats.get("passRateNote")).contains("0/0"));
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

        Map<String, Object> stats = service(projects, jobs).statsFor(OWNER);

        Assert.assertEquals(stats.get("runningJobs"), 2);
        Assert.assertEquals(stats.get("jobCount"), 3);
        Assert.assertEquals(stats.get("hasJobs"), true);
    }

    @Test
    public void attentionFor_prioritizesFailedExecute() {
        JobEntity failedExecute = job("exec_fail", "FAILED");
        failedExecute.setJobKind("EXECUTE");
        JobEntity running = job("conv_run", "RUNNING");

        ProjectRepository projects = mock(ProjectRepository.class);
        JobRepository jobs = mock(JobRepository.class);
        when(projects.findByOwnerUserIdOrderByIdDesc(OWNER)).thenReturn(List.of(project("p1", "Demo", false)));
        when(jobs.findByOwnerUserIdOrderByCreatedAtDesc(OWNER)).thenReturn(List.of(running, failedExecute));

        ExecuteRunService executeRunService = mock(ExecuteRunService.class);
        JobLinkEnricher jobLinks = new JobLinkEnricher(executeRunService);
        DashboardService dashboard = new DashboardService(projects, jobs, executeRunService, jobLinks);

        List<Map<String, Object>> items = dashboard.attentionFor(OWNER);
        Assert.assertFalse(items.isEmpty());
        Assert.assertEquals(items.get(0).get("jobId"), "exec_fail");
        Assert.assertEquals(items.get(0).get("severity"), "error");
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
        j.setJobKind("CONVERT");
        return j;
    }
}
