package delivery.portal.service;

import delivery.portal.PortalApplication;
import delivery.portal.model.JobRecord;
import delivery.portal.persistence.PortalUserRepository;
import delivery.portal.worker.JobLeaseReconciler;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Locks the 2026-09-17 16:04 startup crash (PID 12120, Maven 1520) where
 * {@code reconcileExpiredLeases} used {@code ConcurrentHashMap.computeIfAbsent}
 * while {@code hydrate} also {@code put} the same map. That failed with
 * {@code IllegalStateException: Recursive update} <em>before</em> {@code 6969693}
 * (16:07:01 +03). Restarts after that SHA (PID 34332 at 16:07, PID 31240 at 17:27)
 * started without the recursive-update crash.
 */
@SpringBootTest(classes = PortalApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:hydrate-reconcile;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "delivery.admin-email=admin@testpilot.local",
        "delivery.admin-password=ChangeMeAdmin1!",
        "delivery.dry-run=true",
        "delivery.store-root=./target/test-delivery-store-hydrate-reconcile",
        "delivery.work-dir=./target/test-delivery-work-hydrate-reconcile"
})
@AutoConfigureMockMvc
public class PortalStoreHydrateReconcileTest extends AbstractTestNGSpringContextTests {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private PortalStore store;
    @Autowired
    private PortalUserRepository users;
    @Autowired
    private JobLeaseReconciler reconciler;

    @BeforeMethod
    public void cleanStore() throws Exception {
        Path root = Path.of("./target/test-delivery-store-hydrate-reconcile");
        if (Files.exists(root)) {
            try (var walk = Files.walk(root)) {
                walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (Exception ignored) {
                    }
                });
            }
        }
    }

    @Test
    public void reconcileExpiredLeasesHydratesPersistedJobsWithoutRecursiveUpdate() throws Exception {
        String projectId = createProject();
        JobRecord job = new JobRecord(
                "job_hydrate_1", projectId, adminId(), "EXECUTE",
                Path.of("src/test/resources/delivery/sample-manual-tcs.xlsx"),
                "https://staging.example/", "", "", false, JobRecord.JobKind.EXECUTE);
        store.saveJob(job);
        clearInMemoryJobs();

        int n = store.reconcileExpiredLeases();
        Assert.assertTrue(n >= 0);
        Assert.assertTrue(store.getJob("job_hydrate_1").isPresent(),
                "persisted job must hydrate after empty in-memory map (restart)");

        clearInMemoryJobs();
        reconciler.run(null);
        Assert.assertEquals(store.getJob("job_hydrate_1").orElseThrow().getJobId(), "job_hydrate_1");
    }

    @SuppressWarnings("unchecked")
    private void clearInMemoryJobs() throws Exception {
        PortalStore target = AopTestUtils.getUltimateTargetObject(store);
        Field field = PortalStore.class.getDeclaredField("jobs");
        field.setAccessible(true);
        ((Map<String, JobRecord>) field.get(target)).clear();
    }

    private String createProject() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/projects")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Hydrate Reconcile\",\"baseUrl\":\"https://staging.example/\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return new JSONObject(created.getResponse().getContentAsString()).getString("projectId");
    }

    private long adminId() {
        return users.findByEmailIgnoreCase("admin@testpilot.local").orElseThrow().getId();
    }
}
