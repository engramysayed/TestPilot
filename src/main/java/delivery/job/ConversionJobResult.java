package delivery.job;

import java.nio.file.Path;

public record ConversionJobResult(
        Path zipFile,
        int passed,
        int todo,
        Path scoreReport,
        String message,
        /** COMPLETED or COMPLETED_WITH_BLOCK (soft block). */
        String jobStatus,
        /** SHIP | SHIP_WITH_REVIEW | BLOCK | SKIPPED */
        String reviseVerdict
) {
    public ConversionJobResult(Path zipFile, int passed, int todo, Path scoreReport, String message) {
        this(zipFile, passed, todo, scoreReport, message, "COMPLETED", "SKIPPED");
    }

    public boolean softBlocked() {
        return "COMPLETED_WITH_BLOCK".equals(jobStatus) || "BLOCK".equalsIgnoreCase(reviseVerdict);
    }
}
