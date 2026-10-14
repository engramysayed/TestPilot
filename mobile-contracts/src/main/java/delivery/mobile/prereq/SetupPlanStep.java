package delivery.mobile.prereq;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SetupPlanStep(
        SetupPhaseKind phase,
        String tcId,
        String authProfileRevisionId,
        boolean sharedOnce
) {
    public SetupPlanStep {
        phase = phase == null ? SetupPhaseKind.PREREQUISITE : phase;
        tcId = tcId == null ? "" : tcId;
        authProfileRevisionId = authProfileRevisionId == null ? "" : authProfileRevisionId;
    }
}
