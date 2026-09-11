package delivery.hunt;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@FunctionalInterface
public interface HuntPlanner {
    HuntPlannerDecision plan(Context ctx) throws Exception;

    /** Optional end-of-hunt triage; blank = skip. */
    default String triageBugs(String triageUserPrompt) throws Exception {
        return "";
    }

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
            String domMode,
            String preferredHooksLine,
            boolean hasCredentials,
            String credentialUsername,
            boolean hasOtp,
            boolean loginFeature,
            int iterationIndex,
            int iterationCeiling,
            String iterationPlanMd,
            String priorIterationResultsMd
    ) {
        /** @deprecated use full constructor with iteration + hasOtp fields */
        public Context(
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
            this(briefMd, cycleIndex, cycleCeiling, scenarioCap, scenariosEmitted, actionCapPerCycle,
                    slimDom, screenshotPath, networkFailures, stepsJournalMd, plannerMode,
                    pageMapMd, includeSlimDom, coverageMd, strategyHint, domMode, "",
                    false, "", false, false, 1, 1, "", "");
        }

        public Context(
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
                String domMode,
                String preferredHooksLine
        ) {
            this(briefMd, cycleIndex, cycleCeiling, scenarioCap, scenariosEmitted, actionCapPerCycle,
                    slimDom, screenshotPath, networkFailures, stepsJournalMd, plannerMode,
                    pageMapMd, includeSlimDom, coverageMd, strategyHint, domMode, preferredHooksLine,
                    false, "", false, false, 1, 1, "", "");
        }

        /** Back-compat: credentialOtpHint string replaced by hasOtp flag. */
        public Context(
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
                String domMode,
                String preferredHooksLine,
                boolean hasCredentials,
                String credentialUsername,
                String credentialOtpHint,
                boolean loginFeature
        ) {
            this(briefMd, cycleIndex, cycleCeiling, scenarioCap, scenariosEmitted, actionCapPerCycle,
                    slimDom, screenshotPath, networkFailures, stepsJournalMd, plannerMode,
                    pageMapMd, includeSlimDom, coverageMd, strategyHint, domMode, preferredHooksLine,
                    hasCredentials,
                    credentialUsername,
                    credentialOtpHint != null && !credentialOtpHint.isBlank(),
                    loginFeature,
                    1, 1, "", "");
        }
    }
}
