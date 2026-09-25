package delivery.portal.web;

import delivery.portal.PortalApplication;
import org.json.JSONArray;
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
        "spring.datasource.url=jdbc:h2:mem:portalnavsearch;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "delivery.admin-email=admin@testpilot.local",
        "delivery.admin-password=ChangeMeAdmin1!",
        "delivery.dry-run=true",
        "delivery.emit-compile-check=false",
        "delivery.store-root=./target/test-delivery-store-portalnavsearch",
        "delivery.work-dir=./target/test-delivery-work-portalnavsearch"
})
@AutoConfigureMockMvc
public class PortalNavSearchMvcTest extends AbstractTestNGSpringContextTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void search_findsWorkspacePages() throws Exception {
        MvcResult res = mockMvc.perform(get("/api/portal/search")
                        .param("q", "runs")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk())
                .andReturn();
        JSONArray hits = new JSONArray(res.getResponse().getContentAsString());
        Assert.assertTrue(hits.length() > 0);
        Assert.assertTrue(hits.getJSONObject(0).getString("url").contains("/runs"));
    }

    @Test
    public void search_findsProjectByName() throws Exception {
        mockMvc.perform(post("/api/projects")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Commerce Checkout\",\"baseUrl\":\"https://shop.test\"}"))
                .andExpect(status().isCreated());

        MvcResult res = mockMvc.perform(get("/api/portal/search")
                        .param("q", "Commerce")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk())
                .andReturn();
        JSONArray hits = new JSONArray(res.getResponse().getContentAsString());
        Assert.assertEquals(hits.getJSONObject(0).getString("kind"), "project");
    }

    @Test
    public void dashboard_includesGlobalSearch() throws Exception {
        String body = mockMvc.perform(get("/dashboard")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Assert.assertTrue(body.contains("id=\"globalSearch\""));
        Assert.assertTrue(body.contains("portal-nav.js"));
    }
}
