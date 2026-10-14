package delivery.mobile.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Declares a secret input key required by a login TC (M3-01).
 * Profiles store declarations only — never OTP/PIN/password values.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SecretInputDeclaration(
        String key,
        SecretInputKind kind,
        boolean requiredPerRun
) {
    public SecretInputDeclaration {
        if (key == null || key.isBlank()) {
            throw new AuthContractException("SECRET_KEY", "secret input key required");
        }
        key = key.trim();
        kind = kind == null ? SecretInputKind.OTHER : kind;
        if (kind.requiresPerRunSupply()) {
            requiredPerRun = true;
        }
    }

    public static SecretInputDeclaration otp(String key) {
        return new SecretInputDeclaration(key, SecretInputKind.OTP, true);
    }

    public static SecretInputDeclaration pin(String key) {
        return new SecretInputDeclaration(key, SecretInputKind.PIN, true);
    }

    public static SecretInputDeclaration password(String key) {
        return new SecretInputDeclaration(key, SecretInputKind.PASSWORD, false);
    }
}
