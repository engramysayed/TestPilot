package delivery.job;

import java.nio.file.Path;

public record GenerateBatchJobResult(
        int generatedTcCount,
        int failedStoryCount,
        Path outputCsv,
        String message
) {
}
