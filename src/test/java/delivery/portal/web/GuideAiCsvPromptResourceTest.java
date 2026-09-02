package delivery.portal.web;

import delivery.portal.PortalApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.springframework.test.web.servlet.MockMvc;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = PortalApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:guideaicsv;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "delivery.admin-email=admin@testpilot.local",
        "delivery.admin-password=ChangeMeAdmin1!",
        "delivery.dry-run=true",
        "delivery.emit-compile-check=false",
        "delivery.store-root=./target/test-delivery-store-guide-ai",
        "delivery.work-dir=./target/test-delivery-work-guide-ai"
})
@AutoConfigureMockMvc
public class GuideAiCsvPromptResourceTest extends AbstractTestNGSpringContextTests {

    private static final String HEADER =
            "TC_ID,Title,Steps,ExpectedResult,Preconditions,Priority,Tags,VisualAssertion,TestData";

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void classpathPrompt_containsKeelHeaderAndFidelityRules() throws Exception {
        try (var in = GuideAiCsvPromptResourceTest.class.getClassLoader()
                .getResourceAsStream("static/prompts/keel-tc-rewrite-to-csv.txt")) {
            Assert.assertNotNull(in, "prompt file missing on classpath");
            String body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            Assert.assertTrue(body.contains(HEADER), "missing exact CSV header");
            Assert.assertTrue(body.toLowerCase().contains("csv"), "must require CSV output");
            Assert.assertTrue(body.contains("5–15") || body.contains("5-15"), "batch guidance");
            Assert.assertTrue(body.contains("TC_ID"), "preserve ids");
            Assert.assertTrue(body.toLowerCase().contains("testdata")
                    || body.contains("TestData"), "TestData rules");
            Assert.assertFalse(body.toLowerCase().contains("```csv"),
                    "prompt must not teach markdown fences as required output wrapper");
        }
    }

    @Test
    public void httpGet_prompts_isPublicAndReturnsBody() throws Exception {
        String body = mockMvc.perform(get("/prompts/keel-tc-rewrite-to-csv.txt"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Assert.assertTrue(body.contains(HEADER));
    }
}
