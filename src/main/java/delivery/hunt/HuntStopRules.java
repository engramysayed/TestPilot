package delivery.hunt;

import java.util.List;
import java.util.Optional;

/** Stop-reason rules evaluated after each hunt cycle's actions are recorded. */
public final class HuntStopRules {
    private HuntStopRules() {
    }

    /**
     * Outcome of a repeated-failure recovery. A hunt never stops for being stuck: the failing
     * locators are blocked and (when strategies are on) the next strategy starts.
     */
    public record Recovery(List<String> blockedLocators, String advancedFrom, String advancedTo) {
        public static Recovery none() {
            return new Recovery(List.of(), "", "");
        }

        public boolean triggered() {
            return !blockedLocators.isEmpty() || !advancedFrom.isBlank();
        }
    }

    /**
     * Called after each cycle's actions. When coverage reports a hard fail streak, the stuck
     * locators are blocked (surfaced to the planner as "do not retry") and the strategy
     * sequencer advances when it can, so remaining cycles keep hunting.
     */
    public static Recovery recoverFromStuck(HuntCoverageMap coverage,
                                            boolean strategiesEnabled,
                                            HuntStrategySequencer sequencer) throws Exception {
        if (coverage == null || !coverage.shouldStopStuck()) {
            return Recovery.none();
        }
        List<String> blocked = coverage.markBlockedFromStreaks();
        String from = "";
        String to = "";
        if (strategiesEnabled && sequencer != null && !sequencer.isLast()) {
            from = sequencer.current().mode();
            sequencer.advance();
            coverage.markStrategyDone(from);
            to = sequencer.current().mode();
        }
        coverage.clearFailStreaks();
        return new Recovery(blocked, from, to);
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
