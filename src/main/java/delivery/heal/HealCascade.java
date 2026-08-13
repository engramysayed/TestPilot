package delivery.heal;

import delivery.authoring.AuthoringService;
import delivery.authoring.DomCandidate;
import delivery.authoring.DomCandidateExtractor;
import delivery.authoring.StepIntentBinder;
import delivery.codegen.ProvenStep;
import utils.LogsManager;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Heal stack for one failed intent:
 * shortlist Ollama/Cursor (candidateId) → optional vision widen → last-hope invent (Cursor or AgentRouter).
 */
public class HealCascade {
    private static final java.util.Set<String> INTERACTIVE_TAGS = java.util.Set.of(
            "button", "a", "input", "select", "textarea", "summary", "option", "label");

    private final AuthoringService authoring;
    private final CursorHealClient cursor;
    private final FreeInventHealer freeInvent;
    private final boolean visionWidenEnabled;
    private final int maxInventPerTc;
    private final java.util.Map<String, Integer> inventAttemptsByTc =
            new java.util.concurrent.ConcurrentHashMap<>();

    public HealCascade(AuthoringService authoring, CursorHealClient cursor) {
        this(authoring, cursor, FreeInventHealer.fromConfig(
                cursor == null ? new CursorHealClient() : cursor), resolveVisionWidenEnabled());
    }

    public HealCascade(
            AuthoringService authoring,
            CursorHealClient cursor,
            FreeInventHealer freeInvent,
            boolean visionWidenEnabled
    ) {
        this.authoring = authoring;
        this.cursor = cursor == null ? new CursorHealClient() : cursor;
        this.freeInvent = freeInvent == null ? FreeInventHealer.fromConfig(this.cursor) : freeInvent;
        this.visionWidenEnabled = visionWidenEnabled;
        this.maxInventPerTc = resolveMaxInventPerTc();
    }

    public HealCascade(AuthoringService authoring) {
        this(authoring, new CursorHealClient());
    }

    /**
     * @param tryOllama when false, skip Ollama (e.g. authorIntent already healed with vision)
     */
    public HealResult heal(
            String tcId,
            StepIntentBinder.IntentLine intent,
            String slimHtml,
            byte[] pngOrNull,
            String failureReason,
            Path screenshotPathOrNull,
            boolean tryOllama
    ) {
        return heal(tcId, intent, slimHtml, pngOrNull, failureReason,
                screenshotPathOrNull, tryOllama, List.of(), true);
    }

    public HealResult heal(
            String tcId,
            StepIntentBinder.IntentLine intent,
            String slimHtml,
            byte[] pngOrNull,
            String failureReason,
            Path screenshotPathOrNull,
            boolean tryOllama,
            List<String> priorStepSummaries
    ) {
        return heal(tcId, intent, slimHtml, pngOrNull, failureReason,
                screenshotPathOrNull, tryOllama, priorStepSummaries, true);
    }

