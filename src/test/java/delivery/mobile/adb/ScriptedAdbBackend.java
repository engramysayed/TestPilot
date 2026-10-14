package delivery.mobile.adb;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Test double: maps joined argv to canned stdout or exceptions. */
public final class ScriptedAdbBackend implements AdbCommandBackend {
    private final Map<String, Function<List<String>, String>> handlers = new LinkedHashMap<>();
    private final List<List<String>> calls = new ArrayList<>();
    private boolean unavailable;

    public ScriptedAdbBackend on(String joinedArgs, String response) {
        handlers.put(joinedArgs, args -> response);
        return this;
    }

    public ScriptedAdbBackend on(String joinedArgs, Function<List<String>, String> fn) {
        handlers.put(joinedArgs, fn);
        return this;
    }

    public ScriptedAdbBackend unavailable() {
        this.unavailable = true;
        return this;
    }

    public List<List<String>> calls() {
        return calls;
    }

    @Override
    public String execute(List<String> args) throws AdbInventoryException {
        calls.add(List.copyOf(args));
        if (unavailable) {
            throw new AdbInventoryException(AdbErrorCode.SDK_UNAVAILABLE, "adb binary missing");
        }
        String key = String.join(" ", args);
        Function<List<String>, String> fn = handlers.get(key);
        if (fn == null) {
            // prefix match for dumpsys etc.
            for (Map.Entry<String, Function<List<String>, String>> e : handlers.entrySet()) {
                if (key.startsWith(e.getKey())) {
                    fn = e.getValue();
                    break;
                }
            }
        }
        if (fn == null) {
            throw new AdbInventoryException(AdbErrorCode.COMMAND_FAILED, "unscripted: " + key);
        }
        return fn.apply(args);
    }
}
