package delivery.agent.validation.contract;

/** Approved requirements consumed by TC contract validation (generation and validation share this view). */
public record TestCaseValidationContext(
        String userStoryText,
        String baseUrl,
        String projectId) {

    public TestCaseValidationContext {
        userStoryText = userStoryText == null ? "" : userStoryText;
        baseUrl = baseUrl == null ? "" : baseUrl;
        projectId = projectId == null ? "" : projectId;
    }

    public static TestCaseValidationContext empty() {
        return new TestCaseValidationContext("", "", "");
    }
}