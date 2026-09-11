package delivery.authoring;

import delivery.codegen.ProvenStep;
import delivery.heal.CandidateLiveness;
import delivery.heal.CandidateLivenessProbe;
import delivery.heal.CursorHealClient;
import delivery.heal.FailedLocator;
import delivery.heal.FreeInventHealer;
import delivery.heal.HealResult;
import utils.LogsManager;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Precision path: DOM shortlist → groundRank → bind, or one solve on low confidence. */
public class PrecisionBindService {
    private final AuthoringService authoring;
    private final CursorHealClient cursor;
    private final FreeInventHealer solveParser;
    private final PrecisionCallBudget budget;
    private final boolean precisionEnabled;

    public PrecisionBindService(
            AuthoringService authoring,
            CursorHealClient cursor,
            PrecisionCallBudget budget,
            boolean precisionEnabled
    ) {
        this.authoring = authoring;
        this.cursor = cursor == null ? new CursorHealClient() : cursor;
        this.solveParser = new FreeInventHealer(this.cursor, null, new LocatorValidator(), "cursor", true);
        this.budget = budget == null ? new PrecisionCallBudget(0) : budget;
        this.precisionEnabled = precisionEnabled;
    }

    public PrecisionBindOutcome bindIntent(
            String tcId,
            StepIntentBinder.IntentLine intent,
            String slimHtml,
            Path screenshotPathOrNull,
            List<String> priorSteps,
            List<ProvenStep> spent,
            List<FailedLocator> failed,
            CandidateLivenessProbe probe
    ) {
        Optional<PrecisionBindOutcome.FallbackKeel> preflight = preflightFailure();
        if (preflight.isPresent()) {
            return preflight.get();
        }
        if (slimHtml == null || slimHtml.isBlank()) {
            return new PrecisionBindOutcome.FallbackKeel("PROVIDER_ERROR");
        }
        List<DomCandidate> candidates = buildCandidates(intent, slimHtml, spent, failed, probe);
        List<DomCandidate> shortlist = buildShortlist(intent, candidates);
        if (shortlist.isEmpty()) {
            return new PrecisionBindOutcome.FallbackKeel("PROVIDER_ERROR");
        }
        if (!budget.tryConsume()) {
            return new PrecisionBindOutcome.FallbackKeel("CAP_EXCEEDED");
        }
        String table = DomCandidateExtractor.formatTable(shortlist);
        String htmlExcerpt = slimHtml.length() > 8000 ? slimHtml.substring(0, 8000) : slimHtml;
        GroundRankResult ranked = cursor.groundRankResult(
                intent.text(), table, htmlExcerpt, screenshotPathOrNull, priorSteps);
        if (ranked.isAcceptable()
                && shortlist.stream().anyMatch(c -> c.id().equalsIgnoreCase(ranked.candidateId()))) {
            List<ProvenStep> steps = authoring.stepsPreferringCandidate(
                    tcId, intent, candidates, ranked.candidateId(), false);
            if (validSteps(steps)) {
                LogsManager.info("PRECISION_GROUNDRANK: " + tcId + " candidateId=" + ranked.candidateId()
                        + " confidence=" + ranked.confidence());
                return new PrecisionBindOutcome.Bound(steps, "groundRank");
            }
        }
        LogsManager.info("PRECISION_GROUNDRANK: low confidence for " + intent.text()
                + " — escalating to solve");
        return new PrecisionBindOutcome.NeedsSolve(candidates, shortlist, table, htmlExcerpt);
    }

    public int callsUsed() {
        return budget.used();
    }

    public PrecisionCallBudget budget() {
        return budget;
    }

    public PrecisionBindOutcome solveOnce(
            String tcId,
            StepIntentBinder.IntentLine intent,
            List<DomCandidate> candidates,
            List<DomCandidate> shortlist,
            String table,
            String htmlExcerpt,
            Path screenshotPathOrNull,
            List<String> priorSteps
    ) {
        Optional<PrecisionBindOutcome.FallbackKeel> preflight = preflightFailure();
        if (preflight.isPresent()) {
            return preflight.get();
        }
        if (!budget.tryConsume()) {
            return new PrecisionBindOutcome.FallbackKeel("CAP_EXCEEDED");
        }
        String raw = cursor.solve(intent.text(), "Precision groundRank low confidence",
                table, htmlExcerpt, screenshotPathOrNull, priorSteps);
        String chosen = CursorHealClient.parseCandidateId(raw);
        if (!chosen.isBlank() && shortlist.stream().anyMatch(c -> c.id().equalsIgnoreCase(chosen))) {
            List<ProvenStep> steps = authoring.stepsPreferringCandidate(
                    tcId, intent, candidates, chosen, false);
            if (validSteps(steps)) {
                LogsManager.info("PRECISION_SOLVE: " + tcId + " candidateId=" + chosen);
                return new PrecisionBindOutcome.Bound(steps, "solve");
            }
        }
        Optional<HealResult> written = solveParser.parseInventResponse(
                tcId, intent, raw, htmlExcerpt, null, "");
        if (written.isPresent() && written.get().ok() && validSteps(written.get().steps())) {
            LogsManager.info("PRECISION_SOLVE: " + tcId + " written locator");
            return new PrecisionBindOutcome.Bound(written.get().steps(), "solve");
        }
        return new PrecisionBindOutcome.FallbackKeel("PROVIDER_ERROR");
    }

    private Optional<PrecisionBindOutcome.FallbackKeel> preflightFailure() {
        if (!precisionEnabled || !cursor.isEnabled()) {
            return Optional.of(new PrecisionBindOutcome.FallbackKeel("PROVIDER_UNAVAILABLE"));
        }
        if (!cursor.isRuntimeReady()) {
            return Optional.of(new PrecisionBindOutcome.FallbackKeel("PROVIDER_UNAVAILABLE"));
        }
        return Optional.empty();
    }

    private List<DomCandidate> buildCandidates(
            StepIntentBinder.IntentLine intent,
            String slimHtml,
            List<ProvenStep> spent,
            List<FailedLocator> failed,
            CandidateLivenessProbe probe
    ) {
        List<DomCandidate> candidates = DomCandidateExtractor.extract(slimHtml);
        if (StepIntentBinder.spendsMustAvoidPriorFills(intent.kind())) {
            candidates = StepIntentBinder.withoutSpentControls(candidates, spent);
        }
        candidates = StepIntentBinder.withoutFailedLocators(candidates, failed);
        return filterInteractable(candidates, probe);
    }

    private List<DomCandidate> buildShortlist(
            StepIntentBinder.IntentLine intent, List<DomCandidate> candidates) {
        List<DomCandidate> distinctive = StepIntentBinder.retainDistinctiveMatches(intent, candidates);
        if (distinctive.isEmpty()) {
            distinctive = candidates;
        }
        return authoring.shortlistForIntent(intent, distinctive, 12);
    }

    private static boolean validSteps(List<ProvenStep> steps) {
        return steps != null && !steps.isEmpty() && steps.stream().allMatch(ProvenStep::validated);
    }

    static List<DomCandidate> filterInteractable(
            List<DomCandidate> candidates, CandidateLivenessProbe probe) {
        if (probe == null || candidates == null || candidates.isEmpty()) {
            return candidates == null ? List.of() : candidates;
        }
        List<DomCandidate> out = new ArrayList<>();
        for (DomCandidate c : candidates) {
            CandidateLiveness live = probe.probe(c);
            if (live != null && live.interactable()) {
                out.add(c);
            }
        }
        return out;
    }
}
