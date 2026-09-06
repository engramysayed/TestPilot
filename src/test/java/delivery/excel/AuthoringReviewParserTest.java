package delivery.excel;

import org.testng.Assert;
import org.testng.annotations.Test;

public class AuthoringReviewParserTest {
    @Test
    public void parse_happyPath_findingsAndCases() {
        String raw = """
                {
                  "findings":[{"severity":"warn","tcId":"TC_01","message":"Vague assert"}],
                  "cases":[{
                    "tcId":"TC_01","title":"Login empty email","preconditions":"",
                    "steps":"1. Leave the Email field empty\\n2. Click Login",
                    "expectedResult":"Shows error \\"Invalid\\"","priority":"P1","tags":"",
                    "visualAssertion":"","testData":"\\n","keelPath":"EXECUTE"
                  }],
                  "coverageNotes":"Reviewed empty email"
                }
                """;
        AuthoringReviewParser.ParseResult r = AuthoringReviewParser.parse(raw);
        Assert.assertEquals(r.findings().size(), 1);
        Assert.assertEquals(r.findings().get(0).severity(), "warn");
        Assert.assertEquals(r.cases().size(), 1);
        Assert.assertEquals(r.cases().get(0).tcId(), "TC_01");
        Assert.assertTrue(r.coverageNotes().contains("Reviewed"));
    }

    @Test
    public void parse_narratedLenientJson_acceptsRawNewlineAndTrailingCommas() {
        String raw = """
                Here is the reviewed suite:
                {
                  "findings":[
                    {"severity":"warning","tcId":"TC_01","message":"Clarified steps"},
                  ],
                  "cases":[{
                    "tcId":"TC_01","title":"Login","preconditions":"",
                    "steps":"1. Enter email
                2. Click Login",
                    "expectedResult":"Dashboard opens","priority":"P1","tags":"",
                    "visualAssertion":"","testData":"user@example.com
                ","keelPath":"EXECUTE",
                  }],
                  "coverageNotes":"Reviewed login",
                }
                Review complete.
                """;

        AuthoringReviewParser.ParseResult r = AuthoringReviewParser.parse(raw);

        Assert.assertEquals(r.findings().size(), 1);
        Assert.assertEquals(r.findings().get(0).severity(), "warn");
        Assert.assertEquals(r.findings().get(0).message(), "Clarified steps");
        Assert.assertEquals(r.cases().size(), 1);
        Assert.assertTrue(r.cases().get(0).steps().contains("2. Click Login"));
        Assert.assertEquals(r.coverageNotes(), "Reviewed login");
    }

    @Test
    public void parse_garbage_throws() {
        try {
            AuthoringReviewParser.parse("not json");
            Assert.fail("expected");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().toLowerCase().contains("review"));
        }
    }

    @Test
    public void parse_emptyCases_throws() {
        try {
            AuthoringReviewParser.parse("{\"findings\":[],\"cases\":[]}");
            Assert.fail("expected");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }
}
