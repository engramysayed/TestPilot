package delivery.mobile.codegen;

/**
 * Secret-ref helpers for mobile codegen (web CodegenSecrets / 0065 mirror).
 * Never embeds plaintext secret values into generated Java or Allure parameters.
 */
public final class MobileCodeSecrets {

    /** Display mask for redacted conflict / log messages. */
    public static final String MASK = "********";

    private MobileCodeSecrets() {}

    public static boolean isSecretKind(String inputKind) {
        if (inputKind == null) {
            return false;
        }
        String k = inputKind.trim().toUpperCase();
        return "SECRET_REF".equals(k) || "TYPE_SECRET".equals(k);
    }

    public static String displayValue(String value, boolean secret) {
        if (secret) {
            return MASK;
        }
        return value == null ? "" : value;
    }

    /** Allure / step label: secret-ref key only, never the resolved value. */
    public static String refLabel(String logicalKey) {
        if (logicalKey == null || logicalKey.isBlank()) {
            return "secret-ref:<missing>";
        }
        return "secret-ref:" + logicalKey.trim();
    }
}
