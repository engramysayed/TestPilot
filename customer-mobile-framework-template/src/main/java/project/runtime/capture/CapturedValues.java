package project.runtime.capture;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Per-invocation capture store (prove CAPTURE_TEXT / COMPARE_CAPTURED semantics).
 * Cleared in BaseMobileTest {@code @BeforeMethod}/{@code @AfterMethod} — per-leaf independence.
 */
public final class CapturedValues {

    private static final ThreadLocal<Map<String, String>> STORE =
            ThreadLocal.withInitial(LinkedHashMap::new);

    private CapturedValues() {}

    public static void put(String variableName, String value) {
        if (variableName == null || variableName.isBlank()) {
            throw new IllegalArgumentException("capture variableName required");
        }
        STORE.get().put(variableName.trim(), value == null ? "" : value);
    }

    public static String get(String variableName) {
        if (variableName == null || variableName.isBlank()) {
            throw new IllegalArgumentException("capture variableName required");
        }
        String v = STORE.get().get(variableName.trim());
        if (v == null) {
            throw new IllegalStateException("No captured value for '" + variableName + "'");
        }
        return v;
    }

    public static boolean contains(String variableName) {
        return variableName != null && STORE.get().containsKey(variableName.trim());
    }

    /** Clear all captures for this thread (call from BaseMobileTest teardown/setup). */
    public static void clear() {
        STORE.get().clear();
    }
}
