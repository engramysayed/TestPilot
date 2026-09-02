package delivery.portal.api;

import delivery.portal.PortalApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.springframework.test.web.servlet.MockMvc;
import org.testng.annotations.Test;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = PortalApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:generatemodelsapi;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "delivery.admin-email=admin@testpilot.local",
        "delivery.admin-password=ChangeMeAdmin1!",
        "delivery.dry-run=true",
        "delivery.generate-model=gemma4:e2b",
        "delivery.generate-models=gemma4:e2b,qwen2.5:latest",
        "delivery.store-root=./target/test-delivery-store-generatemodelsapi",
        "delivery.work-dir=./target/test-delivery-work-generatemodelsapi"
})
@AutoConfigureMockMvc
public class GenerateConfigApiTest extends AbstractTestNGSpringContextTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void listModels_returnsConfiguredChoices() throws Exception {
        mockMvc.perform(get("/api/generate/models")
                        .with(httpBasic("admin@testpilot.local", "ChangeMeAdmin1!"))
                        .header("X-Keel-Requested-With", "Keel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultModel").value("gemma4:e2b"))
                .andExpect(jsonPath("$.models[0]").value("gemma4:e2b"))
                .andExpect(jsonPath("$.models[1]").value("qwen2.5:latest"));
    }
}
