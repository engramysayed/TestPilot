package delivery.vision;

import org.json.JSONObject;
import utils.LogsManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

public final class VisionAssertionEvidence {

    private VisionAssertionEvidence() {
    }

    public static void write(
            Path evidenceDir,
            String tcId,
            String assertionText,
            VisionAssertionResult result,
            byte[] png) {
        if (evidenceDir == null) {
            return;
        }
        try {
            Files.createDirectories(evidenceDir);
            if (png != null && png.length > 0) {
                Files.write(evidenceDir.resolve("visual-assert.png"), png);
            }
            JSONObject json = new JSONObject();
            json.put("tcId", tcId == null ? "" : tcId);
            json.put("assertionText", assertionText == null ? "" : assertionText);
            json.put("provider", VisionGroundingConfig.assertProviderId());
            json.put("model", VisionGroundingConfig.assertModel());
            json.put("status", result == null ? "UNCERTAIN" : result.status().name());
            json.put("confidence", result == null ? 0.5 : result.confidence());
            json.put("observation", result == null ? "" : cap(result.observation()));
            json.put("evidence", result == null ? "" : cap(result.evidence()));
            if (result != null && result.error() != null) {
                json.put("error", cap(result.error()));
            }
            json.put("timestamp", Instant.now().toString());
            json.put("screenshotFile", "visual-assert.png");
            Files.writeString(evidenceDir.resolve("visual-assert.json"), json.toString(2));
        } catch (Exception e) {
            LogsManager.error("VISION_ASSERT: failed to write evidence: " + e.getMessage());
        }
    }

    private static String cap(String value) {
        String s = value == null ? "" : value.trim();
        return s.length() <= 500 ? s : s.substring(0, 500);
    }
}
