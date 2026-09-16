package delivery.excel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CallBefore {

    private CallBefore() {
    }

    public static List<String> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String part : raw.split(",")) {
            String id = part.trim();
            if (!id.isEmpty()) {
                out.add(id);
            }
        }
        return out;
    }

    public static String format(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String id : ids) {
            if (id == null || id.isBlank()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append(',');
            }
            sb.append(id.trim());
        }
        return sb.toString();
    }

    /** Rejects self-reference and IDs missing from {@code knownIds}. */
    public static void validateRefs(String callBefore, String selfId, Set<String> knownIds) {
        if (callBefore == null || callBefore.isBlank()) {
            return;
        }
        String self = selfId == null ? "" : selfId.trim();
        Set<String> known = knownIds == null ? Set.of() : knownIds;
        for (String ref : parse(callBefore)) {
            if (ref.equals(self)) {
                throw new IllegalArgumentException("CALL_BEFORE_SELF: " + ref);
            }
            if (!known.contains(ref)) {
                throw new IllegalArgumentException("UNKNOWN_CALL_BEFORE: " + ref);
            }
        }
    }

    /** Self-refs, unknown ids, and cycles. */
    public static void validateGraph(List<ManualTestCase> cases) {
        if (cases == null || cases.isEmpty()) {
            return;
        }
        Map<String, ManualTestCase> byId = new LinkedHashMap<>();
        for (ManualTestCase tc : cases) {
            if (tc == null || tc.tcId() == null || tc.tcId().isBlank()) {
                continue;
            }
            byId.put(tc.tcId().trim(), tc);
        }
        Set<String> known = byId.keySet();
        for (ManualTestCase tc : byId.values()) {
            validateRefs(tc.callBefore(), tc.tcId(), known);
        }
        for (String id : byId.keySet()) {
            detectCycle(id, byId, new LinkedHashSet<>());
        }
    }

    private static void detectCycle(String id, Map<String, ManualTestCase> byId, Set<String> path) {
        if (path.contains(id)) {
            List<String> cycle = new ArrayList<>(path);
            cycle.add(id);
            throw new IllegalArgumentException("CALL_BEFORE_CYCLE: " + String.join(" → ", cycle));
        }
        ManualTestCase tc = byId.get(id);
        if (tc == null) {
            return;
        }
        path.add(id);
        for (String ref : parse(tc.callBefore())) {
            detectCycle(ref, byId, path);
        }
        path.remove(id);
    }
}
