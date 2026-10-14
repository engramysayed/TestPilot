package delivery.mobile.runner.grammar;

public record LocatorOverride(
        String tcId,
        String stepId,
        String context,
        String strategy,
        String value
) {
    public LocatorOverride {
        if (tcId == null || tcId.isBlank()) throw new GrammarException("OVERRIDE_TC", "TC_ID required");
        if (stepId == null || stepId.isBlank()) throw new GrammarException("OVERRIDE_STEP", "Step required");
        if (strategy == null || strategy.isBlank()) throw new GrammarException("OVERRIDE_STRATEGY", "Strategy required");
        if (value == null) throw new GrammarException("OVERRIDE_VALUE", "Value required");
        context = context == null || context.isBlank() ? "NATIVE" : context;
        tcId = tcId.trim();
        stepId = stepId.trim();
        strategy = strategy.trim();
    }

    public String key() {
        return tcId + "\0" + stepId;
    }
}
