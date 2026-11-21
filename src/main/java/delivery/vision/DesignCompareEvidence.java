package delivery.vision;

import org.json.JSONObject;
import utils.LogsManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Locale;

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
        writeInternal(evidenceDir, tcId, null, result, actualSourceOrNull, actualPngOrNull, provider, model);
    }

    /** Per-step evidence: scoped filenames + a reference link into the TC's step subdirectory. */
    public static void writeStep(
            Path evidenceDir,
            String tcId,
            int stepIndex,
            DesignCompareResult result,
            Path actualSourceOrNull,
            byte[] actualPngOrNull,
            String provider,
            String model) {
        writeInternal(evidenceDir, tcId, stepIndex, result, actualSourceOrNull, actualPngOrNull, provider, model);
    }

    private static void writeInternal(
            Path evidenceDir,
            String tcId,
            Integer stepIndexOrNull,
            DesignCompareResult result,
            Path actualSourceOrNull,
            byte[] actualPngOrNull,
            String provider,
            String model) {
        if (evidenceDir == null) {
            return;
        }
        boolean stepScope = stepIndexOrNull != null;
        String actualFileName = stepScope
                ? String.format(Locale.ROOT, "design-actual-step-%03d.png", stepIndexOrNull)
                : "design-actual.png";
        String jsonFileName = stepScope
                ? String.format(Locale.ROOT, "design-compare-step-%03d.json", stepIndexOrNull)
                : "design-compare.json";
        String referenceFile = stepScope
                ? String.format(Locale.ROOT, "../design-references/%s/step-%03d.png", tcId, stepIndexOrNull)
                : "../design-references/" + tcId + ".png";
        try {
            Files.createDirectories(evidenceDir);
            if (actualPngOrNull != null && actualPngOrNull.length > 0) {
                Files.write(evidenceDir.resolve(actualFileName), actualPngOrNull);
            } else if (actualSourceOrNull != null && Files.isRegularFile(actualSourceOrNull)) {
                Files.copy(actualSourceOrNull, evidenceDir.resolve(actualFileName),
                        StandardCopyOption.REPLACE_EXISTING);
            }
            JSONObject json = new JSONObject();
            json.put("tcId", tcId == null ? "" : tcId);
            if (stepScope) {
                json.put("stepIndex", stepIndexOrNull);
                json.put("scope", "step");
            } else {
                json.put("scope", "tc");
            }
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
            json.put("referenceFile", referenceFile);
            json.put("actualFile", actualFileName);
            if (actualSourceOrNull != null) {
                json.put("actualSource", actualSourceOrNull.getFileName().toString());
            }
            json.put("provider", provider == null ? "" : provider);
            json.put("model", model == null ? "" : model);
            json.put("timestamp", Instant.now().toString());
            Files.writeString(evidenceDir.resolve(jsonFileName), json.toString(2));
        } catch (Exception e) {
            LogsManager.error("DESIGN_COMPARE: failed to write evidence: " + e.getMessage());
        }
    }

    private static String cap(String value) {
        String s = value == null ? "" : value.trim();
        return s.length() <= 500 ? s : s.substring(0, 500);
    }
}
