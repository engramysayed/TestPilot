package delivery.mobile.runner.grammar;

import java.util.List;

/** Companion case-settings revision binding (contracts mobile_case_settings). */
public record MobileCaseSettings(
        String libraryRevisionId,
        String tcId,
        String caseSettingsRevisionId,
        String authMode,
        String authProfileRevisionId,
        List<String> startingStateTcIds,
        String permissionOverride,
        String contextDefault,
        Integer timeoutMs,
        Boolean failFast
) {
    public MobileCaseSettings {
        if (libraryRevisionId == null || libraryRevisionId.isBlank()) {
            throw new GrammarException("CASE_LIBRARY", "libraryRevisionId required");
        }
        if (tcId == null || tcId.isBlank()) {
            throw new GrammarException("CASE_TC", "tcId required");
        }
        if (caseSettingsRevisionId == null || caseSettingsRevisionId.isBlank()) {
            throw new GrammarException("CASE_SETTINGS_REV", "caseSettingsRevisionId required");
        }
        startingStateTcIds = startingStateTcIds == null ? List.of() : List.copyOf(startingStateTcIds);
        contextDefault = contextDefault == null || contextDefault.isBlank() ? "NATIVE" : contextDefault;
        failFast = failFast != null && failFast;
        authMode = authMode == null ? "" : authMode;
        authProfileRevisionId = authProfileRevisionId == null ? "" : authProfileRevisionId;
        permissionOverride = permissionOverride == null ? "" : permissionOverride;
    }
}
