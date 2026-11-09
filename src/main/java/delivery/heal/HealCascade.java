package delivery.heal;

import delivery.authoring.AuthoringService;
import delivery.guard.LoopGuard;
import delivery.routing.ModelRouter;
import delivery.routing.ObservationBudget;
import delivery.settle.SettlePolicy;
import delivery.authoring.PrecisionCallBudget;
import delivery.authoring.BindDecision;
import delivery.authoring.BindQualityMetrics;
import delivery.authoring.CandidateScorer;
import delivery.authoring.DomCandidate;
import delivery.authoring.HealPickPayload;
import delivery.authoring.DomCandidateExtractor;
import delivery.authoring.StepIntentBinder;
import delivery.codegen.ProvenStep;
import delivery.vision.GroundingBrowser;
import delivery.vision.GroundingHit;
import delivery.vision.VisionGroundingConfig;
import delivery.vision.VisionGroundingProvider;
import delivery.vision.VisionHealSupport;
import utils.LogsManager;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Heal stack for one failed intent:
 * shortlist Ollama/Cursor (candidateId) → optional vision widen → last-hope invent (Cursor or AgentRouter).
 */
public class HealCascade {
    private static final java.util.Set<String> INTERACTIVE_TAGS = java.util.Set.of(
            "button", "a", "input", "select", "textarea", "summary", "option", "label",
            "combobox", "listbox", "textbox", "searchbox", "checkbox", "radio", "switch",
            "spinbutton", "slider", "tab", "menuitem", "link");

    private final AuthoringService authoring;
    private final CursorHealClient cursor;
    private final FreeInventHealer freeInvent;
    private final boolean visionWidenEnabled;
    private final VisionGroundingProvider visionOrNull;
    private final Supplier<GroundingBrowser> groundingBrowserSupplier;
    private final int maxInventPerTc;
    private final java.util.Map<String, Integer> inventAttemptsByTc =
            new java.util.concurrent.ConcurrentHashMap<>();
    private String allowedOpenPath = "";
    /** Fuller page HTML for recovery presence checks when slim HTML drops attributes. */
    private String presenceHtml = "";
    /** When set, Cursor heal/solve calls share the Precision per-job cap. */
    private PrecisionCallBudget precisionBudget;
    /** A10: per-intent heal loop guard (reset via {@link #beginIntent}). */
    private LoopGuard intentGuard;
    private CursorHealClient.HealSafety lastSafety = CursorHealClient.HealSafety.empty();
    private BindQualityMetrics bindQuality;

    public void setBindQuality(BindQualityMetrics metrics) {
        this.bindQuality = metrics;
    }

    public void setPresenceHtml(String html) {
        this.presenceHtml = html == null ? "" : html;
        if (freeInvent != null) {
            freeInvent.setPresenceHtml(this.presenceHtml);
        }
    }

    public HealCascade(AuthoringService authoring, CursorHealClient cursor) {
        this(authoring, cursor, FreeInventHealer.fromConfig(
                cursor == null ? new CursorHealClient() : cursor), resolveVisionWidenEnabled(),
                null, null);
    }

    public HealCascade(
            AuthoringService authoring,
            CursorHealClient cursor,
            VisionGroundingProvider visionOrNull,
            Supplier<GroundingBrowser> groundingBrowserSupplier
    ) {
        this(authoring, cursor, FreeInventHealer.fromConfig(
                cursor == null ? new CursorHealClient() : cursor), resolveVisionWidenEnabled(),
                visionOrNull, groundingBrowserSupplier);
    }

    public HealCascade(
            AuthoringService authoring,
            CursorHealClient cursor,
            VisionGroundingProvider visionOrNull,
            GroundingBrowser groundingBrowser
    ) {
        this(authoring, cursor, visionOrNull,
                groundingBrowser == null ? null : () -> groundingBrowser);
    }

    public HealCascade(
            AuthoringService authoring,
            CursorHealClient cursor,
            FreeInventHealer freeInvent,
            boolean visionWidenEnabled
    ) {
        this(authoring, cursor, freeInvent, visionWidenEnabled, null, null);
    }

