package delivery.excel;

import java.util.ArrayList;
import java.util.List;
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
}
