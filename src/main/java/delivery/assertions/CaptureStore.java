package delivery.assertions;

import java.util.LinkedHashMap;
import java.util.Map;

/** Prove-time per-thread captures. Cleared at the start of each TC. */
public final class CaptureStore {
    private static final ThreadLocal<Map<String, String>> SLOTS =
            ThreadLocal.withInitial(LinkedHashMap::new);

    private CaptureStore() {
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
