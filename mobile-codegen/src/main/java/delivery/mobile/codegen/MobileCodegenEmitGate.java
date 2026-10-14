package delivery.mobile.codegen;

import delivery.mobile.ir.MobileCaseIr;
import delivery.mobile.ir.MobileStep;

import java.util.ArrayList;
import java.util.List;

/**
 * Mirror web 0064: demote dump/blank screenKey TCs to TODO (clear steps) rather than
 * aborting the whole job when the suite is mixed.
 */
public final class MobileCodegenEmitGate {

    public static final String DEMOTE_REASON =
            "CODEGEN: dump/blank screen stem — demoted to TODO (cannot emit Screen class)";

    private MobileCodegenEmitGate() {}

    public static List<CaseEmitPlan> prepare(List<MobileCaseIr> cases) {
        List<CaseEmitPlan> demoted = demoteDumpScreenStems(cases);
        // Smell-check only non-TODO plans' screens — done by writer after accumulate of emit bodies
        return demoted;
    }

    public static List<CaseEmitPlan> demoteDumpScreenStems(List<MobileCaseIr> cases) {
        List<CaseEmitPlan> out = new ArrayList<>();
        if (cases == null) {
            return out;
        }
        for (MobileCaseIr c : cases) {
            if (c == null) {
                continue;
            }
            if (hasDumpScreen(c)) {
                out.add(CaseEmitPlan.todo(c, DEMOTE_REASON));
            } else {
                out.add(CaseEmitPlan.generated(c));
            }
        }
        return out;
    }

    static boolean hasDumpScreen(MobileCaseIr c) {
        if (c.steps() == null) {
            return false;
        }
        for (MobileStep step : c.steps()) {
            if (step != null && MobileCodegenSmellCheck.isDumpScreenKey(step.screenKey())) {
                return true;
            }
        }
        return false;
    }

    /** Emit plan for one case — generated body or TODO with cleared steps. */
    public record CaseEmitPlan(MobileCaseIr original, boolean todo, String reason, List<MobileStep> emitSteps) {
        public static CaseEmitPlan generated(MobileCaseIr c) {
            return new CaseEmitPlan(c, false, null, c.steps());
        }

        public static CaseEmitPlan todo(MobileCaseIr c, String reason) {
            return new CaseEmitPlan(c, true, reason, List.of());
        }

        public String tcId() {
            return original.tcId();
        }
    }
}
