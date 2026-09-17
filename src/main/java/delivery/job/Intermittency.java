package delivery.job;

import java.util.List;

/** Intermittency requires a comparable sample; one retry is never enough. */
public final class Intermittency {
    public enum Verdict { STABLE_PASS, STABLE_FAIL, INTERMITTENT, INSUFFICIENT_SAMPLE }

    public static final int MIN_SAMPLE = 3;

    public record Outcome(String caseId, boolean passed) {
    }

    private Intermittency() {
    }

    public static Verdict verdict(List<Outcome> comparable) {
        if (comparable == null || comparable.size() < MIN_SAMPLE) {
            return Verdict.INSUFFICIENT_SAMPLE;
        }
        int pass = 0;
        int fail = 0;
        for (Outcome o : comparable) {
            if (o != null && o.passed()) {
                pass++;
            } else {
                fail++;
            }
        }
        if (pass == comparable.size()) {
            return Verdict.STABLE_PASS;
        }
        if (fail == comparable.size()) {
            return Verdict.STABLE_FAIL;
        }
        return Verdict.INTERMITTENT;
    }
}
