package delivery.knowledge;

/** How a knowledge pack was created in the project store. */
public enum KnowledgePackSource {
    LIBRARY,
    UPLOAD_TC,
    UPLOAD_MD;

    public static KnowledgePackSource parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return LIBRARY;
        }
        String t = raw.trim().toUpperCase(java.util.Locale.ROOT).replace('-', '_');
        for (KnowledgePackSource s : values()) {
            if (s.name().equals(t)) {
                return s;
            }
        }
        return LIBRARY;
    }
}