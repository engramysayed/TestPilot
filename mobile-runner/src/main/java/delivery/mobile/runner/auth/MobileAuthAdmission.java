package delivery.mobile.runner.auth;

import delivery.mobile.auth.AuthAdmissionResult;
import delivery.mobile.auth.AuthAdmissionValidator;
import delivery.mobile.auth.AuthProfileRevision;
import delivery.mobile.auth.SuppliedSecretInputs;

/**
 * Runner admission hook (M3-01): reject missing OTP/PIN before execute.
 */
public final class MobileAuthAdmission {
    public static final String FAILURE_CODE = "AUTH_INPUT_MISSING";

    public MobileAuthAdmission() {
    }

    public AuthAdmissionResult check(AuthProfileRevision pinned, SuppliedSecretInputs supplied) {
        return AuthAdmissionValidator.validateBeforeJob(pinned, supplied);
    }

    /**
     * @throws AuthAdmissionRejectedException when required per-run secrets are absent
     */
    public void assertReady(AuthProfileRevision pinned, SuppliedSecretInputs supplied) {
        AuthAdmissionResult r = check(pinned, supplied);
        if (!r.accepted()) {
            throw new AuthAdmissionRejectedException(r);
        }
    }
}
