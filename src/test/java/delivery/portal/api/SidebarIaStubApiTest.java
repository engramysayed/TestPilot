package delivery.portal.api;

import delivery.portal.PortalApplication;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testng.Assert;
import org.testng.annotations.Test;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = PortalApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:sidebariaapi;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "delivery.admin-email=admin@testpilot.local",
        "delivery.admin-password=ChangeMeAdmin1!",
        "delivery.dry-run=true",
        "delivery.emit-compile-check=false",
        "delivery.sidebar.ia-enabled=true",
        "delivery.roles.ui-enabled=true",
        "delivery.store-root=./target/test-delivery-store-sidebariaapi",
        "delivery.work-dir=./target/test-delivery-work-sidebariaapi"
})
@AutoConfigureMockMvc
public class SidebarIaStubApiTest extends AbstractTestNGSpringContextTests {

    private static final String USER = "admin@testpilot.local";
    private static final String PASS = "ChangeMeAdmin1!";

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void accountCredits_finalShape() throws Exception {
        MvcResult res = mockMvc.perform(get("/api/account/credits")
                        .with(httpBasic(USER, PASS))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk()).andReturn();
        JSONObject body = new JSONObject(res.getResponse().getContentAsString());
        Assert.assertTrue(body.has("balance"));
        Assert.assertTrue(body.has("costs"));
        Assert.assertEquals(body.getJSONObject("costs").getLong("generate"), 10L);
        Assert.assertEquals(body.getJSONObject("costs").getLong("automate"), 25L);
    }

    @Test
    public void accountJobs_acceptsPinnedProject() throws Exception {
        MvcResult res = mockMvc.perform(post("/api/account/jobs")
                        .with(httpBasic(USER, PASS))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"GENERATE\",\"projectId\":\"proj_demo\",\"payload\":{}}"))
                .andExpect(status().isCreated()).andReturn();
        JSONObject body = new JSONObject(res.getResponse().getContentAsString());
        Assert.assertEquals(body.getString("kind"), "GENERATE");
        Assert.assertTrue(body.getBoolean("accountScoped"));
        Assert.assertTrue(body.getString("jobId").startsWith("job_"));
    }

    @Test
    public void mePermissions_adminApproved() throws Exception {
        MvcResult res = mockMvc.perform(get("/api/me/permissions")
                        .with(httpBasic(USER, PASS))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk()).andReturn();
        JSONObject body = new JSONObject(res.getResponse().getContentAsString());
        Assert.assertEquals(body.getString("approvalStatus"), "APPROVED");
        Assert.assertTrue(body.getJSONArray("permissions").length() > 0);
    }
}
