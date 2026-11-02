package delivery.evidence;

import java.nio.file.Path;

/**
 * Disk paths for Phase 2 failure replay under an existing TC evidence folder.
 * Layout: {@code {tcEvidenceDir}/failure/{stepIndex}/}.
 */
public final class FailureReplayPaths {
    public static final String META = "meta.json";
    public static final String SCREEN = "screen.png";
    public static final String NETWORK = "network.json";
    public static final String CONSOLE = "console.json";

    private FailureReplayPaths() {
    }

    public static Path failureDir(Path tcEvidenceDir, int stepIndex) {
        if (tcEvidenceDir == null) {
            throw new IllegalArgumentException("tcEvidenceDir required");
        }
        if (stepIndex < 0) {
            stepIndex = 0;
        }
        return tcEvidenceDir.resolve("failure").resolve(Integer.toString(stepIndex));
    }

    public static Path meta(Path failureDir) {
        return failureDir.resolve(META);
    }

    public static Path screen(Path failureDir) {
        return failureDir.resolve(SCREEN);
    }

    public static Path network(Path failureDir) {
        return failureDir.resolve(NETWORK);
    }

    public static Path console(Path failureDir) {
        return failureDir.resolve(CONSOLE);
    }
}
