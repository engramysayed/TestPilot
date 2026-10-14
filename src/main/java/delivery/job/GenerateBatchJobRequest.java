package delivery.job;

import java.nio.file.Path;

public record GenerateBatchJobRequest(
        String projectId,
        Path storiesPath,
        Path outputDir,
        boolean reviewPass,
        boolean failFast,
        String model
) {
}
