package delivery.portal.service;

import delivery.authoring.LocalLlmClient;
import delivery.portal.DeliveryPortalProperties;
import delivery.excel.GenerateQualityGate;
import delivery.portal.model.ProjectRecord;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

public class TcGenerateServiceQualityRetryTest {

    private static final String BAD_JSON = """
            {
              "testCases": [{
                "tcId": "TC_01",
                "title": "Login",
                "preconditions": "",
                "steps": "",
                "expectedResult": "Shown",
                "priority": "",
                "tags": "",
                "visualAssertion": "",
                "testData": "",
                "keelPath": "AUTOMATE"
              }],
              "coverageNotes": ""
            }
            """;

    /** Well-formed leave-empty steps and quoted error; only TestData line 2 is typed. */
    private static final String TESTDATA_DEFECT_JSON = """
            {
              "testCases": [{
                "tcId": "TC_02",
                "title": "Login with empty email and valid password",
                "preconditions": "No login required.",
                "steps": "1. Open the Login Page at https://www.facebook.com/\\n2. Leave the Email or phone field empty\\n3. Enter in the Password field\\n4. Click the Log in button\\n5. Confirm the message 'Please enter your email or phone number' is visible",
                "expectedResult": "1. Login page is shown\\n2. Email or phone is empty\\n3. Password is accepted\\n4. Submit is clicked\\n5. Error message 'Please enter your email or phone number' is shown",
                "priority": "P1",
                "tags": "negative,missing-credentials",
                "visualAssertion": "",
                "testData": "\\nuser@example.com\\nValidPass123!\\n\\n",
                "keelPath": "EXECUTE"
              }],
              "coverageNotes": ""
            }
            """;

    @Test
    public void buildQualityRetryUserMessage_includesErrorsAndHints() {
        ProjectRecord project = new ProjectRecord("p1", "Demo", 1L, 1);
        project.setBaseUrl("https://example.com");
        String msg = TcGenerateService.buildQualityRetryUserMessage(
                project,
                "As a user I can login",
                "US-1",
                "{\"testCases\":[]}",
                List.of("Invalid tcId 'LOGIN': must match TC_<digits> or TC_<ALNUM_UNDERSCORE>"));

        Assert.assertTrue(msg.contains("As a user I can login"));
        Assert.assertTrue(msg.contains("Invalid tcId"));
        Assert.assertTrue(msg.contains("{\"testCases\":[]}"));
        Assert.assertTrue(msg.contains("Email or phone field"));
        Assert.assertTrue(msg.contains("Leave the"));
        Assert.assertTrue(msg.contains("Story ID: US-1"));
    }

    @Test
    public void buildReviewUserMessage_asksToFixMistakesAndAddCoverage() {
        String json = "{\"testCases\":[{\"tcId\":\"TC_01\",\"title\":\"t\",\"steps\":\"1. Open\",\"expectedResult\":\"Shown\",\"keelPath\":\"EXECUTE\"}],\"coverageNotes\":\"\"}";
        String msg = TcGenerateService.buildReviewUserMessage("As a user I can login", json);
        String lower = msg.toLowerCase();
        Assert.assertTrue(lower.contains("fix"), msg);
        Assert.assertTrue(lower.contains("missing"), msg);
        Assert.assertTrue(lower.contains("leave the") || lower.contains("email or phone"), msg);
        Assert.assertTrue(msg.contains("As a user I can login"));
    }

    @Test
    public void generateStory_retriesOnceOnGateFailThenThrows() throws Exception {
        StubTcGenerateService service = new StubTcGenerateService();
        service.llmResponses.add(BAD_JSON);
        service.llmResponses.add(BAD_JSON);

        ProjectRecord project = new ProjectRecord("p1", "Demo", 1L, 1);
        try {
            service.generateStory(project, "As a user I can login", false, null, null);
            Assert.fail("Expected quality gate failure");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(GenerateQualityGate.isQualityGateFailure(e));
            Assert.assertTrue(e.getMessage().contains("steps are blank"));
        }
        Assert.assertEquals(service.llmCallCount, 2);
        Assert.assertTrue(service.userPrompts.get(1).contains("Previous output failed quality checks"));
    }

    @Test
    public void generateStory_repairsTestDataDefectWithoutRetry() throws Exception {
        StubTcGenerateService service = new StubTcGenerateService();
        service.llmResponses.add(TESTDATA_DEFECT_JSON);

        ProjectRecord project = new ProjectRecord("p1", "Demo", 1L, 1);
        project.setBaseUrl("https://www.facebook.com/");
        TcGenerateService.StoryGenerateResult result = service.generateStory(
                project, "As a user I can login", false, null, null);

        Assert.assertFalse(result.cases().isEmpty());
        Assert.assertEquals(service.llmCallCount, 1);
    }

    static final class StubTcGenerateService extends TcGenerateService {
        final List<String> llmResponses = new ArrayList<>();
        final List<String> userPrompts = new ArrayList<>();
        int llmCallCount;

        StubTcGenerateService() {
            super(new DeliveryPortalProperties(), null, null, new GenerateModelService(new DeliveryPortalProperties()), null);
        }

        @Override
        LocalLlmClient.ChatOutcome callOllamaDetailed(
                String system, String user, String model, java.util.function.BooleanSupplier cancelCheck) {
            llmCallCount++;
            userPrompts.add(user);
            String content = llmResponses.get(Math.min(llmCallCount - 1, llmResponses.size() - 1));
            return new LocalLlmClient.ChatOutcome(content, "stop");
        }
    }
}
