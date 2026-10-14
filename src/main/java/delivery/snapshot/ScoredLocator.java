package delivery.snapshot;

/** One verified locator: strategy is always "keel" (KeelTarget string form), score in 0..1. */
public record ScoredLocator(String strategy, String value, double score, String tier) {
    public static final String KEEL = "keel";

    public static ScoredLocator keel(KeelTarget target, double score, String tier) {
        return new ScoredLocator(KEEL, target.format(), score, tier);
    }

    public KeelTarget target() {
        return KeelTarget.parse(value);
    }

    org.json.JSONObject toJson() {
        return new org.json.JSONObject().put("strategy", strategy).put("value", value).put("score", score);
    }
}
