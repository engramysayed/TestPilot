package delivery.vision;

import delivery.authoring.LocalLlmClient;
import delivery.authoring.StepIntentBinder;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.IOException;

public class QwenVisionProviderTest {

    @Test
    public void parseTwoCandidates() {
        VisionAnalysisResult r = QwenVisionProvider.parseResponse(
                "{\"found\":true,\"candidates\":[{\"description\":\"Login\",\"bbox\":{\"x\":1,\"y\":2,\"width\":3,\"height\":4},\"confidence\":0.96}]}");
        Assert.assertTrue(r.found());
        Assert.assertEquals(r.candidates().get(0).boundingBox().x(), 1);
    }

    @Test
    public void parseGarbageIsUnavailable() {
        VisionAnalysisResult r = QwenVisionProvider.parseResponse("not json");
        Assert.assertFalse(r.found());
        Assert.assertNotNull(r.error());
    }

    @Test
    public void analyzeIoFailureIsUnavailable() throws Exception {
        LocalLlmClient failing = new LocalLlmClient("http://127.0.0.1:9", "dummy") {
            @Override
            public String completeJson(String system, String user, byte[] pngOrNull)
                    throws IOException, InterruptedException {
                throw new IOException("connection refused");
            }
        };
        VisionAnalysisResult r = new QwenVisionProvider(failing).analyze(
                new byte[] {1, 2, 3},
                new StepIntentBinder.IntentLine(StepIntentBinder.IntentKind.CLICK, "Click Login"));
        Assert.assertFalse(r.found());
        Assert.assertNotNull(r.error());
        Assert.assertTrue(r.error().contains("connection refused"));
    }

    @Test
    public void analyzeUsesIntentOnlyUserPayload() throws Exception {
        final String[] capturedUser = new String[1];
        LocalLlmClient stub = new LocalLlmClient("http://127.0.0.1:9", "dummy") {
            @Override
            public String completeJson(String system, String user, byte[] pngOrNull)
                    throws IOException, InterruptedException {
                capturedUser[0] = user;
                return "{\"found\":false,\"candidates\":[]}";
            }
        };
        new QwenVisionProvider(stub).analyze(
                new byte[] {1},
                new StepIntentBinder.IntentLine(StepIntentBinder.IntentKind.CLICK, "Click Login"));
        Assert.assertNotNull(capturedUser[0]);
        Assert.assertTrue(capturedUser[0].contains("ACTION:"), capturedUser[0]);
        Assert.assertTrue(capturedUser[0].contains("CLICK"), capturedUser[0]);
        Assert.assertTrue(capturedUser[0].contains("TARGET:"), capturedUser[0]);
        Assert.assertTrue(capturedUser[0].contains("Click Login"), capturedUser[0]);
        Assert.assertFalse(capturedUser[0].contains("<html"));
    }
}
