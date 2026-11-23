package project.utils.reports;

import java.util.concurrent.ConcurrentHashMap;

/** Secret-ref helpers — never embed plaintext values into Allure params or logs. */
public final class Secrets {

    /** Values recorded by {@link #register} get replaced with ${SECRET} inside {@link #redact}. */
    private static final ConcurrentHashMap<String, Boolean> REGISTERED = new ConcurrentHashMap<>();

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

    /** Record a value so {@link #redact} can scrub it from locator-outcomes / verdicts lines. */
    public static void register(String value) {
        if (value == null || value.length() < 3) {
            return;
        }
        REGISTERED.putIfAbsent(value, Boolean.TRUE);
    }

    /** Replace every registered value in {@code line} with {@code ${SECRET}}; safe to call on any string. */
    public static String redact(String line) {
        if (line == null || line.isEmpty() || REGISTERED.isEmpty()) {
            return line;
        }
        String out = line;
        for (String v : REGISTERED.keySet()) {
            if (v != null && !v.isEmpty() && out.contains(v)) {
                out = out.replace(v, "${SECRET}");
            }
        }
        return out;
    }
}
