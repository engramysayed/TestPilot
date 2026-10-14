package delivery.mobile.runner.auth;

import delivery.mobile.auth.AuthAdmissionResult;

public final class AuthAdmissionRejectedException extends RuntimeException {
    private final AuthAdmissionResult result;

    public AuthAdmissionRejectedException(AuthAdmissionResult result) {
        super(result == null ? "auth admission rejected" : result.message());
        this.result = result;
    }

    public AuthAdmissionResult result() {
        return result;
    }

    public String failureCode() {
        return result == null ? MobileAuthAdmission.FAILURE_CODE : result.code();
    }
}
