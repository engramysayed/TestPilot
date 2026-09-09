package delivery.hunt;

import delivery.excel.ManualTestCase;
import delivery.job.JobCancelledException;
import delivery.job.JobProgressTracker;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * Dry-run hunter: one observe cycle with seeded finish decision, then pack ZIP.
 */
public final class DryRunHuntService {
    public HuntJobResult run(HuntRequest request,
                             List<ManualTestCase> selectedCases,
                             Path huntRoot,
                             JobProgressTracker tracker,
                             BooleanSupplier cancelCheck) throws Exception {
        request.normalize();
        if (tracker != null) {
            tracker.update(0, request.getCycleCeiling(), "Writing brief");
        }
        checkCancel(cancelCheck);

        String brief = HuntBriefBuilder.build(request, selectedCases);
        Files.createDirectories(huntRoot);
        Files.writeString(huntRoot.resolve("brief.md"), brief, StandardCharsets.UTF_8);

        Path cycleDir = huntRoot.resolve("cycles").resolve("cycle-01");
        Files.createDirectories(cycleDir);
        Files.writeString(cycleDir.resolve("dom-slim.txt"),
                "(dry-run) no live DOM captured", StandardCharsets.UTF_8);
        Files.writeString(cycleDir.resolve("network-failures.json"), "[]", StandardCharsets.UTF_8);
        Files.writeString(cycleDir.resolve("page-map.md"),
                "# Page map (dry-run)\n\n_no live DOM captured_\n", StandardCharsets.UTF_8);
        Files.writeString(cycleDir.resolve("page-map.json"), "{}", StandardCharsets.UTF_8);

        HuntCoverageMap coverage = new HuntCoverageMap(huntRoot);
        coverage.noteVisit("", "", "");

        if (tracker != null) {
            tracker.update(1, request.getCycleCeiling(), "Planner (dry-run)");
        }
        checkCancel(cancelCheck);

        HuntPlannerDecision decision = HuntPlannerDecision.finishDryRunSeed(request.getScenarioCap());
        Files.writeString(cycleDir.resolve("planner-response.json"),
                decision.rawJson(), StandardCharsets.UTF_8);
        Files.writeString(cycleDir.resolve("actions-log.json"), "[]", StandardCharsets.UTF_8);

        List<Map<String, Object>> bugs = new ArrayList<>(decision.bugs());
        List<Map<String, Object>> scenarios = new ArrayList<>();
        int remaining = request.getScenarioCap();
        for (Map<String, Object> sc : decision.scenarios()) {
            if (remaining <= 0) {
                break;
            }
            scenarios.add(sc);
            remaining--;
        }

        String stopReason = "FINISH";
        Path zip = HuntPackWriter.writePack(
                huntRoot, request, brief, stopReason, bugs, scenarios, 1, "unsupported");
        if (tracker != null) {
            tracker.update(request.getCycleCeiling(), request.getCycleCeiling(), "Hunter pack ready");
            tracker.setScores(bugs.size(), scenarios.size());
        }
        return new HuntJobResult(zip, bugs.size(), scenarios.size(),
                "Dry-run hunt finished — " + bugs.size() + " bug(s), " + scenarios.size() + " scenario(s)",
                stopReason,
                "unsupported");
    }

    private static void checkCancel(BooleanSupplier cancelCheck) {
        if (cancelCheck != null && cancelCheck.getAsBoolean()) {
            throw new JobCancelledException();
        }
    }
}