    public HealResult heal(
            String tcId,
            StepIntentBinder.IntentLine intent,
            String slimHtml,
            byte[] pngOrNull,
            String failureReason,
            Path screenshotPathOrNull,
            boolean tryOllama,
            List<String> priorStepSummaries,
            boolean allowInvent
    ) {
        if (intent == null || slimHtml == null || slimHtml.isBlank()) {
            return HealResult.fail("HEAL_EXHAUSTED: missing intent or HTML");
        }
        List<String> priorSteps = priorStepSummaries == null ? List.of() : priorStepSummaries;
        String reason = failureReason == null ? "" : failureReason;
        List<DomCandidate> candidates = DomCandidateExtractor.extract(slimHtml);
        if (candidates.isEmpty()) {
            HealResult invented = tryInvent(tcId, intent, slimHtml, pngOrNull, reason, priorSteps,
                    "empty_candidates", screenshotPathOrNull, allowInvent);
            return invented != null
                    ? invented
                    : HealResult.fail("HEAL_EXHAUSTED: empty DOM candidate table");
        }

        List<DomCandidate> distinctivePool =
                StepIntentBinder.retainDistinctiveMatches(intent, candidates);
        if (distinctivePool.isEmpty()
                && intent.kind() == StepIntentBinder.IntentKind.CLICK
                && StepIntentBinder.intentRequiresNamedActionControl(intent.text())) {
            distinctivePool = fallbackNamedActionMatches(intent, candidates);
        }
        boolean widened = distinctivePool.isEmpty()
                && intent.kind() == StepIntentBinder.IntentKind.CLICK;
        if (widened && !visionWidenEnabled) {
            List<String> tokens = StepIntentBinder.discriminatingTokens(intent.text(), candidates);
            return HealResult.fail(
                    "HEAL_EXHAUSTED: no DOM candidate matches distinctive tokens for: "
                            + trim(intent.text(), 80)
                            + " (candidates=" + candidates.size() + " tokens=" + tokens + ")");
        }

        List<DomCandidate> shortlist = widened
                ? widenedShortlist(intent, candidates, 16)
                : authoring.shortlistForIntent(
                        intent, distinctivePool.isEmpty() ? candidates : distinctivePool, 12);
        if (widened) {
            LogsManager.info("HEAL_VISION_WIDEN: candidates=" + candidates.size()
                    + " shortlist=" + shortlist.size());
        }
        if (shortlist.isEmpty()) {
            HealResult invented = tryInvent(tcId, intent, slimHtml, pngOrNull, reason, priorSteps,
                    "empty_shortlist", screenshotPathOrNull, allowInvent);
            return invented != null
                    ? invented
                    : HealResult.fail("HEAL_EXHAUSTED: empty shortlist after distinctive filter");
        }

        if (tryOllama) {
            List<ProvenStep> ollamaSteps = authoring.healIntentWithOllama(
                    tcId, intent, candidates, shortlist, pngOrNull, reason, priorSteps, widened);
            if (validHealSteps(intent, candidates, ollamaSteps, widened)) {
                LogsManager.info("HEAL_OLLAMA: resolved " + tcId + " intent=" + trim(intent.text(), 40));
                return HealResult.success(ollamaSteps, widened ? "vision" : "ollama");
            }
            LogsManager.info("HEAL_OLLAMA: no valid pick for " + tcId + " — escalating to Cursor");
        }

        String table = DomCandidateExtractor.formatTable(shortlist);
        String htmlExcerpt = slimHtml.length() > 8000 ? slimHtml.substring(0, 8000) : slimHtml;
        String chosen = cursor.pickCandidateId(
                intent.text(), reason, table, htmlExcerpt, screenshotPathOrNull, priorSteps);
        if (chosen != null && !chosen.isBlank()
                && shortlist.stream().anyMatch(c -> c.id().equalsIgnoreCase(chosen))) {
            List<ProvenStep> cursorSteps =
                    authoring.stepsPreferringCandidate(tcId, intent, candidates, chosen, widened);
            if (validHealSteps(intent, candidates, cursorSteps, widened)) {
                LogsManager.info("HEAL_CURSOR: resolved " + tcId + " candidateId=" + chosen);
                return HealResult.success(cursorSteps, widened ? "vision" : "cursor");
            }
        }

        HealResult invented = tryInvent(tcId, intent, slimHtml, pngOrNull, reason, priorSteps,
                "post_cursor", screenshotPathOrNull, allowInvent);
        return invented != null
                ? invented
                : HealResult.fail("HEAL_EXHAUSTED: Cursor pick path exhausted; id="
                + chosen + " reason=" + reason);
    }

