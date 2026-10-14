package delivery.portal.web;

import delivery.portal.PortalApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.springframework.test.web.servlet.MockMvc;
import org.testng.annotations.Test;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** B20: server-side nav search labels and topbar crumbs use the UX_AUDIT plain-language names. */
@SpringBootTest(classes = PortalApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:plain-nav;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "delivery.admin-email=admin@testpilot.local",
        "delivery.admin-password=ChangeMeAdmin1!",
        "delivery.dry-run=true",
        "delivery.store-root=./target/test-delivery-store-plain-nav",
        "delivery.work-dir=./target/test-delivery-work-plain-nav"
})
@AutoConfigureMockMvc
public class PlainLanguageNavTest extends AbstractTestNGSpringContextTests {
    @Autowired private MockMvc mockMvc;

    private java.util.List<String> labels(String q) throws Exception {
        String body = mockMvc.perform(get("/api/portal/search").param("q", q)
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        org.json.JSONArray arr = new org.json.JSONArray(body);
        java.util.List<String> out = new java.util.ArrayList<>();
        for (int i = 0; i < arr.length(); i++) out.add(arr.getJSONObject(i).getString("label"));
        return out;
    }

    private void search(String q, String label) throws Exception {
        org.testng.Assert.assertTrue(labels(q).contains(label), q + " -> " + labels(q));
    }

    @Test
    public void searchReturnsNewLabelsForNewAndOldWords() throws Exception {
        search("run tests", "Run tests");
        search("execute", "Run tests");
        search("build code", "Build code");
        search("automate", "Build code");
        search("write test cases", "Write test cases");
        search("history", "Run history");
        search("code packages", "Code packages");
        search("how to write", "How to write test cases");
        java.util.List<String> all = labels("e");
        org.testng.Assert.assertFalse(all.contains("Runs & evidence"), all.toString());
        org.testng.Assert.assertFalse(all.contains("TC guide"), all.toString());
    }

    @Test
    public void pageTitlesUsePlainNames() throws Exception {
        String[][] pages = {{"/execute", "Run tests"}, {"/generate", "Write test cases"}, {"/automate", "Build code"},
                {"/runs", "Run history"}, {"/packages", "Code packages"}, {"/tc-guide", "How to write test cases"}};
        for (String[] p : pages) {
            mockMvc.perform(get(p[0]).with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!")))
                    .andExpect(status().isOk())
                    .andExpect(model().attribute("pageTitle", p[1]));
        }
    }
}
