package delivery.mobile.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Portal case-requirement fields for mobile auth / Call-before (M3-06 Final).
 * Separate from workbook {@code ManualTestCase.callBefore} (excel/web string);
 * this is the mobile requirements store with array callBefore + auth pins.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MobileCaseRequirementsView(
        String tcId,
        AuthMode authMode,
        List<String> callBefore,
        String authProfileRevisionId,
        String startStateTcId,
        boolean negativeLogin
) {
    public MobileCaseRequirementsView {
        if (tcId == null || tcId.isBlank()) {
            throw new AuthContractException("CASE_ID", "tcId required");
        }
        tcId = tcId.trim();
        // authMode may be null → profile default at execute time
        callBefore = callBefore == null ? List.of() : List.copyOf(callBefore);
        authProfileRevisionId = authProfileRevisionId == null ? "" : authProfileRevisionId.trim();
        startStateTcId = startStateTcId == null ? "" : startStateTcId.trim();
    }

    public static MobileCaseRequirementsView empty(String tcId) {
        return new MobileCaseRequirementsView(tcId, null, List.of(), "", "", false);
    }
}
