package delivery.authoring;

import delivery.codegen.ProvenStep;
import delivery.heal.CandidateLiveness;
import delivery.heal.CandidateLivenessProbe;
import delivery.heal.CursorHealClient;
import delivery.heal.FailedLocator;
import delivery.heal.FreeInventHealer;
import utils.LogsManager;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Precision path: DOM shortlist → groundRank → bind, or one solve on low confidence. */
public class PrecisionBindService {

    /** Max Excel intents per one Cursor groundRankBatch call (same page). */
    public static final int MAX_INTENTS_PER_GROUNDRANK_BATCH = 5;
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
            return new PrecisionBindOutcome.NeedsSolve(candidates, shortlist, "",
                    slimHtml.substring(0, Math.min(8000, slimHtml.length())));
        }
        if (!budget.tryConsume()) {
            return new PrecisionBindOutcome.FallbackKeel("CAP_EXCEEDED");
        }
        String table = DomCandidateExtractor.formatTable(shortlist);
        String htmlExcerpt = slimHtml.length() > 8000 ? slimHtml.substring(0, 8000) : slimHtml;
        GroundRankResult ranked;
        try {
            ranked = cursor.groundRankResult(intent.text(), table, htmlExcerpt, screenshotPathOrNull, priorSteps);
        } catch (RuntimeException unavailable) {
            return new PrecisionBindOutcome.FallbackKeel("PROVIDER_ERROR");
        }
        if (ranked == null) return new PrecisionBindOutcome.FallbackKeel("PROVIDER_ERROR");
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

    /**
     * One multimodal Cursor call to rank locators for several intents on the current page.
     * Returns consecutive picks from intent index 0, or {@link PrecisionBatchBindOutcome.FallbackKeel}.
     */
    public PrecisionBatchBindOutcome bindIntentBatch(
            String tcId,
            List<StepIntentBinder.IntentLine> intents,
            String slimHtml,
            Path screenshotPathOrNull,
            List<String> priorSteps,
            List<ProvenStep> spent,
            List<FailedLocator> failed,
            CandidateLivenessProbe probe
    ) {
        if (intents == null || intents.size() < 2 || intents.size() > MAX_INTENTS_PER_GROUNDRANK_BATCH) {
            return new PrecisionBatchBindOutcome.FallbackKeel("BATCH_TOO_SMALL");
        }
        Optional<PrecisionBindOutcome.FallbackKeel> preflight = preflightFailure();
        if (preflight.isPresent()) {
            return new PrecisionBatchBindOutcome.FallbackKeel(preflight.get().reason());
        }
        if (slimHtml == null || slimHtml.isBlank()) {
            return new PrecisionBatchBindOutcome.FallbackKeel("PROVIDER_ERROR");
        }
        long preparationStarted = System.nanoTime();
        // A batch describes one unchanged snapshot: extract and probe each selector once.
        List<DomCandidate> snapshotCandidates = DomCandidateExtractor.extract(slimHtml);
        java.util.Map<List<String>, CandidateLiveness> liveBySelector = new java.util.HashMap<>();
        CandidateLivenessProbe snapshotProbe = probe == null ? null : candidate ->
                liveBySelector.computeIfAbsent(List.of(candidate.strategy(), candidate.value()),
                        key -> {
                            CandidateLiveness live = probe.probe(candidate);
                            return live == null ? CandidateLiveness.dead() : live;
                        });
        List<IntentShortlistPack> packs = new ArrayList<>();
        for (int i = 0; i < intents.size(); i++) {
            StepIntentBinder.IntentLine intent = intents.get(i);
            // A later button can be disabled until the earlier fields have been entered.
            CandidateLivenessProbe planningProbe = i == 0 || snapshotProbe == null ? snapshotProbe : candidate -> {
                CandidateLiveness live = snapshotProbe.probe(candidate);
                return new CandidateLiveness(live.displayed(), true, live.width(), live.height());
            };
            List<DomCandidate> candidates = buildCandidates(intent, snapshotCandidates, spent, failed, planningProbe);
            List<DomCandidate> shortlist = buildShortlist(intent, candidates);
            if (shortlist.isEmpty()) {
                break; // Later controls may appear only after an earlier action.
            }
            packs.add(new IntentShortlistPack(i, intent.text(), shortlist, candidates));
        }
        if (packs.size() < 2) return new PrecisionBatchBindOutcome.FallbackKeel("EMPTY_SHORTLIST");
        if (!budget.tryConsume()) {
            return new PrecisionBatchBindOutcome.FallbackKeel("CAP_EXCEEDED");
        }
        LogsManager.info("PERF precision_prepare intents=" + intents.size()
                + " candidates=" + snapshotCandidates.size() + " liveProbes=" + liveBySelector.size()
                + " elapsedMs=" + java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - preparationStarted));
        String intentsBlock = formatBatchIntentsBlock(packs);
        String htmlExcerpt = slimHtml.length() > 8000 ? slimHtml.substring(0, 8000) : slimHtml;
        GroundRankBatchResult ranked;
        try {
            ranked = cursor.groundRankBatchResult(intentsBlock, htmlExcerpt, screenshotPathOrNull, priorSteps);
        } catch (RuntimeException unavailable) {
            return new PrecisionBatchBindOutcome.FallbackKeel("PROVIDER_ERROR");
        }
        if (ranked != null && (ranked.picks().stream().anyMatch(p -> p.intentIndex() < 0 || p.intentIndex() >= packs.size())
                || ranked.picks().stream().map(GroundRankBatchResult.Pick::intentIndex).distinct().count() != ranked.picks().size())) {
            return new PrecisionBatchBindOutcome.FallbackKeel("INVALID_BATCH_RESPONSE");
        }
        List<GroundRankBatchResult.Pick> picks = ranked == null
                ? List.of()
                : ranked.consecutiveAcceptableFromZero();
        if (picks.isEmpty()) {
            LogsManager.info("PRECISION_GROUNDRANK_BATCH: no acceptable consecutive picks for " + tcId
                    + " (intents=" + intents.size() + ", cursorPicks="
                    + (ranked == null ? 0 : ranked.picks().size()) + ")");
            return new PrecisionBatchBindOutcome.FallbackKeel("LOW_CONFIDENCE");
        }
        List<PrecisionBatchBindOutcome.IntentSteps> bound = new ArrayList<>();
        List<ProvenStep> spentSoFar = spent == null ? new ArrayList<>() : new ArrayList<>(spent);
        for (GroundRankBatchResult.Pick pick : picks) {
            IntentShortlistPack pack = packs.get(pick.intentIndex());
            StepIntentBinder.IntentLine intent = intents.get(pick.intentIndex());
            if (pack.shortlist().stream().noneMatch(c -> c.id().equalsIgnoreCase(pick.candidateId()))) break;
            List<DomCandidate> available = StepIntentBinder.spendsMustAvoidPriorFills(intent)
                    ? StepIntentBinder.withoutSpentControls(pack.candidates(), spentSoFar) : pack.candidates();
            if (available.stream().noneMatch(c -> c.id().equalsIgnoreCase(pick.candidateId()))) break;
            List<ProvenStep> steps = authoring.stepsPreferringCandidate(
                    tcId, intent, available, pick.candidateId(), false);
            if (!validSteps(steps)) {
                break;
            }
            bound.add(new PrecisionBatchBindOutcome.IntentSteps(pick.intentIndex(), steps));
            spentSoFar.addAll(steps);
        }
        if (bound.isEmpty()) {
            return new PrecisionBatchBindOutcome.FallbackKeel("BIND_FAILED");
        }
        LogsManager.info("PRECISION_GROUNDRANK_BATCH: " + tcId + " picks=" + bound.size()
                + " of " + intents.size() + " intents");
        return new PrecisionBatchBindOutcome.BatchBound(bound, "groundRankBatch");
    }

    private record IntentShortlistPack(
            int index,
            String intentText,
            List<DomCandidate> shortlist,
            List<DomCandidate> candidates
    ) {
    }

    static String formatBatchIntentsBlock(List<IntentShortlistPack> packs) {
        StringBuilder sb = new StringBuilder();
        for (IntentShortlistPack pack : packs) {
            sb.append("Intent ").append(pack.index()).append(": ").append(pack.intentText()).append('\n');
            sb.append(DomCandidateExtractor.formatTable(pack.shortlist())).append('\n');
        }
        return sb.toString();
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
        String raw;
        try {
            raw = cursor.solve(intent.text(), "Precision groundRank low confidence",
                    table, htmlExcerpt, screenshotPathOrNull, priorSteps);
        } catch (RuntimeException unavailable) {
            return new PrecisionBindOutcome.FallbackKeel("PROVIDER_ERROR");
        }
        String chosen = CursorHealClient.parseCandidateId(raw);
        if (!chosen.isBlank() && shortlist.stream().anyMatch(c -> c.id().equalsIgnoreCase(chosen))) {
            List<ProvenStep> steps = authoring.stepsPreferringCandidate(
                    tcId, intent, candidates, chosen, false);
            if (validSteps(steps)) {
                LogsManager.info("PRECISION_SOLVE: " + tcId + " candidateId=" + chosen);
                return new PrecisionBindOutcome.Bound(steps, "solve");
            }
        }
        List<ProvenStep> written = solveParser.validateWrittenSteps(tcId, intent, raw, htmlExcerpt);
        if (validSteps(written)) {
            LogsManager.info("PRECISION_SOLVE: " + tcId + " written locator");
            return new PrecisionBindOutcome.Bound(written, "solve");
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
        if (budget.exhausted()) {
            return Optional.of(new PrecisionBindOutcome.FallbackKeel("CAP_EXCEEDED"));
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
        return buildCandidates(intent, DomCandidateExtractor.extract(slimHtml), spent, failed, probe);
    }

    private List<DomCandidate> buildCandidates(StepIntentBinder.IntentLine intent,
            List<DomCandidate> snapshot, List<ProvenStep> spent, List<FailedLocator> failed,
            CandidateLivenessProbe probe) {
        // Apply the same eligibility rule enforced by the binder before expensive WebDriver probes.
        List<DomCandidate> candidates = snapshot.stream()
                .filter(candidate -> StepIntentBinder.actionCompatible(intent, candidate)).toList();
        if (StepIntentBinder.spendsMustAvoidPriorFills(intent)) {
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
