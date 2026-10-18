package delivery.portal.service;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class CursorHealAuthoringPromptTest {

    @Test
    public void authoringReviewPrompt_matchesExecuteAuthoringRules() throws Exception {
        String prompt = Files.readString(Path.of("tools/cursor-heal/heal.mjs"), StandardCharsets.UTF_8);

        Assert.assertTrue(prompt.contains("OTP is accepted"));
        Assert.assertTrue(prompt.contains("visible-text"));
    }
}