    public HealCascade(
            AuthoringService authoring,
            CursorHealClient cursor,
            FreeInventHealer freeInvent,
            boolean visionWidenEnabled,
            VisionGroundingProvider visionOrNull,
            Supplier<GroundingBrowser> groundingBrowserSupplier
    ) {
        this.authoring = authoring;
        this.cursor = cursor == null ? new CursorHealClient() : cursor;
        this.freeInvent = freeInvent == null ? FreeInventHealer.fromConfig(this.cursor) : freeInvent;
        this.visionWidenEnabled = visionWidenEnabled;
        this.visionOrNull = visionOrNull;
        this.groundingBrowserSupplier = groundingBrowserSupplier;
        this.maxInventPerTc = resolveMaxInventPerTc();
    }

    public void setExcelOpenPath(String excelOpenPath) {
        this.allowedOpenPath = excelOpenPath == null ? "" : excelOpenPath.trim();
    }

    public void attachPrecisionBudget(PrecisionCallBudget budget) {
        this.precisionBudget = budget;
    }

    /** Start a fresh A10 heal guard for this intent (call once before retries). */
    public void beginIntent() {
        this.intentGuard = loopGuardsEnabled() ? LoopGuard.of(LoopGuard.Loop.HEAL) : null;
    }

    static boolean loopGuardsEnabled() {
        String v = System.getProperty("delivery.guard.loop", "on");
        if (v == null || v.isBlank()) {
            try { v = utils.PropertyReader.getProperty("delivery.guard.loop"); } catch (RuntimeException ignored) { v = "on"; }
        }
        return v == null || !("off".equalsIgnoreCase(v) || "false".equalsIgnoreCase(v));
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
        return heal(tcId, intent, slimHtml, pngOrNull, failureReason,
                screenshotPathOrNull, tryOllama, priorStepSummaries, allowInvent, List.of());
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
            boolean allowInvent,
            List<ProvenStep> spent
    ) {
        return heal(tcId, intent, slimHtml, pngOrNull, failureReason,
                screenshotPathOrNull, tryOllama, priorStepSummaries, allowInvent, spent,
                List.of(), null);
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
            boolean allowInvent,
            List<ProvenStep> spent,
            List<FailedLocator> failed
    ) {
        return heal(tcId, intent, slimHtml, pngOrNull, failureReason,
                screenshotPathOrNull, tryOllama, priorStepSummaries, allowInvent, spent,
                failed, null);
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
            boolean allowInvent,
            List<ProvenStep> spent,
            List<FailedLocator> failed,
            CandidateLivenessProbe probe
    ) {
        // Early guard — audit C3 #30 first extracted helper. Behaviour unchanged.
        HealResult guardReject = heal_earlyGuards(tcId, intent, slimHtml, failureReason,
                failed == null ? List.of() : failed);
        if (guardReject != null) {
            return guardReject;
        }
        List<String> priorSteps = priorStepSummaries == null ? List.of() : priorStepSummaries;
        List<ProvenStep> spentSteps = spent == null ? List.of() : spent;
        List<FailedLocator> failedLocators = failed == null ? List.of() : failed;
        SettlePolicy.SideEffects se = HealPromptSafety.sideEffectsOf(intent);
        if (slimHtml == null || slimHtml.isBlank()) {
            HealResult invented = tryInvent(tcId, intent, "", pngOrNull,
                    failureReason, priorSteps, "empty_html", screenshotPathOrNull, allowInvent, se);
            return invented != null
                    ? invented
                    : HealResult.fail("HEAL_EXHAUSTED: missing intent or HTML");
        }
        if (slimHtml == null || slimHtml.isBlank()) {
            HealResult invented = tryInvent(tcId, intent, "", pngOrNull,
                    failureReason, priorSteps, "empty_html", screenshotPathOrNull, allowInvent, se);
            return invented != null
                    ? invented
                    : HealResult.fail("HEAL_EXHAUSTED: missing intent or HTML");
        }
        String reason = failureReason == null ? "" : failureReason;
        if (!failedLocators.isEmpty()) {
            reason = reason + bannedBlock(failedLocators);
        }
        String safetyText = lastSafety == null ? "" : lastSafety.safetyBlock();
        reason = safetyText + "\n" + reason;
        List<DomCandidate> candidates = DomCandidateExtractor.extract(slimHtml);
        if (StepIntentBinder.spendsMustAvoidPriorFills(intent)) {
            candidates = StepIntentBinder.withoutSpentControls(candidates, spentSteps);
        }
        candidates = StepIntentBinder.withoutFailedLocators(candidates, failedLocators);
        int beforeLiveness = candidates.size();
        candidates = filterInteractable(candidates, probe);
        boolean livenessDropped = candidates.size() < beforeLiveness;
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

        // After a burned locator (or zero-size drop), try the next named row before paying for LLM.
        boolean allowNamedRetry = intent.kind() != StepIntentBinder.IntentKind.ASSERT_VISIBLE
                && (!failedLocators.isEmpty() || livenessDropped);
        if (allowNamedRetry && !distinctivePool.isEmpty()) {
            DomCandidate pick = authoring.shortlistForIntent(intent, distinctivePool, 1).stream()
                    .findFirst()
                    .orElse(null);
            if (pick != null) {
                List<ProvenStep> retrySteps = authoring.stepsPreferringCandidate(
                        tcId, intent, candidates, pick.id(), false);
                if (validHealSteps(intent, candidates, retrySteps, false, spentSteps)) {
                    LogsManager.info("HEAL_RETRY: named control " + pick.id() + " for "
                            + trim(intent.text(), 40));
                    LoopGuard.Verdict gv = guardRecord("retry:" + pick.id(), true, 0, se);
                    return stoppedOr(gv, HealResult.success(retrySteps, "retry"));
                }
            }
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

        int shortlistCap = delivery.authoring.CandidateScorer.SHORTLIST_MAX;
        List<DomCandidate> shortlist = widened
                ? widenedShortlist(intent, candidates, 16)
                : authoring.shortlistForIntent(
                        intent, distinctivePool.isEmpty() ? candidates : distinctivePool, shortlistCap);
        if (widened) {
            LogsManager.info("HEAL_VISION_WIDEN: candidates=" + candidates.size()
                    + " shortlist=" + shortlist.size());
        }

        Optional<GroundingHit> bboxHit = tryVisionBboxWiden(intent, candidates, widened, failedLocators);
        if (bboxHit.isPresent()) {
            GroundingHit hit = bboxHit.get();
            List<ProvenStep> bboxSteps = authoring.stepsPreferringCandidate(
                    tcId, intent, hit.table(), hit.candidateId(), true);
            if (validHealSteps(intent, hit.table(), bboxSteps, true, spentSteps)) {
                LogsManager.info("HEAL_VISION_BBOX: resolved " + tcId + " candidateId=" + hit.candidateId());
                LoopGuard.Verdict gv = guardRecord("vision:bbox:" + hit.candidateId(), true, 1, se);
                return stoppedOr(gv, HealResult.success(bboxSteps, "vision"));
            }
            shortlist = prependCandidate(shortlist, hit.table(), hit.candidateId());
        }

        if (shortlist.isEmpty()) {
            HealResult invented = tryInvent(tcId, intent, slimHtml, pngOrNull, reason, priorSteps,
                    "empty_shortlist", screenshotPathOrNull, allowInvent);
            return invented != null
                    ? invented
                    : HealResult.fail("HEAL_EXHAUSTED: empty shortlist after distinctive filter");
        }

        // A row-picker cannot answer when no row carries the name, and the page will not change
        // between attempts, so paying for the pick is a guaranteed loss.
        boolean answerable = shortlistCanAnswer(intent, shortlist);
        boolean ollamaAllowed = delivery.privacy.ProviderPolicy.fromEnvironment()
                .allows(delivery.privacy.ProviderPolicy.Kind.OLLAMA);
        // CLEAR shortlist (one row): skip Ollama — deterministic named retry / that row.
        if (tryOllama && answerable && ollamaAllowed && shortlist.size() == 1) {
            LogsManager.info("HEAL_OLLAMA_SKIPPED: clear shortlist of 1 — deterministic");
            List<ProvenStep> clearSteps = authoring.stepsPreferringCandidate(
                    tcId, intent, candidates, shortlist.get(0).id(), widened);
            if (validHealSteps(intent, candidates, clearSteps, widened, spentSteps)) {
                LoopGuard.Verdict gv = guardRecord("clear:" + shortlist.get(0).id(), true, 0, se);
                return stoppedOr(gv, HealResult.success(clearSteps, "retry"));
            }
        } else if (tryOllama && answerable && ollamaAllowed && shortlist.size() >= 2) {
            List<ProvenStep> ollamaSteps = authoring.healIntentWithOllama(
                    tcId, intent, candidates, shortlist, pngOrNull, reason, priorSteps, widened, slimHtml);
            if (validHealSteps(intent, candidates, ollamaSteps, widened, spentSteps)) {
                LogsManager.info("HEAL_OLLAMA: resolved " + tcId + " intent=" + trim(intent.text(), 40));
                LoopGuard.Verdict gv = guardRecord("ollama", true, 1, se);
                return stoppedOr(gv, HealResult.success(ollamaSteps, widened ? "vision" : "ollama"));
            }
            LogsManager.info("HEAL_OLLAMA: no valid pick for " + tcId + " — escalating to Cursor");
        } else if (tryOllama && !ollamaAllowed) {
            LogsManager.info("HEAL_OLLAMA_SKIPPED: provider_not_allowlisted");
        } else if (tryOllama) {
            LogsManager.info("HEAL_OLLAMA_SKIPPED: no shortlist row carries the name in "
                    + trim(intent.text(), 40) + " — going straight to Cursor");
        }

        String table = DomCandidateExtractor.formatTable(shortlist);
        int healObs = ObservationBudget.forIntent(ModelRouter.Intent.HEAL);
        String htmlExcerpt = ObservationBudget.clip(slimHtml, healObs);
        String solveReason = reason;
        if (allowedOpenPath != null && !allowedOpenPath.isBlank()
                && !solveReason.contains("Excel open-path")) {
            solveReason = solveReason
                    + "\nExcel open-path (only allowed navigation target): " + allowedOpenPath;
        }
        List<DomCandidate> decidePool = distinctivePool.isEmpty() ? candidates : distinctivePool;
        BindDecision tieDecision = CandidateScorer.decide(CandidateScorer.score(intent, decidePool));
        boolean tiePick = tieDecision.kind() == BindDecision.Kind.TIE
                && tieDecision.shortlist() != null
                && tieDecision.shortlist().size() >= 2;

        if (tiePick) {
            if (!allowPrecisionCursorCall("pick")) {
                return HealResult.fail("HEAL_EXHAUSTED: precision call cap exceeded");
            }
            LoopGuard.Verdict beforePick = guardBefore("pick");
            if (beforePick != null && beforePick.stop()) {
                return HealResult.fail("HEAL_STOPPED: " + beforePick.reason());
            }
            HealPickPayload payload = HealPickPayload.from(intent, tieDecision, slimHtml);
            if (screenshotPathOrNull != null && java.nio.file.Files.isRegularFile(screenshotPathOrNull)) {
                payload = payload.withScreenshotPath(screenshotPathOrNull.toAbsolutePath().toString());
            }
            String chosen = cursor.pickCandidateId(
                    intent.text(), solveReason, payload, screenshotPathOrNull, priorSteps);
            if (!chosen.isBlank() && shortlist.stream().anyMatch(c -> c.id().equalsIgnoreCase(chosen))) {
                List<ProvenStep> cursorSteps =
                        authoring.stepsPreferringCandidate(tcId, intent, candidates, chosen, widened);
                if (validHealSteps(intent, candidates, cursorSteps, widened, spentSteps)) {
                    LogsManager.info("HEAL_CURSOR_PICK: resolved " + tcId + " candidateId=" + chosen);
                    LoopGuard.Verdict gv = guardRecord("pick:" + chosen, true, 1, se);
                    return stoppedOr(gv, HealResult.success(cursorSteps, "cursor"));
                }
            }
            guardRecord("pick:miss", false, 1, se);
            // Invent stays a later call — never acceptWrittenLocator on the TIE pick path.
            HealResult invented = tryInvent(tcId, intent, slimHtml, pngOrNull, reason, priorSteps,
                    "post_pick", screenshotPathOrNull, allowInvent);
            return invented != null
                    ? invented
                    : HealResult.fail("HEAL_EXHAUSTED: Cursor pick path exhausted; id="
                    + chosen + " reason=" + reason);
        }

        if (!allowPrecisionCursorCall("solve")) {
            return HealResult.fail("HEAL_EXHAUSTED: precision call cap exceeded");
        }
        LoopGuard.Verdict beforeSolve = guardBefore("solve");
        if (beforeSolve != null && beforeSolve.stop()) {
            return HealResult.fail("HEAL_STOPPED: " + beforeSolve.reason());
        }
        String raw = cursor.solve(
                intent.text(), solveReason, table, htmlExcerpt, screenshotPathOrNull, priorSteps, lastSafety); // sets pendingSafety
        String chosen = CursorHealClient.parseCandidateId(raw);
        if (raw != null && raw.contains("\"mode\":\"abort\"")) {
            guardRecord("solve:abort", false, 1, se);
            return HealResult.fail("HEAL_ABORTED: model refused — crash/unsafe");
        }
        if (!chosen.isBlank() && shortlist.stream().anyMatch(c -> c.id().equalsIgnoreCase(chosen))) {
            LogsManager.info("CURSOR_HEAL: Auto picked candidateId=" + chosen);
            List<ProvenStep> cursorSteps =
                    authoring.stepsPreferringCandidate(tcId, intent, candidates, chosen, widened);
            if (validHealSteps(intent, candidates, cursorSteps, widened, spentSteps)) {
                LogsManager.info("HEAL_CURSOR: resolved " + tcId + " candidateId=" + chosen);
                LoopGuard.Verdict gv = guardRecord("solve:" + chosen, true, 1, se);
                return stoppedOr(gv, HealResult.success(cursorSteps, widened ? "vision" : "cursor"));
            }
        }
        guardRecord("solve:miss", false, 1, se);

        HealResult written = acceptWrittenLocator(tcId, intent, raw, htmlExcerpt, allowInvent);
        if (written != null) {
            return written;
        }
        if (!raw.isBlank()) {
            return HealResult.fail("HEAL_EXHAUSTED: Cursor solved nothing usable for "
                    + trim(intent.text(), 60) + "; reason=" + reason);
        }
        HealResult invented = tryInvent(tcId, intent, slimHtml, pngOrNull, reason, priorSteps,
                "post_cursor", screenshotPathOrNull, allowInvent);
        return invented != null
                ? invented
                : HealResult.fail("HEAL_EXHAUSTED: Cursor pick path exhausted; id="
                + chosen + " reason=" + reason);
    }

    private Optional<GroundingHit> tryVisionBboxWiden(
            StepIntentBinder.IntentLine intent,
            List<DomCandidate> candidates,
            boolean widened,
            List<FailedLocator> failed) {
        if (!widened || !VisionGroundingConfig.enabled()
                || visionOrNull == null || groundingBrowserSupplier == null) {
            return Optional.empty();
        }
        if (!delivery.privacy.ProviderPolicy.fromEnvironment()
                .allows(delivery.privacy.ProviderPolicy.Kind.VISION)) {
            return Optional.empty();
        }
        GroundingBrowser browser = groundingBrowserSupplier.get();
        if (browser == null) {
            return Optional.empty();
        }
        return VisionHealSupport.tryBboxHit(intent, candidates, visionOrNull, browser, failed);
    }

    private static List<DomCandidate> prependCandidate(
            List<DomCandidate> shortlist, List<DomCandidate> table, String candidateId) {
        DomCandidate grounded = table.stream()
                .filter(c -> candidateId.equals(c.id()))
                .findFirst()
                .orElse(null);
        if (grounded == null) {
            return shortlist;
        }
        List<DomCandidate> out = new ArrayList<>();
        out.add(grounded);
        for (DomCandidate c : shortlist) {
            if (!candidateId.equals(c.id())) {
                out.add(c);
            }
        }
        return out;
    }

    /**
     * Audit C3 #30 — first extracted helper from the 256-line heal() body.
     * Runs the cheap reject checks (null intent, bindQuality metric bump, LoopGuard init, safety
     * side-effects, crash-page abort, LoopGuard pre-check) in one place. Returns a HealResult to
     * stop immediately, or null to continue the main heal flow.
     * Behaviour is identical to the inline version it replaced.
     */
    private HealResult heal_earlyGuards(
            String tcId,
            StepIntentBinder.IntentLine intent,
            String slimHtml,
            String failureReason,
            List<FailedLocator> failedLocators
    ) {
        if (intent == null) {
            return HealResult.fail("HEAL_EXHAUSTED: missing intent or HTML");
        }
        if (bindQuality != null) {
            bindQuality.healEntered++;
        }
        if (intentGuard == null && loopGuardsEnabled()) {
            intentGuard = LoopGuard.of(LoopGuard.Loop.HEAL);
        }
        boolean secret = HealPromptSafety.isSecretIntent(intent);
        SettlePolicy.SideEffects se = HealPromptSafety.sideEffectsOf(intent);
        List<String> banned = HealPromptSafety.formatBanned(failedLocators);
        String htmlForSafety = slimHtml == null ? "" : slimHtml;
        boolean crash = HealPromptSafety.looksLikeCrash(failureReason, htmlForSafety);
        String safetyText = HealPromptSafety.safetyBlock(secret, se, banned, crash);
        lastSafety = new CursorHealClient.HealSafety(secret, se.wire, banned, crash, safetyText);
        if (crash) {
            LogsManager.info("HEAL_ABORT: crash/error page detected for " + tcId);
            return HealResult.fail("HEAL_ABORTED: crash or fatal error page — heal skipped");
        }
        LoopGuard.Verdict pre = guardBefore("heal:" + (intent.text() == null ? "" : intent.text()));
        if (pre != null && pre.stop()) {
            return HealResult.fail("HEAL_STOPPED: " + pre.reason());
        }
        return null;
    }

    private static String bannedBlock(List<FailedLocator> failed) {
        StringBuilder sb = new StringBuilder("\nBanned locators (do not pick the same control):");
        for (FailedLocator f : failed) {
            if (f == null || f.value() == null || f.value().isBlank()) {
                continue;
            }
            sb.append("\n- ").append(f.strategy() == null ? "" : f.strategy())
                    .append(": ").append(f.value());
            if (f.errorSummary() != null && !f.errorSummary().isBlank()) {
                sb.append(" → ").append(trim(f.errorSummary(), 120));
            }
        }
        return sb.toString();
    }

    private static List<DomCandidate> filterInteractable(
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
            boolean relaxed,
            List<ProvenStep> spent
    ) {
        if (steps == null || steps.isEmpty() || steps.stream().anyMatch(s -> !s.validated())) {
            return false;
        }
        if (intent == null) {
            return true;
        }
        if (StepIntentBinder.spendsMustAvoidPriorFills(intent)
                && steps.stream().anyMatch(s -> StepIntentBinder.isSpentLocator(s, spent))) {
            LogsManager.info("HEAL_REJECT: spent locator reused for " + trim(intent.text(), 40));
            return false;
        }
        if (StepIntentBinder.wantsFormSubmit(intent.text()) && clickIsNonSubmitNavigation(steps, candidates)) {
            LogsManager.info("HEAL_REJECT: form submit picked a non-submit navigation control");
            return false;
        }
        if (intent.kind() == StepIntentBinder.IntentKind.TYPE_FIELD
                || intent.kind() == StepIntentBinder.IntentKind.ASSERT_VISIBLE) {
            return fieldStepsHitTheNamedControl(intent, candidates, steps, spent);
        }
        boolean clickHonesty = intent.kind() == StepIntentBinder.IntentKind.CLICK
                || (intent.kind() == StepIntentBinder.IntentKind.CLICK_LOGIN
                && StepIntentBinder.wantsFormSubmit(intent.text()));
        if (!clickHonesty) {
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

    /**
     * A locator Cursor wrote itself instead of picking a row. It costs the same budget as invent
     * because it is the same power, and it passes the same validation.
     */
    private HealResult acceptWrittenLocator(
            String tcId,
            StepIntentBinder.IntentLine intent,
            String raw,
            String slimHtml,
            boolean allowInvent
    ) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        boolean hasWrittenSteps = raw.contains("\"steps\"")
                || raw.contains("\"recoverySteps\"")
                || raw.contains("\"mode\"");
        if (!hasWrittenSteps) {
            return null;
        }
        if (!allowInvent || !hasInventBudget(tcId)) {
            if (allowInvent) {
                logInventBudgetExhausted(tcId);
            }
            return null;
        }
        Optional<HealResult> parsed = freeInvent.parseInventResponse(
                tcId, intent, raw, slimHtml, presenceHtml, allowedOpenPath);
        if (parsed.isEmpty()) {
            return null;
        }
        HealResult result = parsed.get();
        if ("recovery".equals(result.tierUsed())) {
            consumeInventBudget(tcId);
            LogsManager.info("HEAL_RECOVERY: Cursor/solve returned recovery plan for " + tcId);
            SettlePolicy.SideEffects se = HealPromptSafety.sideEffectsOf(intent);
            LoopGuard.Verdict gv = guardRecord("invent:recovery", true, 1, se);
            return stoppedOr(gv, result);
        }
        List<ProvenStep> steps = result.steps();
        if (!inventedStepsCarryIntentTokens(intent, steps, extractOrEmpty(slimHtml))
                || !writtenLocatorsCarryTheName(intent, steps)) {
            return null;
        }
        consumeInventBudget(tcId);
        LogsManager.info("HEAL_CURSOR_SOLVE: resolved " + tcId + " with a written locator");
        SettlePolicy.SideEffects se = HealPromptSafety.sideEffectsOf(intent);
        LoopGuard.Verdict gv = guardRecord("invent:written", true, 1, se);
        return stoppedOr(gv, HealResult.success(steps, "invent"));
    }

    /**
     * A written locator never appears in the candidate table, so the field-name rule is applied to
     * the locator text itself. Without this a solved field intent could target any control.
     */
    private static boolean writtenLocatorsCarryTheName(
            StepIntentBinder.IntentLine intent, List<ProvenStep> steps) {
        if (intent == null
                || (intent.kind() != StepIntentBinder.IntentKind.TYPE_FIELD
                && intent.kind() != StepIntentBinder.IntentKind.ASSERT_VISIBLE)) {
            return true;
        }
        for (ProvenStep step : steps) {
            if ("navigate".equalsIgnoreCase(step.action())) {
                continue;
            }
            DomCandidate asCandidate = new DomCandidate(
                    "written", step.locatorStrategy(), step.locatorValue(),
                    "input", step.locatorValue());
            if (!StepIntentBinder.candidateSharesFieldToken(intent.text(), asCandidate)) {
                LogsManager.info("HEAL_REJECT: written locator misses the field name in "
                        + trim(intent.text(), 40) + " → " + step.locatorValue());
                return false;
            }
        }
        return true;
    }

    /** True when at least one shortlist row carries the name the intent asks for. */
    private static boolean shortlistCanAnswer(
            StepIntentBinder.IntentLine intent, List<DomCandidate> shortlist) {
        if (intent == null
                || (intent.kind() != StepIntentBinder.IntentKind.TYPE_FIELD
                && intent.kind() != StepIntentBinder.IntentKind.ASSERT_VISIBLE)) {
            return true;
        }
        return shortlist.stream()
                .anyMatch(c -> StepIntentBinder.candidateSharesFieldToken(intent.text(), c));
    }

    private boolean allowPrecisionCursorCall(String label) {
        if (precisionBudget == null) {
            return true;
        }
        if (precisionBudget.tryConsume()) {
            return true;
        }
        LogsManager.info("PRECISION_BUDGET: skipped heal " + label + " — cap exceeded");
        return false;
    }


    private LoopGuard.Verdict guardBefore(String action) {
        if (intentGuard == null) return null;
        LoopGuard.Verdict v = intentGuard.beforeIteration();
        if (v.stop()) {
            LogsManager.info("HEAL_LOOP_GUARD stop before " + action + ": " + v.reason());
        } else if (v.level() == LoopGuard.Level.WARN) {
            LogsManager.info("HEAL_LOOP_GUARD warn: " + v.reason());
        }
        return v;
    }

    private LoopGuard.Verdict guardRecord(String action, boolean ok, int modelCalls, SettlePolicy.SideEffects se) {
        if (intentGuard == null) return null;
        LoopGuard.Verdict v = intentGuard.record(new LoopGuard.Step(
                null, action, ok, modelCalls, modelCalls, se == null ? SettlePolicy.SideEffects.UNKNOWN : se));
        if (v.stop()) {
            LogsManager.info("HEAL_LOOP_GUARD stop after " + action + ": " + v.reason());
        } else if (v.level() == LoopGuard.Level.WARN) {
            LogsManager.info("HEAL_LOOP_GUARD warn: " + v.reason());
        }
        return v;
    }

    private HealResult stoppedOr(LoopGuard.Verdict v, HealResult okResult) {
        if (v != null && v.stop()) {
            return HealResult.fail("HEAL_STOPPED: " + v.reason());
        }
        return okResult;
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
        return tryInvent(tcId, intent, slimHtml, pngOrNull, failureReason, priorSteps,
                whyInvoked, screenshotPathOrNull, allowInvent, HealPromptSafety.sideEffectsOf(intent));
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
            boolean allowInvent,
            SettlePolicy.SideEffects se
    ) {
        if (!allowInvent || !hasInventBudget(tcId)) {
            if (allowInvent) {
                logInventBudgetExhausted(tcId);
            }
            return null;
        }
        if (precisionBudget != null && !allowPrecisionCursorCall("invent")) {
            return null;
        }
        freeInvent.setHealSafety(lastSafety);
        Optional<HealResult> healed = freeInvent.inventHealResult(
                tcId, intent, slimHtml, pngOrNull, failureReason, priorSteps,
                whyInvoked, screenshotPathOrNull, allowedOpenPath);
        if (healed.isEmpty()) {
            return null;
        }
        HealResult result = healed.get();
        if ("recovery".equals(result.tierUsed())) {
            consumeInventBudget(tcId);
            LogsManager.info("HEAL_RECOVERY: invent returned recovery plan for " + tcId);
            LoopGuard.Verdict gv = guardRecord("invent:" + whyInvoked + ":recovery", true, 1, se);
            return stoppedOr(gv, result);
        }
        if (!inventedStepsCarryIntentTokens(intent, result.steps(), extractOrEmpty(slimHtml))) {
            return null;
        }
        consumeInventBudget(tcId);
        // AE-L3: invent success must record LoopGuard (model call + attempt).
        LoopGuard.Verdict gv = guardRecord("invent:" + whyInvoked, true, 1, se);
        return stoppedOr(gv, HealResult.success(result.steps(), "invent"));
    }

    /** Invent runs at most {@code maxInventPerTc} successful usable parses per test case. */
    private boolean hasInventBudget(String tcId) {
        String key = tcId == null ? "" : tcId;
        return inventAttemptsByTc.getOrDefault(key, 0) < maxInventPerTc;
    }

    private void consumeInventBudget(String tcId) {
        String key = tcId == null ? "" : tcId;
        inventAttemptsByTc.merge(key, 1, Integer::sum);
    }

    private void logInventBudgetExhausted(String tcId) {
        String key = tcId == null ? "" : tcId;
        LogsManager.info("HEAL_INVENT_SKIPPED: budget exhausted for " + key
                + " (max=" + maxInventPerTc + ")");
    }

    private static boolean fieldStepsHitTheNamedControl(
            StepIntentBinder.IntentLine intent,
            List<DomCandidate> candidates,
            List<ProvenStep> steps,
            List<ProvenStep> spent
    ) {
        for (ProvenStep step : steps) {
            if ("navigate".equalsIgnoreCase(step.action())) {
                continue;
            }
            String action = step.action() == null ? "" : step.action().toLowerCase();
            boolean namedControlAssert = "assert".equals(action)
                    && !"textContains".equals(step.assertionType());
            if (!"type".equals(action) && !"select".equals(action) && !namedControlAssert) {
                continue;
            }
            DomCandidate match = candidates.stream()
                    .filter(c -> c.strategy().equalsIgnoreCase(step.locatorStrategy())
                            && c.value().equals(step.locatorValue()))
                    .findFirst()
                    .orElse(null);
            if (match == null) {
                continue;
            }
            // Selected/checked asserts must reuse the control this TC just filled — that is the point.
            String assertType = step.assertionType() == null ? "" : step.assertionType().toLowerCase();
            boolean stateAssert = "selected".equals(assertType)
                    || "checked".equals(assertType)
                    || "unchecked".equals(assertType)
                    || "enabled".equals(assertType)
                    || "disabled".equals(assertType);
            if (!stateAssert && StepIntentBinder.isSpentLocator(match, spent)) {
                LogsManager.info("HEAL_REJECT: field intent " + trim(intent.text(), 40)
                        + " reused a control this TC already filled → " + match.id());
                return false;
            }
            if (!StepIntentBinder.candidateSharesFieldToken(intent.text(), match)) {
                LogsManager.info("HEAL_REJECT: field intent " + trim(intent.text(), 40)
                        + " picked an unrelated control → " + match.id());
                return false;
            }
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

    private static boolean clickIsNonSubmitNavigation(List<ProvenStep> steps, List<DomCandidate> candidates) {
        if (steps == null) {
            return false;
        }
        for (ProvenStep step : steps) {
            if (!"click".equalsIgnoreCase(step.action())) {
                continue;
            }
            DomCandidate match = candidates == null ? null : candidates.stream()
                    .filter(c -> c.strategy().equalsIgnoreCase(step.locatorStrategy())
                            && c.value().equals(step.locatorValue()))
                    .findFirst()
                    .orElse(null);
            if (match != null && StepIntentBinder.looksLikeNonSubmitNavigation(match)) {
                return true;
            }
            if (StepIntentBinder.looksLikeNonSubmitNavigationLocator(step.locatorValue())) {
                return true;
            }
        }
        return false;
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
