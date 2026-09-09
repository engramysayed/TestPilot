package delivery.codegen;

/**
 * Optional codegen naming settings (Ollama polish for test method names only).
 */
public record CodegenOptions(
        boolean ollamaNaming,
        String llmBaseUrl,
        String llmModel
) {
    public static final CodegenOptions DEFAULT = new CodegenOptions(false, "", "");

    public CodegenOptions {
        llmBaseUrl = llmBaseUrl == null ? "" : llmBaseUrl.trim();
        llmModel = llmModel == null ? "" : llmModel.trim();
    }
}
