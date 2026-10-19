package delivery.codegen;

public record ProvenStep(
        String tcId,
        String pageName,
        String actionType,
        String action,
        String locatorStrategy,
        String locatorValue,
        String value,
        String assertionType,
        String assertionExpected,
        boolean validated,
        String rationale,
        String screenshotRelPath,
        java.util.List<LocatorCandidate> locatorCandidates,
        java.util.List<LocatorCandidate> dropCandidates,
        String sideEffects,
        /** IR "secret" (r2): password / masked / OTP target, or a value from a secret or credential. */
        boolean secret
) {
    /** IR {@code sideEffects} values (prove → codegen). Absent in IR == {@link #SIDE_EFFECTS_UNKNOWN}. */
    public static final String SIDE_EFFECTS_NONE = "none";
    public static final String SIDE_EFFECTS_MUTATING = "mutating";
    public static final String SIDE_EFFECTS_UNKNOWN = "unknown";
    public static final java.util.Set<String> SIDE_EFFECTS_VALUES =
            java.util.Set.of(SIDE_EFFECTS_NONE, SIDE_EFFECTS_MUTATING, SIDE_EFFECTS_UNKNOWN);

    /** r2 engine shape: legacy fields + secret (no candidates, sideEffects unknown). */
    public ProvenStep(
            String tcId, String pageName, String actionType, String action, String locatorStrategy,
            String locatorValue, String value, String assertionType, String assertionExpected,
            boolean validated, String rationale, String screenshotRelPath, boolean secret) {
        this(tcId, pageName, actionType, action, locatorStrategy, locatorValue, value, assertionType,
                assertionExpected, validated, rationale, screenshotRelPath, java.util.List.of(), java.util.List.of(),
                SIDE_EFFECTS_UNKNOWN, secret);
    }

    /** Codegen shape without the secret flag (false). */
    public ProvenStep(
            String tcId, String pageName, String actionType, String action, String locatorStrategy,
            String locatorValue, String value, String assertionType, String assertionExpected,
            boolean validated, String rationale, String screenshotRelPath,
            java.util.List<LocatorCandidate> locatorCandidates, java.util.List<LocatorCandidate> dropCandidates,
            String sideEffects) {
        this(tcId, pageName, actionType, action, locatorStrategy, locatorValue, value, assertionType,
                assertionExpected, validated, rationale, screenshotRelPath, locatorCandidates, dropCandidates,
                sideEffects, false);
    }

    public ProvenStep {
        sideEffects = sideEffects == null || sideEffects.isBlank() ? SIDE_EFFECTS_UNKNOWN : sideEffects;
        if (!SIDE_EFFECTS_VALUES.contains(sideEffects)) {
            throw new IllegalArgumentException("sideEffects must be one of none|mutating|unknown, got '" + sideEffects + "'");
        }
        screenshotRelPath = screenshotRelPath == null ? "" : screenshotRelPath;
        rationale = rationale == null ? "" : rationale;
        locatorCandidates = locatorCandidates == null ? java.util.List.of() : java.util.List.copyOf(locatorCandidates);
        dropCandidates = dropCandidates == null ? java.util.List.of() : java.util.List.copyOf(dropCandidates);
    }

    /** Without a sideEffects marker (unknown). */
    public ProvenStep(
            String tcId,
            String pageName,
            String actionType,
            String action,
            String locatorStrategy,
            String locatorValue,
            String value,
            String assertionType,
            String assertionExpected,
            boolean validated,
            String rationale,
            String screenshotRelPath,
            java.util.List<LocatorCandidate> locatorCandidates,
            java.util.List<LocatorCandidate> dropCandidates
    ) {
        this(tcId, pageName, actionType, action, locatorStrategy, locatorValue, value,
                assertionType, assertionExpected, validated, rationale, screenshotRelPath, locatorCandidates,
                dropCandidates, SIDE_EFFECTS_UNKNOWN);
    }

    public ProvenStep withSideEffects(String marker) {
        return new ProvenStep(
                tcId, pageName, actionType, action, locatorStrategy, locatorValue, value,
                assertionType, assertionExpected, validated, rationale, screenshotRelPath, locatorCandidates,
                dropCandidates, marker, secret);
    }

    /** True when prove declared that re-running this step cannot change application state. */
    public boolean hasNoSideEffects() {
        return SIDE_EFFECTS_NONE.equals(sideEffects);
    }

    /** Without a drag drop target. */
    public ProvenStep(
            String tcId,
            String pageName,
            String actionType,
            String action,
            String locatorStrategy,
            String locatorValue,
            String value,
            String assertionType,
            String assertionExpected,
            boolean validated,
            String rationale,
            String screenshotRelPath,
            java.util.List<LocatorCandidate> locatorCandidates
    ) {
        this(tcId, pageName, actionType, action, locatorStrategy, locatorValue, value,
                assertionType, assertionExpected, validated, rationale, screenshotRelPath, locatorCandidates,
                java.util.List.of());
    }

    /** Drag step (contract v2 proposal): drop target main first, then its fallbacks. */
    public ProvenStep withDropCandidates(java.util.List<LocatorCandidate> drop) {
        return new ProvenStep(
                tcId, pageName, actionType, action, locatorStrategy, locatorValue, value,
                assertionType, assertionExpected, validated, rationale, screenshotRelPath, locatorCandidates, drop, sideEffects, secret);
    }

    public boolean isDrag() {
        return "drag".equalsIgnoreCase(action == null ? "" : action.trim());
    }

    /** Pre-candidate-model shape: no ranked candidates (plain single locator). */
    public ProvenStep(
            String tcId,
            String pageName,
            String actionType,
            String action,
            String locatorStrategy,
            String locatorValue,
            String value,
            String assertionType,
            String assertionExpected,
            boolean validated,
            String rationale,
            String screenshotRelPath
    ) {
        this(tcId, pageName, actionType, action, locatorStrategy, locatorValue, value,
                assertionType, assertionExpected, validated, rationale, screenshotRelPath, java.util.List.of());
    }

    public ProvenStep withLocatorCandidates(java.util.List<LocatorCandidate> candidates) {
        return new ProvenStep(
                tcId, pageName, actionType, action, locatorStrategy, locatorValue, value,
                assertionType, assertionExpected, validated, rationale, screenshotRelPath, candidates, dropCandidates, sideEffects, secret);
    }

    public ProvenStep withLocator(String strategy, String value) {
        return new ProvenStep(
                tcId, pageName, actionType, action, strategy, value, value(),
                assertionType, assertionExpected, validated, rationale, screenshotRelPath,
                locatorCandidates, dropCandidates, sideEffects, secret);
    }

    /** Convenience ctor used by binder/codegen (no screenshot yet). */
    public ProvenStep(
            String tcId,
            String pageName,
            String actionType,
            String action,
            String locatorStrategy,
            String locatorValue,
            String value,
            String assertionType,
            String assertionExpected,
            boolean validated,
            String rationale
    ) {
        this(tcId, pageName, actionType, action, locatorStrategy, locatorValue, value,
                assertionType, assertionExpected, validated, rationale, "");
    }

    public ProvenStep withScreenshot(String relPath) {
        return new ProvenStep(
                tcId, pageName, actionType, action, locatorStrategy, locatorValue, value,
                assertionType, assertionExpected, validated, rationale, relPath == null ? "" : relPath,
                locatorCandidates, dropCandidates, sideEffects, secret);
    }

    public ProvenStep withPageName(String page) {
        return new ProvenStep(
                tcId, page, actionType, action, locatorStrategy, locatorValue, value,
                assertionType, assertionExpected, validated, rationale, screenshotRelPath, locatorCandidates, dropCandidates, sideEffects, secret);
    }

    public ProvenStep withSecret(boolean s) {
        return new ProvenStep(
                tcId, pageName, actionType, action, locatorStrategy, locatorValue, value,
                assertionType, assertionExpected, validated, rationale, screenshotRelPath, locatorCandidates, dropCandidates,
                sideEffects, s);
    }

    /** Logs print steps via toString: a secret value is never rendered. */
    @Override
    public String toString() {
        return "ProvenStep[tcId=" + tcId + ", pageName=" + pageName + ", actionType=" + actionType + ", action=" + action
                + ", locatorStrategy=" + locatorStrategy + ", locatorValue=" + locatorValue
                + ", value=" + (secret ? delivery.ir.SecretStep.MASK : value) + ", assertionType=" + assertionType
                + ", assertionExpected=" + assertionExpected + ", validated=" + validated + ", rationale=" + rationale
                + ", screenshotRelPath=" + screenshotRelPath + ", locatorCandidates=" + locatorCandidates
                + ", dropCandidates=" + dropCandidates + ", sideEffects=" + sideEffects + ", secret=" + secret + "]";
    }
}
