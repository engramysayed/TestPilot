package delivery.mobile.auth;

import java.util.List;

public record AuthAdmissionResult(boolean accepted, String code, List<String> missingKeys, String message) {
    public AuthAdmissionResult {
        missingKeys = missingKeys == null ? List.of() : List.copyOf(missingKeys);
        code = code == null ? "" : code;
        message = message == null ? "" : message;
    }

    public static AuthAdmissionResult ok() {
        return new AuthAdmissionResult(true, "", List.of(), "");
    }

    public static AuthAdmissionResult reject(String code, List<String> missing, String message) {
        return new AuthAdmissionResult(false, code, missing, message);
    }
}
