package delivery.mobile.run;

import org.json.JSONArray;
import org.json.JSONObject;

/** A short review of a finished phone batch. Shown on the run and in the run list. */
public final class MobileBatchReview {

    private MobileBatchReview() {
    }

    public static String of(MobileRun run) {
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
        for (int i = 0; i < results.length(); i++) {
            JSONObject row = results.optJSONObject(i);
            if (row == null) {
                continue;
            }
            String outcome = row.optString("outcome", "");
            if ("PASSED".equals(outcome)) {
                passed++;
            } else if ("FAILED".equals(outcome) || "BLOCKED".equals(outcome)) {
                failed++;
                if (fails.length() < 400) {
                    fails.append(' ').append(row.optString("tcId", "case"))
                            .append(": ").append(row.optString("detail", outcome)).append('.');
                }
            }
        }
        if (passed == 0 && failed == 0) {
            return "";
        }
        return "Review: " + passed + " passed, " + failed + " failed." + fails;
    }
}
