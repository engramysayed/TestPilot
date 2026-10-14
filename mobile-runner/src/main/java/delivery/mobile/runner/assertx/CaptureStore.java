package delivery.mobile.runner.assertx;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Per-case capture scope including prerequisites; cases isolated by separate store instances. */
public final class CaptureStore {
    private final Map<String, String> values = new HashMap<>();

    public void put(String name, String value) {
        if (name == null || name.isBlank()) throw new AssertionSemanticsException("CAPTURE_NAME", "capture name required");
        if (value == null) throw new AssertionSemanticsException("CAPTURE_VALUE", "capture value required");
        values.put(name, value);
    }

    public Optional<String> get(String name) {
        return Optional.ofNullable(values.get(name));
    }

    public boolean has(String name) {
        return values.containsKey(name);
    }

    public CaptureStore snapshot() {
        CaptureStore c = new CaptureStore();
        c.values.putAll(values);
        return c;
    }
}
