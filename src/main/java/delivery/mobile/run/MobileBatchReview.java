package delivery.mobile.run;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.file.Files;
import java.nio.file.Path;

/** A short review written after a phone batch finishes, including design compare when it ran. */
public final class MobileBatchReview {

    private MobileBatchReview() {
    }

    public static String of(MobileRun run) {
        return of(run, null);
    }

    public static String of(MobileRun run, Path evidenceRoot) {
        if (run == null) {
            return "";
        }
        JSONArray results = run.view().optJSONArray("results");
        if (results == null || results.length() == 0) {
            return "";
        }
        int passed = 0;
        int failed = 0;
        StringBuilder fails = new StringBuilder();
        StringBuilder design = new StringBuilder();
        for (int i = 0; i < results.length(); i++) {
            JSONObject row = results.optJSONObject(i);
            if (row == null) {
                continue;
            }
            String tcId = row.optString("tcId", "case");
            String outcome = row.optString("outcome", "");
            if ("PASSED".equals(outcome)) {
                passed++;
            } else if ("FAILED".equals(outcome) || "BLOCKED".equals(outcome)) {
                failed++;
                if (fails.length() < 500) {
                    int step = row.optInt("failedStep", -1);
                    String detail = row.optString("detail", outcome);
                    fails.append(' ').append(tcId);
                    if (step > 0) {
                        fails.append(" failed at step ").append(step);
                    } else {
                        fails.append(" failed");
                    }
                    if (!detail.isBlank()) {
                        fails.append(": ").append(detail);
                    }
                    fails.append('.');
                }
            }
            String designStatus = designStatus(evidenceRoot, tcId);
            if (!designStatus.isBlank() && design.length() < 300) {
                design.append(' ').append(tcId).append(' ').append(designStatus).append('.');
            }
        }
        if (passed == 0 && failed == 0) {
            return "";
        }
        String text = "After this batch: " + passed + " passed, " + failed + " failed." + fails;
        if (design.length() > 0) {
            text = text + " Design:" + design;
        }
        return text;
    }

    private static String designStatus(Path evidenceRoot, String tcId) {
        if (evidenceRoot == null || tcId == null || tcId.isBlank()) {
            return "";
        }
        Path json = evidenceRoot.resolve(tcId).resolve("design-compare.json");
        if (!Files.isRegularFile(json)) {
            return "";
        }
        try {
            return new JSONObject(Files.readString(json)).optString("status", "");
        } catch (Exception ignored) {
            return "";
        }
    }
}
