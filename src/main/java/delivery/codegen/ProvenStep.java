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
        String screenshotRelPath
) {
    public ProvenStep {
        screenshotRelPath = screenshotRelPath == null ? "" : screenshotRelPath;
        rationale = rationale == null ? "" : rationale;
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
                assertionType, assertionExpected, validated, rationale, relPath == null ? "" : relPath);
    }

    public ProvenStep withPageName(String page) {
        return new ProvenStep(
                tcId, page, actionType, action, locatorStrategy, locatorValue, value,
                assertionType, assertionExpected, validated, rationale, screenshotRelPath);
    }
}
