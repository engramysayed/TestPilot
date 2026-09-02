package delivery.job;

import delivery.portal.model.ProjectRecord;

import java.nio.file.Path;

public record GenerateBatchJobRequest(
        ProjectRecord project,
        Path storiesPath,
        Path outputDir,
        boolean reviewPass,
        boolean failFast,
        String model
) {
}
