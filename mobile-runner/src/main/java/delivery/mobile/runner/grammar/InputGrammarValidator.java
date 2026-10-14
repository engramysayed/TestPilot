package delivery.mobile.runner.grammar;

import delivery.mobile.ir.MobileInput;
import delivery.mobile.ir.MobileIrException;
import delivery.mobile.ir.MobileOperation;
import delivery.mobile.ir.MobileStep;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Validates step list + overrides: unknown ops rejected; SECRET_REF required when marked;
 * no inferred assertions; reordering does not reattach overrides (keyed by stepId).
 */
public final class InputGrammarValidator {
    public record Result(boolean ok, List<String> errors) {}

    public Result validate(List<MobileStep> steps, List<LocatorOverride> overrides, Set<String> requiredSecretKeys) {
        List<String> errors = new ArrayList<>();
        if (steps == null) steps = List.of();
        Set<String> stepIds = new HashSet<>();
        for (MobileStep st : steps) {
            if (!stepIds.add(st.stepId())) {
                errors.add("DUPLICATE_STEP: " + st.stepId());
            }
            try {
                MobileOperation.parse(st.operation().name());
            } catch (MobileIrException e) {
                errors.add("UNKNOWN_OP: " + e.getMessage());
            }
            if (st.operation() == MobileOperation.TYPE && st.input() != null) {
                MobileInput in = st.input();
                if ("SECRET_REF".equals(in.kind()) && (in.key() == null || in.key().isBlank())) {
                    errors.add("SECRET_REF_KEY: " + st.stepId());
                }
            }
            // no inferred assertions: ASSERT must declare assertType
            if (st.operation() == MobileOperation.ASSERT
                    && (st.assertType() == null || st.assertType().isBlank())) {
                errors.add("NO_INFERRED_ASSERT: " + st.stepId());
            }
        }
        if (requiredSecretKeys != null) {
            Set<String> present = new HashSet<>();
            for (MobileStep st : steps) {
                if (st.input() != null && "SECRET_REF".equals(st.input().kind())) {
                    present.add(st.input().key());
                }
            }
            for (String need : requiredSecretKeys) {
                if (!present.contains(need)) {
                    errors.add("REQUIRED_SECRET_REF: " + need);
                }
            }
        }
        if (overrides != null) {
            for (LocatorOverride o : overrides) {
                if (!stepIds.contains(o.stepId())) {
                    // override for unknown step — conflict/review, not silent rebind
                    errors.add("OVERRIDE_UNKNOWN_STEP: " + o.tcId() + "/" + o.stepId());
                }
            }
        }
        return new Result(errors.isEmpty(), List.copyOf(errors));
    }
}
