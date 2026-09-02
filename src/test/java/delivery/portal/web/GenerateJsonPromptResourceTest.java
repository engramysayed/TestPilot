package delivery.portal.web;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;

public class GenerateJsonPromptResourceTest {

    private static final String PROMPT_PATH = "static/prompts/keel-tc-generate-from-stories-to-json.txt";

    @Test
    public void jsonPrompt_containsExecuteSafeLoginRules() throws Exception {
        try (var in = GenerateJsonPromptResourceTest.class.getClassLoader().getResourceAsStream(PROMPT_PATH)) {
            Assert.assertNotNull(in, "JSON prompt file missing on classpath");
            String body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            Assert.assertTrue(body.contains("LOGIN & ASSERT AUTHORING"));
            Assert.assertTrue(body.contains("Email or phone field"));
            Assert.assertTrue(body.contains("Leave the Email or phone field empty"));
            Assert.assertTrue(body.contains("Email / Mobile number"),
                    "must map Email/Mobile number to Email or phone field");
            Assert.assertFalse(body.contains("e.g. \"Enter in the Email field\", \"Enter in the Password field\""),
                    "must not recommend standalone Email field for combined forms");
            Assert.assertTrue(body.contains("clear validation/error state"));
            Assert.assertTrue(body.contains("TC_02"));
            Assert.assertTrue(body.contains("Empty email login validation"));
            Assert.assertTrue(body.contains("TC_03"));
            Assert.assertTrue(body.contains("Special characters"));
            Assert.assertTrue(body.contains("EXECUTE"));
        }
    }

    @Test
    public void jsonPrompt_mentionsImportAndBlankKeelPath() throws Exception {
        try (var in = GenerateJsonPromptResourceTest.class.getClassLoader().getResourceAsStream(PROMPT_PATH)) {
            Assert.assertNotNull(in, "JSON prompt file missing on classpath");
            String body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            String lower = body.toLowerCase();

            Assert.assertTrue(body.contains("Import into project"),
                    "must instruct paste into Keel Import into project");
            Assert.assertTrue(body.contains("JSON") && lower.contains("preferred"),
                    "JSON must be preferred format");
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
}
