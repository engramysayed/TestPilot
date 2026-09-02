package delivery.vision;

import delivery.authoring.LocalLlmClient;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.concurrent.atomic.AtomicInteger;

public class UiTarsVisionProviderTest {

    @Test
    public void needsAssertRetryWhenUncertainAndBlankFields() {
        Assert.assertTrue(UiTarsVisionProvider.needsAssertRetry(
                VisionAssertionResult.uncertain("empty vision assertion response")));
        Assert.assertTrue(UiTarsVisionProvider.needsAssertRetry(
                VisionAssertionResult.of(VisionAssertionStatus.UNCERTAIN, 0.5, "", "")));
        Assert.assertFalse(UiTarsVisionProvider.needsAssertRetry(
                VisionAssertionResult.of(VisionAssertionStatus.UNCERTAIN, 0.5,
                        "Login form with username field", "fields visible")));
        Assert.assertFalse(UiTarsVisionProvider.needsAssertRetry(
                VisionAssertionResult.of(VisionAssertionStatus.PASS, 0.9,
                        "Login button visible", "matches assertion")));
        Assert.assertTrue(UiTarsVisionProvider.needsAssertRetry(
                VisionAssertionResult.of(VisionAssertionStatus.PASS, 0.9,
                        "Login page with username and password fields", "")));
        Assert.assertTrue(UiTarsVisionProvider.needsAssertRetry(
                VisionAssertionResult.of(VisionAssertionStatus.PASS, 0.5,
                        "Login page with username and password fields",
                        "fields are clearly visible")));
    }

    @Test
    public void assertVisualRetriesOnceOnEmptyUncertain() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        LocalLlmClient client = new LocalLlmClient("http://127.0.0.1:9", "ui-tars") {
            @Override
            public String completeChat(String system, String user, byte[] imagePng, boolean forceJson) {
                int n = calls.incrementAndGet();
                Assert.assertTrue(system.contains("PASS|FAIL|UNCERTAIN")
                                || system.contains("observation"),
                        "must use UiTars assert prompt");
                if (n == 1) {
                    return """
                            {"status":"PASS","confidence":0.9,
                            "observation":"Username and password fields with a Login button",
                            "evidence":""}
                            """;
                }
                return """
                        {"status":"PASS","confidence":0.9,
                        "observation":"Username and password fields with a Login button",
                        "evidence":"Login form controls are clearly visible on the page"}
                        """;
            }
        };
        UiTarsVisionProvider provider = new UiTarsVisionProvider(client);
        VisionAssertionResult result = provider.assertVisual(
                new byte[]{1, 2, 3}, "Login page shows username and password fields", null);
        Assert.assertEquals(calls.get(), 2);
        Assert.assertEquals(result.status(), VisionAssertionStatus.PASS);
        Assert.assertTrue(result.observation().length() >= 20);
        Assert.assertFalse(result.evidence().isBlank());
    }

    @Test
    public void sanitizeAssertFillsEvidenceFromObservation() {
        VisionAssertionResult cleaned = UiTarsVisionProvider.sanitizeAssert(
                VisionAssertionResult.of(VisionAssertionStatus.PASS, 0.5,
                        "Username and password fields with a Login button are visible",
                        ""));
        Assert.assertEquals(cleaned.status(), VisionAssertionStatus.PASS);
        Assert.assertTrue(cleaned.confidence() >= 0.7);
        Assert.assertTrue(cleaned.evidence().contains("Visible UI matches"));
        Assert.assertFalse(VisionAssertionGate.isPromptPlaceholder(cleaned.evidence()));
    }

    @Test
    public void sanitizeAssertUnwrapsAngleEvidenceIntoObservation() {
        VisionAssertionResult cleaned = UiTarsVisionProvider.sanitizeAssert(
                VisionAssertionResult.of(VisionAssertionStatus.PASS, 0.5, "",
                        "<Login page with username and password fields and a Login button>"));
        Assert.assertEquals(cleaned.status(), VisionAssertionStatus.PASS);
        Assert.assertTrue(cleaned.observation().toLowerCase().contains("login page"));
        Assert.assertTrue(cleaned.confidence() >= 0.7);
        Assert.assertFalse(VisionAssertionGate.isPromptPlaceholder(cleaned.observation()));
    }
}
