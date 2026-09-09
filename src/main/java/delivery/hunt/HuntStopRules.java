package delivery.hunt;

import java.util.Optional;

/** Stop-reason rules evaluated after each hunt cycle's actions are recorded. */
public final class HuntStopRules {
    private HuntStopRules() {
    }

    /**
     * Returns {@code STUCK} when coverage reports a hard fail streak; empty otherwise.
     * FINISH and other stop reasons remain the caller's responsibility.
     */
    public static Optional<String> afterActions(HuntCoverageMap coverage,
                                                HuntPlannerDecision.Decision decision) throws Exception {
        return afterActions(coverage, decision, false, null);
    }

    /**
     * When strategies are enabled and coverage would stop as stuck, advance the sequencer
     * (unless already on the last mode) and clear fail streaks instead of stopping.
     */
    public static Optional<String> afterActions(HuntCoverageMap coverage,
                                                HuntPlannerDecision.Decision decision,
                                                boolean strategiesEnabled,
                                                HuntStrategySequencer sequencer) throws Exception {
        if (coverage == null || !coverage.shouldStopStuck()) {
            return Optional.empty();
        }
        if (strategiesEnabled && sequencer != null && !sequencer.isLast()) {
            String prev = sequencer.current().mode();
            sequencer.advance();
            coverage.markStrategyDone(prev);
            coverage.clearFailStreaks();
            return Optional.empty();
        }
        return Optional.of("STUCK");
    }

    /**
     * Returns {@code COMPLETE} when strategies are exhausted, invent budget is met, and at least one bug exists.
     */
    public static Optional<String> completeStop(boolean strategiesEnabled,
                                                HuntStrategySequencer sequencer,
                                                int scenariosEmitted,
                                                int scenarioCap,
                                                int bugCount) {
        if (!strategiesEnabled || sequencer == null) {
            return Optional.empty();
        }
        if (sequencer.finishedAll() && scenariosEmitted >= scenarioCap && bugCount > 0) {
            return Optional.of("COMPLETE");
        }
        return Optional.empty();
    }

    /** Planner {@code finish} stop reason — upgraded to {@code COMPLETE} when playbook conditions are met. */
    public static String resolveFinishStopReason(boolean strategiesEnabled,
                                                 HuntStrategySequencer sequencer,
                                                 int scenariosEmitted,
                                                 int scenarioCap,
                                                 int bugCount) {
        return completeStop(strategiesEnabled, sequencer, scenariosEmitted, scenarioCap, bugCount)
                .orElse("FINISH");
    }
}
