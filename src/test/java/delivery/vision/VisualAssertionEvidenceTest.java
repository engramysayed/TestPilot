package delivery.vision;

import org.json.JSONObject;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;

public class VisualAssertionEvidenceTest {

    @Test
    public void writesPngAndJsonWithoutEmbeddingBytes() throws Exception {
        Path dir = Files.createTempDirectory("va-ev-");
        byte[] png = new byte[] {1, 2, 3, 4, 5};
        VisionAssertionResult result = VisionAssertionResult.of(
                VisionAssertionStatus.PASS, 0.91, "Welcome", "heading");
        VisionAssertionEvidence.write(dir, "TC_01", "Welcome visible", result, png);

        Path shot = dir.resolve("visual-assert.png");
        Path json = dir.resolve("visual-assert.json");
        Assert.assertTrue(Files.isRegularFile(shot));
        Assert.assertEquals(Files.readAllBytes(shot), png);
        String body = Files.readString(json);
        JSONObject root = new JSONObject(body);
        Assert.assertEquals(root.getString("tcId"), "TC_01");
        Assert.assertEquals(root.getString("status"), "PASS");
        Assert.assertEquals(root.getString("screenshotFile"), "visual-assert.png");
        Assert.assertFalse(body.contains("AQIDBAU"));
        Assert.assertFalse(root.has("pngBase64"));
    }
}
