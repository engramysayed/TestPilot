package delivery.hunt;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Shared result for dry-run and live Bug Hunter jobs. */
public record HuntJobResult(
        Path zipPath,
        int bugCount,
        int scenarioCount,
        String message,
        String stopReason,
        String networkCapture
) {
    public HuntJobResult(Path zipPath, int bugCount, int scenarioCount, String message, String stopReason) {
        this(zipPath, bugCount, scenarioCount, message, stopReason, "best-effort");
    }
}
