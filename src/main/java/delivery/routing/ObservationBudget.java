package delivery.routing;

/**
 * Apply A11 {@link ModelRouter#observationChars} to slim-HTML / page excerpts
 * for heal, hunt, and ProvePhase authoring (AE-D9).
 */
public final class ObservationBudget {
    private ObservationBudget() {}

    public static int forIntent(ModelRouter.Intent intent) {
        try {
            return ModelRouter.observationChars(ModelRouter.builtin().route(intent));
        } catch (RuntimeException e) {
            return switch (intent == null ? ModelRouter.Intent.BIND : intent) {
                case HEAL -> 16_000;
                case HUNT_PLANNING -> 24_000;
                case BIND, GROUNDING, PRECISION -> 24_000;
                case PLANNING, GENERATE -> 16_000;
                default -> 8_000;
            };
        }
    }

    /** ProvePhase / Automate authoring HTML budget (A11 bind intent). */
    public static int forProve() {
        return forIntent(ModelRouter.Intent.BIND);
    }

    public static String clip(String text, int maxChars) {
        if (text == null) return "";
        if (maxChars <= 0 || text.length() <= maxChars) return text;
        return text.substring(0, maxChars);
    }

    public static String clipFor(ModelRouter.Intent intent, String text) {
        return clip(text, forIntent(intent));
    }
}
