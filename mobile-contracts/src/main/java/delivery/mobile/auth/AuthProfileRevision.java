package delivery.mobile.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Immutable auth-profile revision (M3-01 / architecture §7).
 * Secrets are references/declarations only — never embedded values.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AuthProfileRevision(
        String profileId,
        String revisionId,
        String tenantId,
        String projectId,
        String loginTcId,
        String libraryRevisionId,
        AuthMode defaultMode,
        List<String> sessionPredicates,
        List<String> signedOutPredicates,
        String logoutTcId,
        String setupTcId,
        LogicalAccountRef account,
        List<SecretInputDeclaration> inputDeclarations,
        List<ProtectedCredentialRef> protectedCredentialRefs,
        long createdAtEpochMs
) {
    public AuthProfileRevision {
        require(profileId, "profileId");
        require(revisionId, "revisionId");
        profileId = profileId.trim();
        revisionId = revisionId.trim();
        tenantId = nz(tenantId);
        projectId = nz(projectId);
        loginTcId = nz(loginTcId);
        libraryRevisionId = nz(libraryRevisionId);
        defaultMode = defaultMode == null ? AuthMode.ENSURE_LOGGED_IN : defaultMode;
        sessionPredicates = copy(sessionPredicates);
        signedOutPredicates = copy(signedOutPredicates);
        logoutTcId = nz(logoutTcId);
        setupTcId = nz(setupTcId);
        inputDeclarations = inputDeclarations == null ? List.of() : List.copyOf(inputDeclarations);
        protectedCredentialRefs = protectedCredentialRefs == null ? List.of() : List.copyOf(protectedCredentialRefs);
        if (createdAtEpochMs < 0) {
            createdAtEpochMs = 0;
        }
    }

    public List<SecretInputDeclaration> requiredPerRunInputs() {
        return inputDeclarations.stream().filter(SecretInputDeclaration::requiredPerRun).toList();
    }

    /** Edits produce a new revision; this instance stays unchanged. */
    public AuthProfileRevision withNewRevision(
            String newRevisionId,
            long createdAt,
            List<SecretInputDeclaration> newDecls,
            List<ProtectedCredentialRef> newCreds,
            LogicalAccountRef newAccount) {
        return new AuthProfileRevision(
                profileId, newRevisionId, tenantId, projectId, loginTcId, libraryRevisionId,
                defaultMode, sessionPredicates, signedOutPredicates, logoutTcId, setupTcId,
                newAccount != null ? newAccount : account,
                newDecls != null ? newDecls : inputDeclarations,
                newCreds != null ? newCreds : protectedCredentialRefs,
                createdAt);
    }

    private static void require(String v, String name) {
        if (v == null || v.isBlank()) {
            throw new AuthContractException("PROFILE_FIELD", name + " required");
        }
    }

    private static String nz(String v) {
        return v == null ? "" : v.trim();
    }

    private static List<String> copy(List<String> in) {
        return in == null ? List.of() : List.copyOf(in);
    }
}
