package delivery.mobile.prereq;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** Per-leaf setup closure (M3-03). One selected leaf per plan. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LeafSetupPlan(
        String leafTcId,
        boolean accepted,
        String failureCode,
        String message,
        List<SetupPlanStep> steps
) {
    public LeafSetupPlan {
        leafTcId = leafTcId == null ? "" : leafTcId;
        failureCode = failureCode == null ? "" : failureCode;
        message = message == null ? "" : message;
        steps = steps == null ? List.of() : List.copyOf(steps);
    }

    public static LeafSetupPlan reject(String leaf, String code, String msg) {
        return new LeafSetupPlan(leaf, false, code, msg, List.of());
    }

    public static LeafSetupPlan accept(String leaf, List<SetupPlanStep> steps) {
        return new LeafSetupPlan(leaf, true, "", "", steps);
    }
}
