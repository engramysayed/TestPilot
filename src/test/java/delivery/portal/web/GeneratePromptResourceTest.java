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
        "spring.datasource.url=jdbc:h2:mem:generateprompt;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "delivery.admin-email=admin@testpilot.local",
        "delivery.admin-password=ChangeMeAdmin1!",
        "delivery.dry-run=true",
        "delivery.emit-compile-check=false",
        "delivery.store-root=./target/test-delivery-store-generate-prompt",
        "delivery.work-dir=./target/test-delivery-work-generate-prompt"
})
@AutoConfigureMockMvc
public class GeneratePromptResourceTest extends AbstractTestNGSpringContextTests {

    private static final String PROMPT_PATH = "static/prompts/keel-tc-generate-from-stories-to-csv.txt";
    private static final String HTTP_PATH = "/prompts/keel-tc-generate-from-stories-to-csv.txt";
    private static final String HEADER =
            "TC_ID,Title,Steps,ExpectedResult,Preconditions,Priority,Tags,VisualAssertion,TestData,KeelPath";

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void classpathPrompt_containsKeelHeaderAndGenerateRules() throws Exception {
        try (var in = GeneratePromptResourceTest.class.getClassLoader().getResourceAsStream(PROMPT_PATH)) {
            Assert.assertNotNull(in, "prompt file missing on classpath");
            String body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertGeneratePromptContent(body);
        }
    }

    @Test
    public void httpGet_prompts_isPublicAndReturnsBody() throws Exception {
        String body = mockMvc.perform(get(HTTP_PATH))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        assertGeneratePromptContent(body);
    }

    private static void assertGeneratePromptContent(String body) {
        Assert.assertTrue(body.contains(HEADER), "missing exact CSV header");
        Assert.assertTrue(body.contains("KeelPath"), "must include KeelPath column");
        Assert.assertTrue(body.toLowerCase().contains("csv"), "must require CSV output");
        Assert.assertTrue(body.contains("5–15") || body.contains("5-15"), "batch guidance");

        String lower = body.toLowerCase();
        Assert.assertTrue(lower.contains("user stor") || lower.contains("acceptance criteria")
                || lower.contains("prd"), "must target stories/PRD/AC input");
        Assert.assertTrue(body.contains("TC_01") && body.contains("TC_02"),
                "must assign sequential TC_ID values");
        Assert.assertFalse(lower.contains("preserve existing tc_id"),
                "generate must assign ids, not preserve existing");
        Assert.assertFalse(lower.contains("rewrite only"),
                "generate must not be rewrite-only fidelity");

        Assert.assertTrue(lower.contains("testdata") || body.contains("TestData"), "TestData rules");
        Assert.assertTrue(lower.contains("file upload") || lower.contains("file-upload"),
                "must forbid file-upload-only cases");
        Assert.assertTrue(lower.contains("password") || lower.contains("<password>"),
                "must forbid real passwords / use placeholders");

        Assert.assertTrue(lower.contains("paste") && (lower.contains("stories")
                || lower.contains("acceptance criteria") || lower.contains("requirements")),
                "must end by telling user to paste stories next");
        Assert.assertTrue(body.contains("LOGIN & ASSERT AUTHORING"), "Execute-safe login rules");
        Assert.assertTrue(body.contains("Email or phone field"), "combined login field label");
        Assert.assertTrue(body.contains("Email / Mobile number"),
                "must map Email/Mobile number to Email or phone field");
        Assert.assertFalse(body.contains("e.g. \"Enter in the Email field\", \"Enter in the Password field\""),
                "must not recommend standalone Email field for combined forms");
        Assert.assertTrue(body.contains("TC_03") && body.contains("EXECUTE"),
                "malformed-input example with EXECUTE keelPath");
        Assert.assertFalse(lower.contains("```csv"),
                "prompt must not teach markdown fences as required output wrapper");

        Assert.assertTrue(body.contains("Import into project"),
                "must instruct paste into Keel Import into project");
        Assert.assertTrue(lower.contains("json") && lower.contains("preferred"),
                "CSV prompt must state JSON is preferred");
        Assert.assertTrue(body.contains("RFC4180"),
                "CSV multiline fields must reference RFC4180 quoting");
        Assert.assertTrue(lower.contains("real line break") || lower.contains("real line breaks"),
                "must require real line breaks inside quoted CSV fields");
        Assert.assertTrue(lower.contains("blank") && lower.contains("keelpath"),
                "blank KeelPath guidance required");
        Assert.assertTrue(lower.contains("automate") && lower.contains("execute"),
                "blank KeelPath must mention both Automate and Execute");
        Assert.assertFalse(body.contains("Excel interchange format"),
                "must not describe Excel-only interchange workflow");
        Assert.assertTrue(lower.contains("excel upload") && lower.contains("only path"),
                "must warn against Excel-only path");
    }
}
