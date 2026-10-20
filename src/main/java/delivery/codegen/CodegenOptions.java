package delivery.codegen;

/**
 * Optional codegen naming settings (Ollama polish for test method names only).
 */
public record CodegenOptions(
        boolean ollamaNaming,
        String llmBaseUrl,
        String llmModel,
        /** Project Settings → Test data env (e.g. sit). Blank = no prefix on username/password/otp keys. */
        String testDataEnvPrefix
) {
    public static final CodegenOptions DEFAULT = new CodegenOptions(false, "", "", "");

    public CodegenOptions(boolean ollamaNaming, String llmBaseUrl, String llmModel) {
        this(ollamaNaming, llmBaseUrl, llmModel, "");
    }

    public CodegenOptions {
        llmBaseUrl = llmBaseUrl == null ? "" : llmBaseUrl.trim();
        llmModel = llmModel == null ? "" : llmModel.trim();
        testDataEnvPrefix = testDataEnvPrefix == null ? "" : testDataEnvPrefix.trim();
    }
}
