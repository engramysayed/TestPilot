package delivery.excel;

import delivery.excel.KeelPathCaseFilter.Surface;

import java.util.Optional;

/**
 * Blocks or warns when a workbook's KeelPath mix does not match the target surface
 * (Automate vs Execute).
 */
public final class KeelPathSurfaceGuard {

    private KeelPathSurfaceGuard() {
    }

    public static Optional<String> hardBlock(Surface surface, KeelPathCounts counts) {
        if (counts == null) {
            return Optional.of(defaultEmptyMessage(surface));
        }
        if (runnable(surface, counts) > 0) {
            return Optional.empty();
        }
        return Optional.of(defaultEmptyMessage(surface));
    }

    public static Optional<String> softWarn(Surface surface, KeelPathCounts counts) {
        if (counts == null || hardBlock(surface, counts).isPresent()) {
            return Optional.empty();
        }
        int tcCount = counts.tcCount();
        if (tcCount <= 0) {
            return Optional.empty();
        }
        int runnable = runnable(surface, counts);
        if (runnable * 2 >= tcCount) {
            return Optional.empty();
        }
        String surfaceLabel = surface == Surface.AUTOMATE ? "Automate" : "Execute";
        return Optional.of("Only " + runnable + " of " + tcCount
                + " test cases are eligible for " + surfaceLabel
                + ". Most rows are marked for the other surface or MANUAL. Continue anyway?");
    }

    private static int runnable(Surface surface, KeelPathCounts counts) {
        return switch (surface) {
            case AUTOMATE -> counts.automateRunnable();
            case EXECUTE -> counts.executeRunnable();
        };
    }

    private static String defaultEmptyMessage(Surface surface) {
        return switch (surface) {
            case AUTOMATE ->
                    "No AUTOMATE test cases in workbook (EXECUTE/VISION_ONLY/MANUAL rows are skipped on Automate)";
            case EXECUTE ->
                    "No runnable test cases in workbook (only MANUAL rows are skipped on Execute)";
        };
    }
}
