package delivery.job;

import java.nio.file.Path;

public record ConversionJobRequest(
        String projectId,
        Path excel,
        String baseUrl,
        String username,
        String password,
        Path workDir,
        Path storeRoot,
        Path templateRoot,
        String mode,
        String localLlmBaseUrl,
        String localLlmModel,
        /** Portal "Client delivery — final revise" checkbox. */
        boolean finalRevise
) {
    /** Back-compat for callers without final-revise flag. */
    public ConversionJobRequest(
            String projectId,
            Path excel,
            String baseUrl,
            String username,
            String password,
            Path workDir,
            Path storeRoot,
            Path templateRoot,
            String mode,
            String localLlmBaseUrl,
            String localLlmModel
    ) {
        this(projectId, excel, baseUrl, username, password, workDir, storeRoot, templateRoot,
                mode, localLlmBaseUrl, localLlmModel, false);
    }
}
