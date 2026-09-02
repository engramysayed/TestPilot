package delivery.vision;

import org.json.JSONObject;
import utils.LogsManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;

public final class DesignCompareEvidence {

    private DesignCompareEvidence() {
    }

    public static void write(
            Path evidenceDir,
            String tcId,
            DesignCompareResult result,
            Path actualSourceOrNull,
            byte[] actualPngOrNull,
            String provider,
            String model) {
        if (evidenceDir == null) {
            return;
        }
        try {
            Files.createDirectories(evidenceDir);
            if (actualPngOrNull != null && actualPngOrNull.length > 0) {
                Files.write(evidenceDir.resolve("design-actual.png"), actualPngOrNull);
            } else if (actualSourceOrNull != null && Files.isRegularFile(actualSourceOrNull)) {
                Files.copy(actualSourceOrNull, evidenceDir.resolve("design-actual.png"),
                        StandardCopyOption.REPLACE_EXISTING);
            }
            JSONObject json = new JSONObject();
            json.put("tcId", tcId == null ? "" : tcId);
            json.put("status", result == null ? DesignCompareStatus.UNCERTAIN.name()
                    : result.status().name());
            json.put("confidence", result == null ? 0.5 : result.confidence());
            json.put("observation", result == null ? "" : cap(result.observation()));
            json.put("evidence", result == null ? "" : cap(result.evidence()));
            if (result != null && result.error() != null && !result.error().isBlank()) {
                json.put("error", cap(result.error()));
            }
            if (result != null && result.status() == DesignCompareStatus.SKIPPED) {
                json.put("reason", cap(result.evidence()));
            }
            json.put("referenceFile", "../design-references/" + tcId + ".png");
            json.put("actualFile", "design-actual.png");
            if (actualSourceOrNull != null) {
                json.put("actualSource", actualSourceOrNull.getFileName().toString());
            }
            json.put("provider", provider == null ? "" : provider);
            json.put("model", model == null ? "" : model);
            json.put("timestamp", Instant.now().toString());
            Files.writeString(evidenceDir.resolve("design-compare.json"), json.toString(2));
        } catch (Exception e) {
            LogsManager.error("DESIGN_COMPARE: failed to write evidence: " + e.getMessage());
        }
    }

    private static String cap(String value) {
        String s = value == null ? "" : value.trim();
        return s.length() <= 500 ? s : s.substring(0, 500);
    }
}
