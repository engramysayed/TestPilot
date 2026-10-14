package delivery.mobile.auth;

import java.util.ArrayList;
import java.util.List;

/**
 * Rejects jobs before execute when required OTP/PIN (or other per-run secrets) are missing (M3-01).
 * LOGIN_UNDER_TEST still requires declared per-run inputs if the body/login TC declares them.
 */
public final class AuthAdmissionValidator {
    private AuthAdmissionValidator() {
    }

    public static AuthAdmissionResult validateBeforeJob(
            AuthProfileRevision pinnedRevision, SuppliedSecretInputs supplied) {
        if (pinnedRevision == null) {
            return AuthAdmissionResult.reject("AUTH_PROFILE_MISSING", List.of(), "pinned auth revision required");
        }
        SuppliedSecretInputs inputs = supplied == null ? SuppliedSecretInputs.empty() : supplied;
        List<String> missing = new ArrayList<>();
        for (SecretInputDeclaration decl : pinnedRevision.requiredPerRunInputs()) {
            if (!inputs.has(decl.key())) {
                missing.add(decl.key());
            }
        }
        if (!missing.isEmpty()) {
            return AuthAdmissionResult.reject(
                    "AUTH_INPUT_MISSING",
                    missing,
                    "missing required OTP/PIN (or per-run secret) before job: " + missing);
        }
        return AuthAdmissionResult.ok();
    }
}
