package delivery.vision;

import delivery.authoring.LocalLlmClient;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.IOException;

public class QwenVisionProviderAssertTest {

    @Test
    public void assertVisualUsesAssertionPromptNotBboxSchema() throws Exception {
        final String[] captured = new String[2];
        LocalLlmClient stub = new LocalLlmClient("http://127.0.0.1:9", "dummy") {
            @Override
            public String completeJson(String system, String user, byte[] pngOrNull)
                    throws IOException, InterruptedException {
                captured[0] = system;
                captured[1] = user;
                return "{\"status\":\"PASS\",\"confidence\":0.9,\"observation\":\"ok\",\"evidence\":\"ui\"}";
            }
        };
        VisionAssertionResult r = new QwenVisionProvider(stub).assertVisual(
                new byte[] {1}, "Welcome heading is visible", null);
        Assert.assertEquals(r.status(), VisionAssertionStatus.PASS);
        Assert.assertTrue(captured[0].contains("\"confidence\":0.9"));
        Assert.assertFalse(captured[0].contains("\"confidence\":0.0"));
        Assert.assertFalse(captured[0].contains("candidates"));
        Assert.assertTrue(captured[1].contains("Welcome heading is visible"));
        Assert.assertFalse(captured[1].contains("Figma"));
    }

    @Test
    public void assertVisualIoFailureIsUncertain() throws Exception {
        LocalLlmClient failing = new LocalLlmClient("http://127.0.0.1:9", "dummy") {
            @Override
            public String completeJson(String system, String user, byte[] pngOrNull)
                    throws IOException, InterruptedException {
                throw new IOException("connection refused");
            }
        };
        VisionAssertionResult r = new QwenVisionProvider(failing).assertVisual(
                new byte[] {1}, "something visible", null);
        Assert.assertEquals(r.status(), VisionAssertionStatus.UNCERTAIN);
        Assert.assertNotNull(r.error());
        Assert.assertTrue(r.error().contains("connection refused"));
    }

    @Test
    public void assertSystemPromptDoesNotUseZeroConfidenceExample() {
        Assert.assertTrue(QwenVisionProvider.ASSERT_SYSTEM_PROMPT.contains("\"confidence\":0.9"));
        Assert.assertFalse(QwenVisionProvider.ASSERT_SYSTEM_PROMPT.contains("\"confidence\":0.0"));
        Assert.assertFalse(QwenVisionProvider.ASSERT_SYSTEM_PROMPT.contains("<describe"));
        Assert.assertFalse(QwenVisionProvider.ASSERT_SYSTEM_PROMPT.contains("<why"));
    }
}
