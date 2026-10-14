package project.utils.reports;

/** Secret-ref helpers — never embed plaintext values into Allure params or logs. */
public final class Secrets {
    private Secrets() {}

    public static String refLabel(String logicalKey) {
        if (logicalKey == null || logicalKey.isBlank()) {
            return "<missing-secret-ref>";
        }
        return "secret-ref:" + logicalKey.trim();
    }

    public static boolean looksLikeSecretKey(String key) {
        if (key == null) {
            return false;
        }
        String k = key.toLowerCase();
        return k.contains("password") || k.contains("secret") || k.contains("token")
                || k.contains("otp") || k.contains("pin");
    }
}
