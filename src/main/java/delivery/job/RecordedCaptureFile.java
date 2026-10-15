package delivery.job;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Sidecar the Automate worker reads so a recorded job skips the browser. */
public final class RecordedCaptureFile {
    private RecordedCaptureFile() {
    }

    public static Path path(Path workDir, String jobId) {
        return workDir.resolve("recorded-jobs").resolve(jobId + ".json");
    }

    public static void write(Path file, List<RecordedCapture> captures) throws Exception {
        Files.createDirectories(file.getParent());
        JSONArray rows = new JSONArray();
        if (captures != null) {
            for (RecordedCapture capture : captures) {
                if (capture == null) {
                    continue;
                }
                JSONObject row = new JSONObject();
                row.put("kind", capture.kind());
                row.put("label", capture.label());
                row.put("value", capture.secret() ? "" : capture.value());
                row.put("secret", capture.secret());
                row.put("strategy", capture.strategy());
                row.put("locator", capture.locator());
                row.put("pageUrl", capture.pageUrl());
                rows.put(row);
            }
        }
        JSONObject root = new JSONObject();
        root.put("captures", rows);
        Files.writeString(file, root.toString(2), StandardCharsets.UTF_8);
    }

    public static List<RecordedCapture> read(Path file) throws Exception {
        JSONObject root = new JSONObject(Files.readString(file, StandardCharsets.UTF_8));
        JSONArray rows = root.optJSONArray("captures");
        List<RecordedCapture> out = new ArrayList<>();
        if (rows == null) {
            return out;
        }
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.getJSONObject(i);
            out.add(new RecordedCapture(
                    row.optString("kind", ""),
                    row.optString("label", ""),
                    row.optString("value", ""),
                    row.optBoolean("secret", false),
                    row.optString("strategy", ""),
                    row.optString("locator", ""),
                    row.optString("pageUrl", "")));
        }
        return out;
    }
}
