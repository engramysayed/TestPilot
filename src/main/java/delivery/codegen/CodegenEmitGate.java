package delivery.codegen;

import delivery.job.TcOutcome;
import delivery.job.TcStatus;

import java.util.ArrayList;
import java.util.List;

/**
 * Fail-closed gate before any customer ZIP bytes are produced: smell-check pages
 * (collapsed names, duplicate methods/locators). Dump / blank {@code Page} stems are
 * demoted per-TC to {@link TcStatus#TODO} (0064) rather than aborting the whole Automate
 * job — dump {@code Page} classes are never emitted. {@link CodeWriter#write} demotes
 * then verifies; {@link delivery.job.EmitPhase} also compiles the project before packaging.
 */
public final class CodegenEmitGate {
    /** Reason stamped on TCs demoted because clustering could not name a real page stem. */
    public static final String DUMP_STEM_DEMOTE_REASON =
            "CODEGEN: dump/blank page stem — demoted to TODO (cannot emit Page class)";

    private CodegenEmitGate() {
    }

    /**
     * Demote any TC that would contribute a dump/blank {@code Page} stem to TODO and
     * clear its steps so page accumulate never emits a dump class. Other TCs unchanged.
     */
    public static List<TcOutcome> demoteDumpPageStems(List<TcOutcome> outcomes) {
        if (outcomes == null || outcomes.isEmpty()) {
            return outcomes == null ? List.of() : outcomes;
        }
        List<TcOutcome> out = new ArrayList<>(outcomes.size());
        for (TcOutcome outcome : outcomes) {
            if (contributesDumpStem(outcome)) {
                out.add(demoteDump(outcome));
            } else {
                out.add(outcome);
            }
        }
        return List.copyOf(out);
    }

    /**
     * Accumulate + verify; throws {@link IllegalStateException} on smells.
     * Call {@link #demoteDumpPageStems} or {@link #prepare} first so dump stems become TODO
     * rather than aborting the whole job.
     */
    public static PageAccumulator verifyOrThrow(List<TcOutcome> outcomes) {
        PageAccumulator pages = CodeWriter.accumulate(outcomes);
        CodegenSmellCheck.verify(pages.pages());
        return pages;
    }

    /**
     * Demote dump/blank Page stems to TODO, then verify remaining smells.
     * Returns demoted outcomes + accumulated pages for emit / score / ZIP.
     */
    public static Prepared prepare(List<TcOutcome> outcomes) {
        List<TcOutcome> prepared = demoteDumpPageStems(outcomes);
        return new Prepared(prepared, verifyOrThrow(prepared));
    }

    public record Prepared(List<TcOutcome> outcomes, PageAccumulator pages) {
    }

    static boolean contributesDumpStem(TcOutcome outcome) {
        if (outcome == null) {
            return false;
        }
        return hasDumpStep(outcome.provenSteps())
                || hasDumpStep(outcome.loginSteps())
                || hasDumpStep(outcome.setupSteps());
    }

    private static boolean hasDumpStep(List<ProvenStep> steps) {
        if (steps == null || steps.isEmpty()) {
            return false;
        }
        for (ProvenStep step : steps) {
            if (step != null && CodegenSmellCheck.isDumpPageName(step.pageName())) {
                return true;
            }
        }
        return false;
    }

    private static TcOutcome demoteDump(TcOutcome outcome) {
        String prior = outcome.failureReason() == null ? "" : outcome.failureReason().trim();
        String reason = DUMP_STEM_DEMOTE_REASON;
        if (!prior.isEmpty() && !prior.startsWith("CODEGEN: dump/blank page stem")) {
            reason = reason + "; prior: " + prior;
        }
        // Clear steps so accumulate cannot rebuild a dump Page_* class; TODO test still emits.
        return new TcOutcome(
                outcome.tcId(),
                outcome.title(),
                TcStatus.TODO,
                List.of(),
                reason,
                outcome.evidenceDir(),
                false,
                List.of(),
                List.of());
    }
}
