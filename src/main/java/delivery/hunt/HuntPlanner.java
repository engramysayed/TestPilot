package delivery.hunt;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@FunctionalInterface
public interface HuntPlanner {
    HuntPlannerDecision plan(Context ctx) throws Exception;

    record Context(
            String briefMd,
            int cycleIndex,
            int cycleCeiling,
            int scenarioCap,
            int scenariosEmitted,
            int actionCapPerCycle,
            String slimDom,
            Path screenshotPath,
            List<Map<String, Object>> networkFailures,
            String stepsJournalMd,
            String plannerMode,
            String pageMapMd,
            boolean includeSlimDom,
            String coverageMd,
            String strategyHint,
            String domMode
    ) {
    }
}
