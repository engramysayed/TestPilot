package delivery.hunt;

import delivery.job.JobCancelledException;
import delivery.mobile.run.MobileRun;
import delivery.mobile.run.MobileRunOrchestrator;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.function.BooleanSupplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Waits for the phone explorer and writes the hunt zip the job page already downloads. */
public final class AndroidHuntPack {

    private AndroidHuntPack() {
    }

    public static HuntJobResult pack(MobileRunOrchestrator runs, String runId, Path huntRoot, BooleanSupplier cancel)
            throws Exception {
        if (runId == null || runId.isBlank()) {
            throw new IllegalStateException("Android hunt has no phone run");
        }
        Instant deadline = Instant.now().plus(Duration.ofMinutes(12));
        JSONObject view = null;
        while (Instant.now().isBefore(deadline)) {
            if (cancel != null && cancel.getAsBoolean()) {
                runs.cancel(runId);
                throw new JobCancelledException();
            }
            view = runs.view(runId).orElse(null);
            if (view != null && terminal(view.optString("status", ""))) {
                break;
            }
            Thread.sleep(2000);
        }
        if (view == null || !terminal(view.optString("status", ""))) {
            runs.cancel(runId);
            throw new IllegalStateException("The phone hunt did not finish");
        }
        String status = view.optString("status", "");
        if ("FAILED".equals(status) || "CANCELLED".equals(status)) {
            String err = view.optString("error", "");
            String message = view.optString("message", "The phone hunt failed");
            throw new IllegalStateException(err.isBlank() ? message : message + " " + err);
        }
        JSONArray results = view.optJSONArray("results");
        JSONArray findings = new JSONArray();
        if (results != null) {
            for (int i = 0; i < results.length(); i++) {
                JSONObject row = results.optJSONObject(i);
                if (row == null) {
                    continue;
                }
                JSONArray nested = row.optJSONArray("findings");
                if (nested == null) {
                    continue;
                }
                for (int j = 0; j < nested.length(); j++) {
                    findings.put(nested.get(j));
                }
            }
        }
        int bugs = 0;
        StringBuilder report = new StringBuilder();
        report.append("# Android Bug Hunter\n\n");
        for (int i = 0; i < findings.length(); i++) {
            JSONObject f = findings.optJSONObject(i);
            if (f == null) {
                continue;
            }
            String kind = f.optString("kind", "NOTE");
            String detail = f.optString("detail", "");
            if ("BUG".equals(kind)) {
                bugs++;
            }
            report.append("- ").append(kind).append(": ").append(detail).append('\n');
        }
        if (findings.isEmpty()) {
            report.append("The phone run finished with no notes.\n");
        }
        Files.createDirectories(huntRoot);
        Files.writeString(huntRoot.resolve("findings.json"), findings.toString(2), StandardCharsets.UTF_8);
        Files.writeString(huntRoot.resolve("report.md"), report.toString(), StandardCharsets.UTF_8);
        Path zip = huntRoot.resolve("hunt.zip");
        try (OutputStream raw = Files.newOutputStream(zip); ZipOutputStream zos = new ZipOutputStream(raw)) {
            put(zos, "findings.json", findings.toString(2));
            put(zos, "report.md", report.toString());
        }
        String message = bugs == 0
                ? "Explored the app. No crash dialog turned up."
                : "Explored the app. " + bugs + " problem" + (bugs == 1 ? "" : "s") + " on screen.";
        return new HuntJobResult(zip, bugs, 0, message, "android-explore");
    }

    private static boolean terminal(String status) {
        return MobileRun.Status.COMPLETED.name().equals(status)
                || MobileRun.Status.FAILED.name().equals(status)
                || MobileRun.Status.CANCELLED.name().equals(status);
    }

    private static void put(ZipOutputStream zos, String name, String text) throws Exception {
        zos.putNextEntry(new ZipEntry(name));
        zos.write(text.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }
}
