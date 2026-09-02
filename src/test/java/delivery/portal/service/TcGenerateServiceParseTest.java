package delivery.portal.service;

import delivery.excel.ManualTestCase;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

public class TcGenerateServiceParseTest {

    private static final String SAMPLE_JSON = """
            {
              "testCases": [
                {
                  "tcId": "TC_01",
                  "title": "Login",
                  "preconditions": "",
                  "steps": "1. Open login",
                  "expectedResult": "Shown",
                  "priority": "",
                  "tags": "",
                  "visualAssertion": "",
                  "testData": "",
                  "keelPath": "AUTOMATE"
                }
              ],
              "coverageNotes": "Smoke."
            }
            """;

    private static final String SAMPLE_CSV = """
            TC_ID,Title,Steps,ExpectedResult,Preconditions,Priority,Tags,VisualAssertion,TestData,KeelPath
            TC_01,Login,1. Open login,Shown,,,,,,AUTOMATE
            ---KEEL_COVERAGE---
            Smoke.
            """;

    @Test
    public void parseLlmOutput_prefersJson() {
        TcGenerateService.ParsedLlmOutput parsed = TcGenerateService.parseLlmOutput(SAMPLE_JSON);
        List<ManualTestCase> cases = parsed.cases();
        Assert.assertEquals(cases.size(), 1);
        Assert.assertEquals(cases.get(0).keelPath(), "AUTOMATE");
        Assert.assertEquals(parsed.coverageNotes(), "Smoke.");
    }

    @Test
    public void parseLlmOutput_fallsBackToCsv() {
        TcGenerateService.ParsedLlmOutput parsed = TcGenerateService.parseLlmOutput(SAMPLE_CSV);
        List<ManualTestCase> cases = parsed.cases();
        Assert.assertEquals(cases.size(), 1);
        Assert.assertEquals(cases.get(0).tcId(), "TC_01");
        Assert.assertTrue(parsed.coverageNotes().contains("Smoke"));
    }

    @Test
    public void parseLlmOutput_jsonWithUnescapedNewlinesInSteps_parses() {
        String gemmaStyle = """
                {
                  "testCases": [
                    {
                      "tcId": "TC_01",
                      "title": "Invalid login",
                      "preconditions": "No login required.",
                      "steps": "1. Open the Login Page at /login
                2. Enter in the Email or phone field
                3. Click Log in",
                      "expectedResult": "1. Login page is shown
                2. Identifier is accepted
                3. Error is shown",
                      "priority": "P1",
                      "tags": "negative",
                      "visualAssertion": "",
                      "testData": "user@example.com",
                      "keelPath": "AUTOMATE"
                    }
                  ],
                  "coverageNotes": "Negative login only."
                }
                """;
        TcGenerateService.ParsedLlmOutput parsed = TcGenerateService.parseLlmOutput(gemmaStyle);
        Assert.assertEquals(parsed.cases().size(), 1);
        Assert.assertEquals(parsed.cases().get(0).tcId(), "TC_01");
        Assert.assertTrue(parsed.cases().get(0).steps().contains("\n2. Enter"));
    }

    @Test
    public void parseLlmOutput_jsonWithoutTestCases_doesNotReportMissingTcid() {
        try {
            TcGenerateService.parseLlmOutput("{\"coverageNotes\":\"none\"}");
            Assert.fail("expected parse failure");
        } catch (IllegalArgumentException e) {
            Assert.assertFalse(e.getMessage().contains("Missing CSV column: TCID"), e.getMessage());
            Assert.assertTrue(
                    e.getMessage().contains("testCases") || e.getMessage().contains("JSON"),
                    e.getMessage());
        }
    }
}
