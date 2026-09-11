package delivery.authoring;

/** Per-job prove/heal engine: Keel (default) or Precision (Cursor groundRank). */
public enum AuthoringEngine {
    KEEL("keel"),
    PRECISION("precision");

    private final String wireValue;

    AuthoringEngine(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }

    public static AuthoringEngine parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return KEEL;
        }
        String normalized = raw.trim().toLowerCase();
        for (AuthoringEngine engine : values()) {
            if (engine.wireValue.equals(normalized)) {
                return engine;
            }
        }
        return KEEL;
    }
}
