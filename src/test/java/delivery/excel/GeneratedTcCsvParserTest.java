package delivery.excel;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

public class GeneratedTcCsvParserTest {

    private static final String SAMPLE = """
            TC_ID,Title,Steps,ExpectedResult,Preconditions,Priority,Tags,VisualAssertion,TestData,KeelPath
            TC_01,Login ok,"1. Open login
            2. Enter user","1. Page shown
            2. Accepted",Login required.,P1,smoke,,user,EXECUTE
            TC_02,Upload file,1. Choose file,Error shown,None,P2,negative,,,MANUAL
            ---KEEL_COVERAGE---
            Applied boundary and negative paths. Gap: no mobile stories.
            """;

    @Test
    public void parse_extractsRowsAndKeelPath() {
        List<ManualTestCase> cases = GeneratedTcCsvParser.parse(SAMPLE);
        Assert.assertEquals(cases.size(), 2);
        Assert.assertEquals(cases.get(0).tcId(), "TC_01");
        Assert.assertEquals(cases.get(0).keelPath(), "EXECUTE");
        Assert.assertEquals(cases.get(1).keelPath(), "MANUAL");
    }

    @Test
    public void extractCoverageNotes_afterMarker() {
        String notes = GeneratedTcCsvParser.extractCoverageNotes(SAMPLE);
        Assert.assertTrue(notes.contains("boundary"));
    }

    @Test
    public void toCsv_roundTripHeader() {
        List<ManualTestCase> cases = GeneratedTcCsvParser.parse(SAMPLE);
        String csv = GeneratedTcCsvParser.toCsv(cases);
        Assert.assertTrue(csv.startsWith("TC_ID,Title,Steps,ExpectedResult"));
        Assert.assertTrue(csv.contains(",MANUAL"));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void parse_rejectsUnknownKeelPath() {
        String bad = """
                TC_ID,Title,Steps,ExpectedResult,Preconditions,Priority,Tags,VisualAssertion,TestData,KeelPath
                TC_01,T,S,E,,,,,,NOT_A_PATH
                """;
        GeneratedTcCsvParser.parse(bad);
    }

    @Test
    public void parse_normalizesLiteralBackslashNInSteps() {
        String gemmaStyle = """
                TC_ID,Title,Steps,ExpectedResult,Preconditions,Priority,Tags,VisualAssertion,TestData,KeelPath
                TC_01,Invalid login,1. Open the Login Page at /login\\n2. Enter a valid email address\\n3. Enter a wrong password\\n4. Click the Login button,1. Stay on login\\n2. Show error,Login required.,P1,negative,test@example.com\\nWrongPassword123,AUTOMATE,
                """;
        List<ManualTestCase> cases = GeneratedTcCsvParser.parse(gemmaStyle);
        Assert.assertEquals(cases.size(), 1);
        Assert.assertTrue(cases.get(0).steps().contains("\n2. Enter"));
        Assert.assertEquals(cases.get(0).keelPath(), "AUTOMATE");
        Assert.assertTrue(cases.get(0).testData().trim().contains("test@example.com"));
        Assert.assertTrue(cases.get(0).testData().trim().contains("WrongPassword123"));
        var intents = delivery.authoring.StepIntentBinder.parseIntents(cases.get(0));
        Assert.assertFalse(intents.isEmpty(), "steps should produce actionable intents after normalization");
    }

    @Test
    public void parse_mergesStepShardsIntoOneTestCase() {
        String broken = """
                TC_ID,Title,Steps,ExpectedResult,Preconditions,Priority,Tags,VisualAssertion,TestData,KeelPath
                -2. Enter a valid email address in the Email field,,,,,,,,,AUTOMATE
                -3. Enter a wrong password in the Password field,,,,,,,,,AUTOMATE
                -4. Click the Log in button,,,,,,,,,AUTOMATE
                -5. Verify wrong-password error is shown,"1. Login page is shown
                2. Error is visible",No login required.,P1,negative,,"user@example.com
                WrongPass
                ",AUTOMATE
                """;
        List<ManualTestCase> cases = GeneratedTcCsvParser.parse(broken);
        Assert.assertEquals(cases.size(), 1);
        Assert.assertEquals(cases.get(0).tcId(), "TC_01");
        Assert.assertTrue(cases.get(0).steps().contains("Enter a valid email"));
        Assert.assertTrue(cases.get(0).steps().contains("Click the Log in button"));
    }

    @Test
    public void parse_realignsCredentialsMisplacedInKeelPathColumn() {
        String shifted = """
                TC_ID,Title,Steps,ExpectedResult,Preconditions,Priority,Tags,VisualAssertion,TestData,KeelPath
                TC_01,Invalid password,"1. Open login
                2. Enter email
                3. Enter password
                4. Click Log in","1. Error shown",No login required.,P1,negative,,,test_user@example.com WrongPassword123
                """;
        List<ManualTestCase> cases = GeneratedTcCsvParser.parse(shifted);
        Assert.assertEquals(cases.size(), 1);
        Assert.assertEquals(cases.get(0).keelPath(), "AUTOMATE");
        Assert.assertTrue(cases.get(0).testData().contains("test_user@example.com"));
    }
}
