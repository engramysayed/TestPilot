package delivery.mobile.codegen;

import delivery.mobile.ir.MobileCaseIr;
import delivery.mobile.ir.MobileOperation;
import delivery.mobile.ir.MobileStep;

import java.util.ArrayList;
import java.util.List;

/**
 * Mirror web 0064: demote dump/blank screenKey TCs to TODO (clear steps) rather than
 * aborting the whole job when the suite is mixed.
 * <p>M4-08: also demote SCROLL_UNTIL without target (never fake tap).
 */
public final class MobileCodegenEmitGate {

    public static final String DEMOTE_REASON =
            "CODEGEN: dump/blank screen stem — demoted to TODO (cannot emit Screen class)";

    public static final String SCROLL_DEMOTE_REASON =
            "CODEGEN: SCROLL_UNTIL missing target locator — demoted to TODO (cannot emit scrollUntil)";

    public static final String IDENTITY_DEMOTE_REASON =
            "CODEGEN: chron identity unresolved — demoted to TODO (never prefix-first bind)";

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
                String scrollReason = scrollUntilMissingTargetReason(c);
                if (scrollReason != null) {
                    out.add(CaseEmitPlan.todo(c, scrollReason));
                } else {
                    out.add(CaseEmitPlan.generated(c));
                }
            }
        }
        return out;
    }

    /** Null if OK; reason string if SCROLL_UNTIL lacks target (container/target locator). */
    public static String scrollUntilMissingTargetReason(MobileCaseIr c) {
        if (c == null || c.steps() == null) {
            return null;
        }
        for (MobileStep step : c.steps()) {
            if (step != null
                    && step.operation() == MobileOperation.SCROLL_UNTIL
                    && step.target() == null) {
                return SCROLL_DEMOTE_REASON;
            }
        }
        return null;
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
