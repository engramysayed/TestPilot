package delivery.portal.service;

import delivery.excel.GenerateQualityGate;
import delivery.excel.GeneratedTcJsonParser;
import delivery.excel.ManualTestCase;
import delivery.portal.DeliveryPortalProperties;
import delivery.portal.model.ProjectRecord;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class FacebookLoginNegativeGenerateTest {

    private static final String BASE_URL = "https://www.facebook.com/";
    private static final String FIXTURE_US = "generate/facebook-login-negative-us.txt";
    private static final String GOLDEN_JSON = "generate/facebook-login-negative-golden.json";

    @Test
    public void goldenFixture_passesQualityGate() throws Exception {
        List<ManualTestCase> cases = parseGoldenJson();
        List<String> errors = GenerateQualityGate.validate(cases, BASE_URL);
        Assert.assertTrue(errors.isEmpty(), errors.toString());
        assertCoverageGroups(cases);
    }

    @Test
    public void generateStory_acceptsGoldenOnFirstCall() throws Exception {
        StubTcGenerateService service = new StubTcGenerateService();
        service.llmResponses.add(readGoldenJson());
        ProjectRecord project = facebookProject();
        TcGenerateService.StoryGenerateResult result = service.generateStory(
                project, readFixtureUs(), false, "US-1", null);
        Assert.assertTrue(result.cases().size() >= 8);
        assertCoverageGroups(result.cases());
        Assert.assertEquals(service.llmCallCount, 1);
    }

    @Test
    public void generateStory_repairsBadPhoneFieldWithoutRetry() throws Exception {
        StubTcGenerateService service = new StubTcGenerateService();
        service.llmResponses.add(badPhoneFieldJson());
        ProjectRecord project = facebookProject();
        TcGenerateService.StoryGenerateResult result = service.generateStory(
                project, readFixtureUs(), false, "US-1", null);
        Assert.assertFalse(result.cases().isEmpty());
        Assert.assertEquals(service.llmCallCount, 1);
        Assert.assertTrue(result.cases().get(0).steps().toLowerCase().contains("email or phone"));
    }

    static void assertCoverageGroups(List<ManualTestCase> cases) {
        boolean missingCreds = false;
        boolean invalidCreds = false;
        boolean malformed = false;
        boolean abnormal = false;

        for (ManualTestCase tc : cases) {
            String blob = (tc.title() + " " + tc.steps() + " " + tc.expectedResult() + " " + tc.tags())
                    .toLowerCase();
            if (blob.contains("leave the") && blob.contains("empty")) {
                missingCreds = true;
            }
            if (blob.contains("incorrect") || blob.contains("wrong password") || blob.contains("unregistered")) {
                invalidCreds = true;
            }
            if (blob.contains("special char") || blob.contains("whitespace") || blob.contains("oversized")
                    || blob.contains("boundary") || blob.contains("exceed")) {
                malformed = true;
            }
            String keel = tc.keelPath() == null ? "" : tc.keelPath().trim().toUpperCase();
            if (("MANUAL".equals(keel) || "EXECUTE".equals(keel))
                    && (blob.contains("network") || blob.contains("timeout") || blob.contains("double")
                    || blob.contains("rapid") || blob.contains("unavailable"))) {
                abnormal = true;
            }
        }

        Assert.assertTrue(missingCreds, "Missing credentials group not covered");
        Assert.assertTrue(invalidCreds, "Invalid credentials group not covered");
        Assert.assertTrue(malformed, "Malformed input group not covered");
        Assert.assertTrue(abnormal, "Abnormal conditions group not covered");
    }

    private static List<ManualTestCase> parseGoldenJson() throws Exception {
        return GeneratedTcJsonParser.parse(readGoldenJson()).cases();
    }

    private static ProjectRecord facebookProject() {
        ProjectRecord project = new ProjectRecord("fb-login-neg", "Facebook Login Negative", 1L, 1);
        project.setBaseUrl(BASE_URL);
        return project;
    }

    private static String readFixtureUs() throws Exception {
        return readResource(FIXTURE_US);
    }

    private static String readGoldenJson() throws Exception {
        return readResource(GOLDEN_JSON);
    }

    private static String readResource(String path) throws Exception {
        try (InputStream in = FacebookLoginNegativeGenerateTest.class.getClassLoader().getResourceAsStream(path)) {
            Assert.assertNotNull(in, "Missing test resource: " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String badPhoneFieldJson() {
        return """
                {
                  "testCases": [{
                    "tcId": "TC_03",
                    "title": "Invalid password login with valid-looking phone number",
                    "preconditions": "No login required.",
                    "steps": "1. Open the Login Page at https://www.facebook.com/\\n2. Enter in the Phone field\\n3. Enter in the Password field\\n4. Click the Log in button\\n5. Confirm the message 'Incorrect email or phone number' is visible",
                    "expectedResult": "1. Login page is shown\\n2. Phone number is accepted\\n3. Password is accepted\\n4. Submit is clicked\\n5. Error message 'Incorrect email or phone number' is shown",
                    "priority": "P1",
                    "tags": "negative",
                    "visualAssertion": "",
                    "testData": "5551234567\\nWrongPass123!\\n\\n",
                    "keelPath": "EXECUTE"
                  }],
                  "coverageNotes": ""
                }
                """;
    }

    static final class StubTcGenerateService extends TcGenerateService {
        final java.util.List<String> llmResponses = new java.util.ArrayList<>();
        int llmCallCount;

        StubTcGenerateService() {
            super(new DeliveryPortalProperties(), null, null, new GenerateModelService(new DeliveryPortalProperties()), null);
        }

        @Override
        String callOllama(String system, String user, String model) {
            llmCallCount++;
            return llmResponses.get(llmCallCount - 1);
        }
    }
}
