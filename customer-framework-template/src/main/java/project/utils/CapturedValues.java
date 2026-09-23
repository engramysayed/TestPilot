package project.utils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Per-thread phrases captured during one browser/test execution (BeforeMethod + @Test).
 * Not shared across TestNG classes or worker threads.
 */
public final class CapturedValues {
    private static final ThreadLocal<Map<String, String>> SLOTS =
            ThreadLocal.withInitial(LinkedHashMap::new);

    private CapturedValues() {
    }

    public static void put(String slot, String value) {
        if (slot == null || slot.isBlank() || value == null) {
            return;
        }
        SLOTS.get().put(slot, value);
    }

    public static String get(String slot) {
        if (slot == null || slot.isBlank()) {
            return null;
        }
        return SLOTS.get().get(slot);
    }

    public static void clear() {
        SLOTS.get().clear();
        SLOTS.remove();
    }
}
