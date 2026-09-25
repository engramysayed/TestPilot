package delivery.portal.web;

import delivery.portal.PortalApplication;
import org.json.JSONArray;
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
        "spring.datasource.url=jdbc:h2:mem:projectlistkpi;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "delivery.admin-email=admin@testpilot.local",
        "delivery.admin-password=ChangeMeAdmin1!",
        "delivery.dry-run=true",
        "delivery.emit-compile-check=false",
        "delivery.store-root=./target/test-delivery-store-projectlistkpi",
        "delivery.work-dir=./target/test-delivery-work-projectlistkpi"
})
@AutoConfigureMockMvc
public class ProjectListKpiMvcTest extends AbstractTestNGSpringContextTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void listProjects_includesCardKpiFields() throws Exception {
        mockMvc.perform(post("/api/projects")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"KPI Demo\"}"))
                .andExpect(status().isCreated());

        MvcResult res = mockMvc.perform(get("/api/projects")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk())
                .andReturn();

        JSONArray list = new JSONArray(res.getResponse().getContentAsString());
        Assert.assertTrue(list.length() > 0);
        JSONObject row = list.getJSONObject(0);
        Assert.assertTrue(row.has("libraryCaseCount"));
        Assert.assertTrue(row.has("provenPassRatePercent"));
        Assert.assertEquals(row.getString("platform"), "web");
    }

    @Test
    public void projectsPage_includesKpiMarkupHooks() throws Exception {
        String body = mockMvc.perform(get("/projects")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Assert.assertTrue(body.contains("projectcard-progress"));
        Assert.assertTrue(body.contains("libraryCaseCount"));
    }
}
