package delivery.portal.api;

import delivery.excel.ManualTestCase;
import delivery.portal.PortalApplication;
import delivery.portal.persistence.PortalUser;
import delivery.portal.persistence.PortalUserRepository;
import delivery.portal.preflight.PreflightProbe;
import delivery.portal.service.GeneratedWorkbookService;
import delivery.portal.service.ProjectCredentialService;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** B14: GET /api/projects/{id}/preflight (ux-patches/BACKEND_NEEDS.md §1). */
@SpringBootTest(classes = {PortalApplication.class, ProjectPreflightApiTest.FakeProbe.class}, properties = {
        "spring.datasource.url=jdbc:h2:mem:preflightapi;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.main.allow-bean-definition-overriding=true",
        "delivery.admin-email=admin@testpilot.local",
        "delivery.admin-password=ChangeMeAdmin1!",
        "delivery.dry-run=true",
        "delivery.store-root=./target/test-delivery-store-preflight",
        "delivery.work-dir=./target/test-delivery-work-preflight"
})
@AutoConfigureMockMvc
public class ProjectPreflightApiTest extends AbstractTestNGSpringContextTests {

    @TestConfiguration
    static class FakeProbe {
        @Bean
        @Primary
        PreflightProbe fakePreflightProbe() {
            return url -> url.contains("down.example")
                    ? new PreflightProbe.Result(false, null, null, "Keel could not open the website.")
                    : new PreflightProbe.Result(true, 200, 12L, null);
        }
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private GeneratedWorkbookService workbooks;
    @Autowired private ProjectCredentialService credentials;
    @Autowired private PortalUserRepository users;
    @Autowired private PasswordEncoder encoder;

    @BeforeMethod
    public void secondCustomer() {
        if (!users.existsByEmailIgnoreCase("customer-pf@testpilot.local")) {
            PortalUser u = new PortalUser();
            u.setEmail("customer-pf@testpilot.local");
            u.setPasswordHash(encoder.encode("CustomerPf1!"));
            u.setRole(PortalUser.Role.USER);
            u.setEnabled(true);
            users.save(u);
        }
    }

    @Test
    public void healthyProjectIsOk() throws Exception {
        String id = createProject("https://app.example.test");
        workbooks.saveFromCases(id, List.of(
                new ManualTestCase("TC_01", "Login", "", "1. Open login", "1. OK", "P1", "", "", "", "AUTOMATE"),
                new ManualTestCase("TC_02", "Manual", "", "1. Look", "1. OK", "P1", "", "", "", "MANUAL")
        ), "TEST", "unit");
        credentials.create(id, "default", "user@test", "pw");
        mockMvc.perform(get("/api/projects/" + id + "/preflight").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId").value(id))
                .andExpect(jsonPath("$.reachable").value(true))
                .andExpect(jsonPath("$.httpStatus").value(200))
                .andExpect(jsonPath("$.login.profile").value("default"))
                .andExpect(jsonPath("$.login.checked").value(false))
                .andExpect(jsonPath("$.cases.total").value(2))
                .andExpect(jsonPath("$.cases.runnable").value(1))
                .andExpect(jsonPath("$.cases.manual").value(1))
                .andExpect(jsonPath("$.cases.byPath.AUTOMATE").value(1))
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.problems.length()").value(0));
    }

    @Test
    public void reportsUnreachableNoCasesAndUnknownProfile() throws Exception {
        String id = createProject("https://down.example.test");
        mockMvc.perform(get("/api/projects/" + id + "/preflight")
                        .param("credentialProfile", "nobody").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reachable").value(false))
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.problems[?(@.code=='UNREACHABLE')]").exists())
                .andExpect(jsonPath("$.problems[?(@.code=='NO_CASES')]").exists())
                // B18: Test cases tab is ?tab=tcs (was ?tab=cases, which opened Summary)
                .andExpect(jsonPath("$.problems[?(@.code=='NO_CASES')].fixHref").value("/projects/" + id + "?tab=tcs"))
                .andExpect(jsonPath("$.problems[?(@.code=='LOGIN_FAILED')]").exists())
                .andExpect(jsonPath("$.problems[0].fixHref").value("/projects/" + id + "?tab=settings"));
        // HUNT doesn't need cases
        mockMvc.perform(get("/api/projects/" + id + "/preflight").param("kind", "HUNT").with(admin()))
                .andExpect(jsonPath("$.problems[?(@.code=='NO_CASES')]").doesNotExist());
        mockMvc.perform(get("/api/projects/" + id + "/preflight").param("kind", "BOGUS").with(admin()))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void allManualCasesAreNotRunnable() throws Exception {
        String id = createProject("https://app.example.test");
        workbooks.saveFromCases(id, List.of(
                new ManualTestCase("TC_01", "Manual", "", "1. Look", "1. OK", "P1", "", "", "", "MANUAL")
        ), "TEST", "unit");
        mockMvc.perform(get("/api/projects/" + id + "/preflight").with(admin()))
                .andExpect(jsonPath("$.problems[?(@.code=='NO_RUNNABLE_CASES')]").exists())
                .andExpect(jsonPath("$.problems[?(@.code=='NO_RUNNABLE_CASES')].fixHref").value("/projects/" + id + "?tab=tcs"));
    }

    @Test
    public void crossTenantAndUnknownAre404() throws Exception {
        String id = createProject("https://app.example.test");
        mockMvc.perform(get("/api/projects/" + id + "/preflight")
                        .with(httpBasic("customer-pf@testpilot.local", "CustomerPf1!")))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/projects/prj_doesnotexist/preflight").with(admin()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/projects/" + id + "/preflight"))
                .andExpect(status().isUnauthorized());
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor admin() {
        return httpBasic("admin@testpilot.local", "ChangeMeAdmin1!");
    }

    private String createProject(String baseUrl) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/projects").with(admin())
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Preflight\",\"baseUrl\":\"" + baseUrl + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return new JSONObject(res.getResponse().getContentAsString()).getString("projectId");
    }
}
