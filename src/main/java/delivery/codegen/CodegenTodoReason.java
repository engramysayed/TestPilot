package delivery.codegen;

/**
 * Short, operator-friendly TODO stop reason for generated test classes.
 */
public final class CodegenTodoReason {
    private CodegenTodoReason() {
    }

    public static String summarize(String raw, int maxLen) {
        if (raw == null || raw.isBlank()) {
            return "conversion incomplete";
        }
        String r = raw.trim();
        if (r.toUpperCase().startsWith("HEAL_EXHAUSTED:")) {
            r = r.substring("HEAL_EXHAUSTED:".length()).trim();
        }
        int nl = r.indexOf('\n');
        if (nl >= 0) {
            r = r.substring(0, nl).trim();
        }
        r = r.replaceAll("\\s+", " ").trim();
        if (r.isEmpty()) {
            return "conversion incomplete";
        }
        int limit = Math.max(20, maxLen);
        if (r.length() <= limit) {
            return r;
        }
        return r.substring(0, limit - 1).trim() + "…";
    }
}
