package delivery.mobile.auth;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Per-run supplied secrets (M3-01). Values are held only for admission/execute;
 * {@link #toString()} and {@link #redactedMap()} never expose raw values.
 */
public final class SuppliedSecretInputs {
    private final Map<String, String> values;

    private SuppliedSecretInputs(Map<String, String> values) {
        this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    public static SuppliedSecretInputs of(Map<String, String> raw) {
        Map<String, String> copy = new LinkedHashMap<>();
        if (raw != null) {
            raw.forEach((k, v) -> {
                if (k != null && !k.isBlank() && v != null) {
                    copy.put(k.trim(), v);
                }
            });
        }
        return new SuppliedSecretInputs(copy);
    }

    public static SuppliedSecretInputs empty() {
        return new SuppliedSecretInputs(Map.of());
    }

    public boolean has(String key) {
        return key != null && values.containsKey(key.trim()) && !values.get(key.trim()).isBlank();
    }

    public Set<String> keys() {
        return values.keySet();
    }

    /** Execute-path only; callers must not log the return value. */
    public String getForExecute(String key) {
        if (!has(key)) {
            return null;
        }
        return values.get(key.trim());
    }

    /** Safe map for manifests/logs: keys present, values always redacted. */
    public Map<String, String> redactedMap() {
        Map<String, String> out = new LinkedHashMap<>();
        for (String k : values.keySet()) {
            out.put(k, "(redacted)");
        }
        return Collections.unmodifiableMap(out);
    }

    @Override
    public String toString() {
        return "SuppliedSecretInputs{keys=" + values.keySet() + "}";
    }
}
