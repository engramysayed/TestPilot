package delivery.mobile.prereq;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Case graph node for Call-before / start-state planning (M3-03).
 * {@code callBefore} lists prerequisite TC ids; authProfileRevisionId pins auth for conflict checks.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CaseNode(
        String tcId,
        List<String> callBefore,
        String authProfileRevisionId,
        String startStateTcId,
        boolean negativeLogin,
        boolean selected
) {
    public CaseNode {
        if (tcId == null || tcId.isBlank()) {
            throw new PrereqException("CASE_ID", "tcId required");
        }
        tcId = tcId.trim();
        callBefore = callBefore == null ? List.of() : List.copyOf(callBefore);
        authProfileRevisionId = authProfileRevisionId == null ? "" : authProfileRevisionId.trim();
        startStateTcId = startStateTcId == null ? "" : startStateTcId.trim();
    }
}
