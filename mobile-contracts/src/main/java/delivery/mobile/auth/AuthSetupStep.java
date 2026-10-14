package delivery.mobile.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AuthSetupStep(
        AuthSetupPhase phase,
        String tcRef,
        String reason
) {
    public AuthSetupStep {
        phase = phase == null ? AuthSetupPhase.SESSION_CHECK : phase;
        tcRef = tcRef == null ? "" : tcRef;
        reason = reason == null ? "" : reason;
    }
}
