package delivery.excel;

public record ManualTestCase(
        String tcId,
        String title,
        String preconditions,
        String steps,
        String expectedResult,
        String priority,
        String tags,
        String visualAssertion,
        String testData,
        String keelPath,
        String callBefore
) {
    public ManualTestCase {
        visualAssertion = visualAssertion == null ? "" : visualAssertion;
        testData = testData == null ? "" : testData;
        keelPath = keelPath == null ? "" : keelPath;
        callBefore = callBefore == null ? "" : callBefore.trim();
    }

    public ManualTestCase(
            String tcId,
            String title,
            String preconditions,
            String steps,
            String expectedResult,
            String priority,
            String tags) {
        this(tcId, title, preconditions, steps, expectedResult, priority, tags, "", "", "", "");
    }

    public ManualTestCase(
            String tcId,
            String title,
            String preconditions,
            String steps,
            String expectedResult,
            String priority,
            String tags,
            String visualAssertion) {
        this(tcId, title, preconditions, steps, expectedResult, priority, tags, visualAssertion, "", "", "");
    }

    public ManualTestCase(
            String tcId,
            String title,
            String preconditions,
            String steps,
            String expectedResult,
            String priority,
            String tags,
            String visualAssertion,
            String testData) {
        this(tcId, title, preconditions, steps, expectedResult, priority, tags, visualAssertion, testData, "", "");
    }

    public ManualTestCase(
            String tcId,
            String title,
            String preconditions,
            String steps,
            String expectedResult,
            String priority,
            String tags,
            String visualAssertion,
            String testData,
            String keelPath) {
        this(tcId, title, preconditions, steps, expectedResult, priority, tags, visualAssertion, testData, keelPath, "");
    }

    public String contentHash() {
        String normalized = normalize(steps) + "|" + normalize(expectedResult)
                + "|" + normalize(visualAssertion) + "|" + normalize(testData);
        return Integer.toHexString(normalized.hashCode());
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }
}
