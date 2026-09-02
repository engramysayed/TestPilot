package delivery.vision;

import org.json.JSONObject;
import utils.LogsManager;
import utils.PropertyReader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

/**
 * Append-only JSONL journal of vision ground hits/misses for later fine-tune / debug.
 */
public final class VisionMissJournal {

    private VisionMissJournal() {
    }

    public static boolean enabled() {
        String p = firstProp("delivery.vision.miss-log.enabled");
        if (p == null || p.isBlank()) {
            return true;
        }
        return "true".equalsIgnoreCase(p.trim());
    }

    public static Path logDir() {
        String p = firstProp("delivery.vision.miss-log.dir");
        if (p == null || p.isBlank()) {
            p = "delivery-store/vision-misses";
        }
        return Path.of(p.trim());
    }

    public static void recordGrounding(
            String intentText,
            String outcome,
            BoundingBox bbox,
            double confidence,
            String description,
            String elementFromPointSummary,
            String rawClip) {
        if (!enabled()) {
            return;
        }
        // Dataset is for failures only; heal already gets successes via VisionAttemptLog.
        String o = outcome == null ? "" : outcome.trim().toLowerCase();
        if (o.equals("grounded") || o.equals("ground") || o.equals("hit")) {
            return;
        }
        try {
            Path dir = logDir();
            Files.createDirectories(dir);
            JSONObject row = new JSONObject();
            row.put("ts", Instant.now().toString());
            row.put("kind", "grounding");
            row.put("provider", VisionGroundingConfig.groundingProviderId());
            row.put("model", VisionGroundingConfig.groundingModel());
            row.put("intent", intentText == null ? "" : intentText);
            row.put("outcome", outcome == null ? "" : outcome);
            row.put("confidence", confidence);
            row.put("description", description == null ? "" : description);
            row.put("elementFromPoint", elementFromPointSummary == null ? "" : elementFromPointSummary);
            if (bbox != null) {
                row.put("bbox", new JSONObject()
                        .put("x", bbox.x())
                        .put("y", bbox.y())
                        .put("width", bbox.width())
                        .put("height", bbox.height()));
            }
            if (rawClip != null && !rawClip.isBlank()) {
                String clip = rawClip.length() > 500 ? rawClip.substring(0, 500) : rawClip;
                row.put("rawClip", clip);
            }
            Path file = dir.resolve("grounding-" + dayStamp() + ".jsonl");
            Files.writeString(
                    file,
                    row.toString() + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (Exception e) {
            LogsManager.error("VISION_MISS_JOURNAL: " + e.getMessage());
        }
    }

    public static void recordDomPostClick(String action, String targetHint, DomPostClickValidator.Result result) {
        if (!enabled() || result == null) {
            return;
        }
        // Only interesting outcomes — skip OK/SKIP noise.
        if (result.status() != DomPostClickValidator.Status.FAIL
                && result.status() != DomPostClickValidator.Status.WEAK) {
            return;
        }
        try {
            Path dir = logDir();
            Files.createDirectories(dir);
            JSONObject row = new JSONObject();
            row.put("ts", Instant.now().toString());
            row.put("kind", "dom-post-click");
            row.put("action", action == null ? "" : action);
            row.put("targetHint", targetHint == null ? "" : targetHint);
            row.put("status", result.status().name());
            row.put("reason", result.reason() == null ? "" : result.reason());
            Path file = dir.resolve("dom-post-click-" + dayStamp() + ".jsonl");
            Files.writeString(
                    file,
                    row.toString() + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (Exception e) {
            LogsManager.error("VISION_MISS_JOURNAL: " + e.getMessage());
        }
    }

    private static String dayStamp() {
        return Instant.now().toString().substring(0, 10);
    }

    private static String firstProp(String key) {
        String p = System.getProperty(key);
        if (p == null || p.isBlank()) {
            p = PropertyReader.getProperty(key);
        }
        return p;
    }
}