    /**
     * When full retain is empty, keep action controls that carry the longest distinctive token.
     */
    private static List<DomCandidate> fallbackNamedActionMatches(
            StepIntentBinder.IntentLine intent, List<DomCandidate> candidates) {
        if (intent == null || candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        List<String> tokens = StepIntentBinder.discriminatingTokens(intent.text(), candidates);
        if (tokens.isEmpty()) {
            tokens = StepIntentBinder.discriminatingTokens(intent.text(), null);
        }
        String longest = tokens.stream()
                .max(java.util.Comparator.comparingInt(String::length))
                .orElse(null);
        if (longest == null || longest.length() < 4) {
            return List.of();
        }
        String token = longest;
        return candidates.stream()
                .filter(c -> StepIntentBinder.candidateMatchesNamedAction(intent.text(), c, candidates)
                        || (StepIntentBinder.intentActionVerbs(intent.text()).stream()
                        .anyMatch(v -> (c.value() + " " + c.label()).toLowerCase().contains(v))
                        && (c.value() + " " + c.label()).toLowerCase().contains(token)))
                .filter(c -> (c.value() + " " + c.label()).toLowerCase().contains(token))
                .toList();
    }

    /**
     * @param relaxed widened pools reached the AI precisely because no candidate carried the
     *                intent tokens, so the token rule is replaced by an interactive-control rule.
     */
    private static boolean validHealSteps(
            StepIntentBinder.IntentLine intent,
            List<DomCandidate> candidates,
            List<ProvenStep> steps,
            boolean relaxed
    ) {
        if (steps == null || steps.isEmpty() || steps.stream().anyMatch(s -> !s.validated())) {
            return false;
        }
        if (intent == null || intent.kind() != StepIntentBinder.IntentKind.CLICK) {
            return true;
        }
        for (ProvenStep step : steps) {
            if (!"click".equalsIgnoreCase(step.action())) {
                continue;
            }
            DomCandidate match = candidates.stream()
                    .filter(c -> c.strategy().equalsIgnoreCase(step.locatorStrategy())
                            && c.value().equals(step.locatorValue()))
                    .findFirst()
                    .orElse(null);
            if (relaxed) {
                if (match != null && !isInteractive(match)) {
                    LogsManager.info("HEAL_REJECT: widened pick is not an interactive control → "
                            + match.id());
                    return false;
                }
                continue;
            }
            if (match != null
                    && !StepIntentBinder.candidateCarriesDistinctiveTokens(intent.text(), match, candidates)) {
                LogsManager.info("HEAL_REJECT: distinctive-token mismatch for "
                        + trim(intent.text(), 40) + " → " + match.id());
                return false;
            }
            if (StepIntentBinder.intentRequiresNamedActionControl(intent.text())) {
                boolean ok = match != null
                        ? StepIntentBinder.candidateMatchesNamedAction(intent.text(), match, candidates)
                        : StepIntentBinder.intentActionVerbs(intent.text()).stream()
                        .anyMatch(v -> step.locatorValue() != null
                                && step.locatorValue().toLowerCase().contains(v));
                if (!ok) {
                    LogsManager.info("HEAL_REJECT: named-action intent got non-action locator "
                            + step.locatorValue());
                    return false;
                }
            }
        }
        return true;
    }

    private HealResult tryInvent(
            String tcId,
            StepIntentBinder.IntentLine intent,
            String slimHtml,
            byte[] pngOrNull,
            String failureReason,
            List<String> priorSteps,
            String whyInvoked,
            Path screenshotPathOrNull,
            boolean allowInvent
    ) {
        if (!allowInvent || !claimInventBudget(tcId)) {
            return null;
        }
        Optional<List<ProvenStep>> steps = freeInvent.invent(
                tcId, intent, slimHtml, pngOrNull, failureReason, priorSteps,
                whyInvoked, screenshotPathOrNull);
        if (steps.isEmpty()) {
            return null;
        }
        if (!inventedStepsCarryIntentTokens(intent, steps.get(), extractOrEmpty(slimHtml))) {
            return null;
        }
        return HealResult.success(steps.get(), "invent");
    }

    /** Invent runs at most {@code maxInventPerTc} times per test case, however often heal is called. */
    private boolean claimInventBudget(String tcId) {
        String key = tcId == null ? "" : tcId;
        int used = inventAttemptsByTc.merge(key, 1, Integer::sum);
        if (used > maxInventPerTc) {
            LogsManager.info("HEAL_INVENT_SKIPPED: budget exhausted for " + key
                    + " (max=" + maxInventPerTc + ")");
            return false;
        }
        return true;
    }

    /**
     * Invented locators skip the shortlist, so the distinctive-token rule is applied to the
     * locator value itself — otherwise a click could land on a same-shaped wrong control.
     */
    private static boolean inventedStepsCarryIntentTokens(
            StepIntentBinder.IntentLine intent,
            List<ProvenStep> steps,
            List<DomCandidate> corpus
    ) {
        if (intent == null || intent.kind() != StepIntentBinder.IntentKind.CLICK) {
            return true;
        }
        List<String> tokens = StepIntentBinder.discriminatingTokens(intent.text(), corpus);
        if (tokens.isEmpty()) {
            return true;
        }
        for (ProvenStep step : steps) {
            if (!"click".equalsIgnoreCase(step.action())) {
                continue;
            }
            String hay = (step.locatorValue() == null ? "" : step.locatorValue())
                    .toLowerCase(java.util.Locale.ROOT);
            for (String token : tokens) {
                if (!hay.contains(token.toLowerCase(java.util.Locale.ROOT))) {
                    LogsManager.info("HEAL_INVENT_REJECTED: locator misses intent token '" + token
                            + "' → " + step.locatorValue());
                    return false;
                }
            }
        }
        return true;
    }

    private static List<DomCandidate> extractOrEmpty(String slimHtml) {
        try {
            return DomCandidateExtractor.extract(slimHtml);
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    private static boolean isInteractive(DomCandidate candidate) {
        if (candidate == null) {
            return false;
        }
        String tag = candidate.tag() == null ? "" : candidate.tag().toLowerCase(java.util.Locale.ROOT);
        if (INTERACTIVE_TAGS.contains(tag)) {
            return true;
        }
        String hay = (candidate.value() + " " + candidate.label()).toLowerCase(java.util.Locale.ROOT);
        return hay.contains("role=button") || hay.contains("role=link")
                || hay.contains("role=tab") || hay.contains("role=menuitem");
    }

    private static List<DomCandidate> widenedShortlist(
            StepIntentBinder.IntentLine intent, List<DomCandidate> candidates, int limit) {
        List<DomCandidate> ranked = StepIntentBinder.rankedCandidates(intent, candidates);
        List<DomCandidate> source = ranked.isEmpty() ? new ArrayList<>(candidates) : ranked;
        return source.stream().limit(limit).toList();
    }

    private static int resolveMaxInventPerTc() {
        String value = System.getProperty("delivery.heal.invent.max-per-tc");
        if (value == null || value.isBlank()) {
            value = utils.PropertyReader.getProperty("delivery.heal.invent.max-per-tc");
        }
        try {
            int parsed = Integer.parseInt(value == null ? "" : value.trim());
            return parsed > 0 ? parsed : 2;
        } catch (NumberFormatException e) {
            return 2;
        }
    }

    private static boolean resolveVisionWidenEnabled() {
        String value = System.getProperty("delivery.heal.vision-widen.enabled");
        if (value == null || value.isBlank()) {
            value = utils.PropertyReader.getProperty("delivery.heal.vision-widen.enabled");
        }
        return value == null || value.isBlank()
                || "true".equalsIgnoreCase(value) || "1".equals(value);
    }

    private static String trim(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
