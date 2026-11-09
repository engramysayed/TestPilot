package delivery.portal.tunnel;

import delivery.portal.DeliveryPortalProperties;
import delivery.portal.persistence.ProjectEntity;
import delivery.portal.persistence.JobRepository;
import delivery.portal.persistence.ProjectRepository;
import delivery.portal.service.PortalStore;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TunnelJobBaseUrlServiceTest {

    @Test
    public void relayBaseUrlWhenActive() throws Exception {
        Path tmp = Files.createTempDirectory("tunnel-job-base-url");
        String projectId = "proj_tunnel";
        DeliveryPortalProperties props = new DeliveryPortalProperties();
        props.setStoreRoot(tmp.toString());
        ProjectEntity project = new ProjectEntity();
        project.setProjectId(projectId);
        project.setOwnerUserId(1L);
        ProjectRepository projects = mock(ProjectRepository.class);
        JobRepository jobs = mock(JobRepository.class);
        when(projects.findByProjectId(projectId)).thenReturn(Optional.of(project));
        when(jobs.findByProjectId(projectId)).thenReturn(List.of());
        PortalStore store = new PortalStore(props, projects, jobs);
        Path projectRoot = store.projectDiskRoot(projectId);
        Files.createDirectories(projectRoot);
        TunnelEnrollmentDiskStore disk = new TunnelEnrollmentDiskStore();
        disk.save(projectRoot, tmp, "abc123", projectId, "u", "sec", "pending");
        disk.markActive(projectRoot, "http://127.0.0.1:4040");
        TunnelJobBaseUrlService svc = new TunnelJobBaseUrlService(store);
        ReflectionTestUtils.setField(svc, "tunnelEnabled", true);
        ReflectionTestUtils.setField(svc, "publicBaseUrl", "https://portal.example.com");
        Assert.assertTrue(svc.relayBaseUrl(projectId).isPresent());
        Assert.assertEquals(svc.relayBaseUrl(projectId).get(), "https://portal.example.com/tunnel/p/abc123/");
        Assert.assertEquals(
                svc.effectiveBaseUrl(projectId, "http://ignored"),
                "https://portal.example.com/tunnel/p/abc123/");
    }

    @Test
    public void noRelayWhenHeartbeatStale() throws Exception {
        Path tmp = Files.createTempDirectory("tunnel-job-base-url-stale");
        String projectId = "proj_tunnel_stale";
        DeliveryPortalProperties props = new DeliveryPortalProperties();
        props.setStoreRoot(tmp.toString());
        ProjectEntity project = new ProjectEntity();
        project.setProjectId(projectId);
        project.setOwnerUserId(1L);
        ProjectRepository projects = mock(ProjectRepository.class);
        JobRepository jobs = mock(JobRepository.class);
        when(projects.findByProjectId(projectId)).thenReturn(Optional.of(project));
        when(jobs.findByProjectId(projectId)).thenReturn(List.of());
        PortalStore store = new PortalStore(props, projects, jobs);
        Path projectRoot = store.projectDiskRoot(projectId);
        Files.createDirectories(projectRoot);
        TunnelEnrollmentDiskStore disk = new TunnelEnrollmentDiskStore();
        disk.save(projectRoot, tmp, "abc999", projectId, "u", "sec", "pending");
        disk.markActive(projectRoot, "http://127.0.0.1:4040");
        TunnelJobBaseUrlService svc = new TunnelJobBaseUrlService(store);
        ReflectionTestUtils.setField(svc, "tunnelEnabled", true);
        ReflectionTestUtils.setField(svc, "publicBaseUrl", "https://portal.example.com");
        ReflectionTestUtils.setField(svc, "heartbeatTtlSeconds", 1L);
        Thread.sleep(1_500);
        Assert.assertTrue(svc.relayBaseUrl(projectId).isEmpty(), "stale tunnel must not hijack execute baseUrl");
        Assert.assertEquals(svc.effectiveBaseUrl(projectId, "http://app.local"), "http://app.local");
    }
}
