package delivery.portal.model;

public enum KeelPath {
    AUTOMATE,
    EXECUTE,
    VISION_ONLY,
    MANUAL;

    public static KeelPath parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return EXECUTE;
        }
        String normalized = raw.trim().toUpperCase().replace('-', '_').replace(' ', '_');
        return switch (normalized) {
            case "AUTOMATE", "AUTO", "CONVERT" -> AUTOMATE;
            case "EXECUTE", "RUN" -> EXECUTE;
            case "VISION_ONLY", "VISION", "VLM" -> VISION_ONLY;
            case "MANUAL", "NONE", "HUMAN" -> MANUAL;
            default -> throw new IllegalArgumentException("Unknown KeelPath: " + raw);
        };
    }
}
