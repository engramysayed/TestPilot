package delivery.excel;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

public class GeneratedTcJsonParserTest {

    private static final String HAPPY_JSON = """
            {
              "testCases": [
                {
                  "tcId": "TC_01",
                  "title": "Login ok",
                  "preconditions": "Login required.",
                  "steps": "1. Open login\\n2. Enter user",
                  "expectedResult": "1. Page shown\\n2. Accepted",
                  "priority": "High",
                  "tags": "smoke",
                  "visualAssertion": "",
                  "testData": "user",
                  "keelPath": "EXECUTE"
                },
                {
                  "tcId": "TC_02",
                  "title": "Upload file",
                  "preconditions": "None",
                  "steps": "1. Choose file",
                  "expectedResult": "Error shown",
                  "priority": "P2",
                  "tags": "negative",
                  "visualAssertion": "",
                  "testData": "",
                  "keelPath": "MANUAL"
                }
              ],
              "coverageNotes": "Applied boundary and negative paths."
            }
            """;

    @Test
    public void parse_happyJson_extractsCasesAndCoverageNotes() {
        GeneratedTcJsonParser.ParseResult result = GeneratedTcJsonParser.parse(HAPPY_JSON);
        List<ManualTestCase> cases = result.cases();
        Assert.assertEquals(cases.size(), 2);
        Assert.assertEquals(cases.get(0).tcId(), "TC_01");
        Assert.assertEquals(cases.get(0).keelPath(), "EXECUTE");
        Assert.assertTrue(cases.get(0).steps().contains("\n2. Enter"));
        Assert.assertEquals(cases.get(1).keelPath(), "MANUAL");
        Assert.assertTrue(result.coverageNotes().contains("boundary"));
    }

    @Test
    public void parse_markdownFencedJson_stripsFence() {
        String fenced = """
                Here is the batch:
                ```json
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
                  "coverageNotes": "Smoke only."
                }
                ```
                """;
        GeneratedTcJsonParser.ParseResult result = GeneratedTcJsonParser.parse(fenced);
        Assert.assertEquals(result.cases().size(), 1);
        Assert.assertEquals(result.cases().get(0).keelPath(), "AUTOMATE");
        Assert.assertEquals(result.coverageNotes(), "Smoke only.");
    }

    @Test
    public void parse_unescapedNewlinesInSteps_keepsLineBreaks() {
        String gemmaStyle = """
                {
                  "testCases": [
                    {
                      "tcId": "TC_01",
                      "title": "Invalid login",
                      "preconditions": "",
                      "steps": "1. Open login
                2. Enter user",
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
        GeneratedTcJsonParser.ParseResult result = GeneratedTcJsonParser.parse(gemmaStyle);
        Assert.assertEquals(result.cases().size(), 1);
        Assert.assertTrue(result.cases().get(0).steps().contains("\n2. Enter"));
    }

    @Test
    public void parse_testCasesSnakeCaseAlias() {
        String aliased = """
                {
                  "test_cases": [
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
        GeneratedTcJsonParser.ParseResult result = GeneratedTcJsonParser.parse(aliased);
        Assert.assertEquals(result.cases().size(), 1);
        Assert.assertEquals(result.cases().get(0).tcId(), "TC_01");
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void parse_nonJson_throwsForCallerCsvFallback() {
        GeneratedTcJsonParser.parse("""
                TC_ID,Title,Steps,ExpectedResult,Preconditions,Priority,Tags,VisualAssertion,TestData,KeelPath
                TC_01,Login,1. Open,Shown,,,,,,AUTOMATE
                """);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void parse_emptyTestCases_throws() {
        GeneratedTcJsonParser.parse("""
                {"testCases":[],"coverageNotes":"none"}
                """);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void parse_unknownKeelPath_throws() {
        GeneratedTcJsonParser.parse("""
                {
                  "testCases": [
                    {
                      "tcId": "TC_01",
                      "title": "Bad path",
                      "preconditions": "",
                      "steps": "1. Act",
                      "expectedResult": "Done",
                      "priority": "",
                      "tags": "",
                      "visualAssertion": "",
                      "testData": "",
                      "keelPath": "NOT_A_PATH"
                    }
                  ],
                  "coverageNotes": ""
                }
                """);
    }
}
