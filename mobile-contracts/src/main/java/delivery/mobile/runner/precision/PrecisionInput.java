package delivery.mobile.runner.precision;

import java.util.List;

public record PrecisionInput(
        List<PrecisionCandidate> candidates,
        String chosenId,
        String stepContext,
        int callsUsed,
        int callCap,
        boolean providerAvailable,
        boolean providerAllowed,
        String providerName,
        String responseText,
        boolean batch
) {
    public PrecisionInput {
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
        chosenId = chosenId == null ? "" : chosenId;
        stepContext = stepContext == null ? "" : stepContext;
        providerName = providerName == null ? "" : providerName;
        responseText = responseText == null ? "" : responseText;
    }
}
