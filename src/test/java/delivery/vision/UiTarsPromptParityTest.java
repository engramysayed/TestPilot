package delivery.vision;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * UiTars must use UiTars-specific prompts/parser — not Qwen SYSTEM_PROMPT for analyze.
 */
public class UiTarsPromptParityTest {

    @Test
    public void uitarsProviderUsesDedicatedPromptAndParser() throws Exception {
        String src = Files.readString(Path.of(
                "src/main/java/delivery/vision/UiTarsVisionProvider.java"));
        Assert.assertTrue(src.contains("UiTarsPrompt.groundingSystem()"),
                "analyze must use UiTarsPrompt.groundingSystem");
        Assert.assertTrue(src.contains("UiTarsResponseParser.parse"),
                "analyze must use UiTarsResponseParser");
        Assert.assertTrue(src.contains("VisionBboxQualityGate.filter"),
                "analyze must quality-gate candidates");
        Assert.assertTrue(src.contains("UiTarsPrompt.assertSystem()"),
                "assert must use UiTarsPrompt.assertSystem");
        Assert.assertFalse(src.contains("QwenVisionProvider.SYSTEM_PROMPT"),
                "must not reuse Qwen grounding SYSTEM_PROMPT");
        Assert.assertTrue(UiTarsPrompt.assertSystem().toLowerCase().contains("placeholder")
                        || UiTarsPrompt.assertSystem().toLowerCase().contains("what is visible"),
                "assert prompt must ban placeholder echoes");
        Assert.assertTrue(UiTarsPrompt.groundingSystem().contains("start_box")
                        || UiTarsPrompt.groundingSystem().contains("Action:"),
                "grounding prompt must prefer native Action/start_box");
        Assert.assertTrue(src.contains("UiTarsPrompt.groundingUser")
                        || src.contains("groundingUser("),
                "analyze must use UiTars groundingUser");
        Assert.assertTrue(UiTarsPrompt.groundingSystem().toLowerCase().contains("prefer click")
                        || UiTarsPrompt.groundingSystem().contains("Prefer click"),
                "grounding prompt must prefer click over finished");
    }
}
