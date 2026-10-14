package project.utils.reports;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Run-wide registry of values typed by IR {@code secret: true} steps. Generated page actions register
 * the value just before typing it; {@link #redact(String)} is applied by LogsManager, KeelResults
 * (verdicts/locator-events jsonl) and AllureSteps (step status + rethrown message), so the value never
 * reaches logs or reports even if a driver/exception message echoes it.
 */
public final class Secrets {
    /** Same mask as the engine (SecretStep.MASK, r2 SPEC section 2). */
    public static final String MASK = "\u2022\u2022\u2022";
    /** Values shorter than this are not substring-masked (a 1-2 char value would shred every log line). */
    static final int MIN_LEN = 3;
    private static final Set<String> VALUES = ConcurrentHashMap.newKeySet();

    private Secrets() {
    }

    /** Registers a secret value and returns it unchanged (so call sites stay one expression). */
    public static String register(String value) {
        if (value != null && value.length() >= MIN_LEN) {
            VALUES.add(value);
        }
        return value;
    }

    public static boolean containsSecret(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        for (String v : VALUES) {
            if (text.contains(v)) {
                return true;
            }
        }
        return false;
    }

    public static String redact(String text) {
        if (text == null || text.isEmpty() || VALUES.isEmpty()) {
            return text;
        }
        String out = text;
        // longest first so a secret containing another secret is masked whole
        for (String v : VALUES.stream().sorted((a, b) -> b.length() - a.length()).toList()) {
            out = out.replace(v, MASK);
        }
        return out;
    }

    /**
     * Value for an IR {@code secret: true} step, read from the secret store or env, never from code:
     * {@code -D<key>}, then env {@code <key>}, then env {@code KEEL_SECRET_<KEY>} (non-alphanumerics to
     * '_', upper case), then the local secrets file / test data via PropertyReader. Registered for redaction.
     * Throws when missing (the message names the key only).
     */
    public static String property(String key) {
        String v = System.getProperty(key);
        if (v == null) {
            v = System.getenv(key);
        }
        if (v == null) {
            v = System.getenv(envName(key));
        }
        if (v == null) {
            v = project.utils.dataReader.PropertyReader.getProperty(key);
        }
        if (v == null) {
            throw new IllegalStateException("Missing secret test data key: " + key + " (set -D" + key + ", env "
                    + envName(key) + ", or test-data/delivery-secrets.local.properties)");
        }
        return register(v);
    }

    public static String envName(String key) {
        return "KEEL_SECRET_" + (key == null ? "" : key.replaceAll("[^A-Za-z0-9]", "_").toUpperCase(java.util.Locale.ROOT));
    }

    /** Test hook. */
    static void clear() {
        VALUES.clear();
    }
}
