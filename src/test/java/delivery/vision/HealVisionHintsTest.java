package delivery.vision;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;

public class HealVisionHintsTest {

    @Test
    public void cursorSidecarIncludesVisionAttemptsSection() throws Exception {
        String src = Files.readString(Path.of("tools/cursor-heal/heal.mjs"));
        Assert.assertTrue(src.contains("visionAttempts"));
        Assert.assertTrue(src.contains("## Vision attempts this intent"));
    }
}
