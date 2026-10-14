package delivery.effect;

/** Result of an action-effect check. NO_EFFECT is never PASS evidence. */
public enum EffectOutcome {
    /** Cheap DOM/accessibility signature changed (URL, focus, page model, DOM digest or target state). */
    EFFECT_DOM,
    /** DOM signature identical, but the masked screenshot changed beyond the threshold (canvas, CSS-only). */
    EFFECT_VISUAL,
    /** Nothing observable changed within the settle window, after all retries. */
    NO_EFFECT,
    /** The check itself could not run (driver error); never PASS either. */
    UNKNOWN;

    /** Only an observed effect may count as action evidence. */
    public boolean isEvidence() {
        return this == EFFECT_DOM || this == EFFECT_VISUAL;
    }
}
