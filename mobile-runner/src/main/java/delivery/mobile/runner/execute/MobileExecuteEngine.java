package delivery.mobile.runner.execute;

import delivery.mobile.runner.evidence.XmlRedactor;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Mobile Execute admission/results path (M2-06): every selected case accounted for;
 * SIMULATED excluded; fail-fast; device loss → INTERRUPTED_UNCERTAIN + BLOCKED remainder;
 * results never carry raw secrets.
 */
public final class MobileExecuteEngine {

    public record ExecuteReport(List<CaseResult> results, boolean failFastStopped, boolean deviceLost) {
        public ExecuteReport {
            results = List.copyOf(results);
        }
    }

    /**
     * @param runner executes a non-simulated case; may throw DeviceLostException
     */
    public ExecuteReport run(
            List<SelectedCase> selected,
            boolean failFast,
            Function<SelectedCase, CaseResult> runner
    ) {
        Objects.requireNonNull(runner, "runner");
        List<CaseResult> out = new ArrayList<>();
        if (selected == null) selected = List.of();
        boolean lost = false;
        boolean stopped = false;
        for (int i = 0; i < selected.size(); i++) {
            SelectedCase c = selected.get(i);
            if (lost) {
                out.add(CaseResult.blocked(c.tcId(), "DEVICE_LOST"));
                continue;
            }
            if (stopped) {
                out.add(CaseResult.blocked(c.tcId(), "FAIL_FAST"));
                continue;
            }
            if (c.simulated()) {
                out.add(CaseResult.excludedSimulated(c.tcId()));
                continue;
            }
            try {
                CaseResult r = runner.apply(c);
                r = sanitize(r);
                out.add(r);
                if (failFast && r.outcome() == CaseOutcome.FAILED) {
                    stopped = true;
                }
            } catch (DeviceLostException e) {
                lost = true;
                out.add(CaseResult.interrupted(c.tcId()));
            }
        }
        // account for every selected case
        if (out.size() != selected.size()) {
            throw new IllegalStateException("not every selected case accounted for");
        }
        return new ExecuteReport(out, stopped, lost);
    }

    static CaseResult sanitize(CaseResult r) {
        if (r == null) return null;
        String code = r.failureCode() == null ? "" : r.failureCode();
        if (code.contains(XmlRedactor.SECRET_CANARY)) {
            code = "(redacted)";
        }
        return new CaseResult(r.tcId(), r.outcome(), code, r.evidence(), false);
    }

    public static final class DeviceLostException extends RuntimeException {
        public DeviceLostException(String msg) { super(msg); }
    }
}
