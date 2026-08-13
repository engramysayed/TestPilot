package delivery.authoring;

import delivery.codegen.ProvenStep;
import delivery.excel.ManualTestCase;
import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class AuthoringService {
    private final LocalLlmClient llmClient;
    private final LocatorValidator validator;

    public AuthoringService(LocalLlmClient llmClient, LocatorValidator validator) {
        this.llmClient = llmClient;
        this.validator = validator;
    }

    public List<ProvenStep> author(ManualTestCase tc, String slimHtml, Path screenshotOrNull) throws Exception {
        byte[] png = readPng(screenshotOrNull);
        List<DomCandidate> candidates = DomCandidateExtractor.extract(slimHtml);
        StepIntentBinder.BindResult bound = StepIntentBinder.bind(tc, candidates);
        if (bound.ok()) {
            return bound.steps();
        }
        String reason = bound.rejectReason() == null ? "" : bound.rejectReason();
        if (reason.startsWith("AMBIGUOUS:")) {
            return resolveAmbiguousOrFallback(tc, candidates, reason, png);
        }
        if (!reason.isBlank()) {
            // Bind failure heal: vision over top candidates when a screenshot is available
            List<ProvenStep> healed = healBindFailureWithVision(tc, candidates, reason, png);
            if (!healed.isEmpty() && healed.stream().allMatch(ProvenStep::validated)) {
                return healed;
            }
            return List.of(rejectStep(tc.tcId(), reason));
        }
        return authorWithCandidates(tc, slimHtml, candidates, LocatorPolicy.promptRules(), false);
    }

    /**
     * Bind one Excel intent against the current page HTML (stepwise prove).
     * When {@code allowVisionHeal} is true, AMBIGUOUS / bind-failure may call Ollama.
     * ProvePhase prefers {@code allowVisionHeal=false} and routes heals through {@code HealCascade}.
     */
    public List<ProvenStep> authorIntent(
            String tcId,
            StepIntentBinder.IntentLine intent,
            String slimHtml,
            byte[] pngOrNull,
            boolean allowVisionHeal
    ) throws Exception {
        List<DomCandidate> candidates = DomCandidateExtractor.extract(slimHtml);
        StepIntentBinder.BindResult bound = StepIntentBinder.bindSingle(intent, tcId, candidates, List.of());
        if (bound.ok()) {
            return bound.steps();
        }
        String reason = bound.rejectReason() == null ? "" : bound.rejectReason();
        if (reason.isBlank()) {
            return List.of();
        }
        if (!allowVisionHeal) {
            return List.of(rejectStep(tcId, reason));
        }
        ManualTestCase synthetic = new ManualTestCase(
                tcId, "", "", "1. " + intent.text(), "", "", "");
        if (reason.startsWith("AMBIGUOUS:")) {
            return resolveAmbiguousOrFallback(synthetic, candidates, reason, pngOrNull);
        }
        List<ProvenStep> healed = healBindFailureWithVision(synthetic, candidates, reason, pngOrNull);
        if (!healed.isEmpty() && healed.stream().allMatch(ProvenStep::validated)) {
            return healed;
        }
        return List.of(rejectStep(tcId, reason));
    }

    /**
     * Bind one Excel intent; AMBIGUOUS / bind-failure may call Ollama vision heal.
     */
    public List<ProvenStep> authorIntent(
            String tcId,
            StepIntentBinder.IntentLine intent,
            String slimHtml,
            byte[] pngOrNull
    ) throws Exception {
        return authorIntent(tcId, intent, slimHtml, pngOrNull, true);
    }

    /** Backward-compatible overload (no vision bytes). */
    public List<ProvenStep> authorIntent(
            String tcId,
            StepIntentBinder.IntentLine intent,
            String slimHtml
    ) throws Exception {
        return authorIntent(tcId, intent, slimHtml, null, true);
    }

    /** Top-N candidates for heal shortlist (scored when possible). */
    public List<DomCandidate> shortlistForIntent(
            StepIntentBinder.IntentLine intent,
            List<DomCandidate> candidates,
            int limit
    ) {
        int n = limit <= 0 ? 12 : limit;
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        List<DomCandidate> pool = StepIntentBinder.retainDistinctiveMatches(intent, candidates);
        if (pool.isEmpty()) {
            if (intent != null && StepIntentBinder.intentRequiresNamedActionControl(intent.text())) {
                return List.of();
            }
            pool = candidates;
        }
        List<DomCandidate> scored = StepIntentBinder.rankedCandidates(intent, pool);
        if (!scored.isEmpty()) {
            return scored.stream().limit(n).toList();
        }
        return pool.stream().limit(n).toList();
    }

    /**
     * Ollama vision/text heal for a single intent — picks candidateId from shortlist only.
     */
    public List<ProvenStep> healIntentWithOllama(
            String tcId,
            StepIntentBinder.IntentLine intent,
            List<DomCandidate> candidates,
            List<DomCandidate> shortlist,
            byte[] pngOrNull,
            String failureReason
    ) {
        return healIntentWithOllama(
                tcId, intent, candidates, shortlist, pngOrNull, failureReason, List.of());
    }

    public List<ProvenStep> healIntentWithOllama(
            String tcId,
            StepIntentBinder.IntentLine intent,
            List<DomCandidate> candidates,
            List<DomCandidate> shortlist,
            byte[] pngOrNull,
            String failureReason,
            List<String> priorStepSummaries
    ) {
        if (shortlist == null || shortlist.isEmpty() || candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        boolean hasImage = pngOrNull != null && pngOrNull.length > 0;
        String system = hasImage
                ? LocatorPolicy.visionHealRules()
                : """
                Pick exactly one candidateId from the shortlist that best matches the Excel step intent.
                Respond with a short Thought, then Action JSON: {"candidateId":"<id from shortlist>"}.
                Do not invent ids. Do not return locators.
                """;
        String user = """
                TC_ID: %s
                Intent: %s
                Failure: %s
                ## Already completed in this TC
                %s
                Shortlist:
                %s
                %s
                """.formatted(
                tcId,
                intent == null ? "" : intent.text(),
                failureReason == null ? "" : failureReason,
                formatPriorSteps(priorStepSummaries),
                DomCandidateExtractor.formatTable(shortlist),
                hasImage ? "(Screenshot of current page is attached as an image.)" : ""
        );
        try {
            String raw = hasImage
                    ? llmClient.completeJson(system, user, pngOrNull)
                    : llmClient.completeJson(system, user);
            String chosenId = parseCandidateId(raw);
            if (!shortlistContains(shortlist, chosenId)) {
                return List.of();
            }
            return stepsPreferringCandidate(tcId, intent, candidates, chosenId);
        } catch (Exception e) {
            if (hasImage) {
                try {
                    String raw = llmClient.completeJson("""
                            Pick exactly one candidateId from the shortlist that best matches the Excel step intent.
                            Respond with a short Thought, then Action JSON:
                            {"candidateId":"<id from shortlist>"}.
                            """, user);
                    String chosenId = parseCandidateId(raw);
                    if (!shortlistContains(shortlist, chosenId)) {
                        return List.of();
                    }
                    return stepsPreferringCandidate(tcId, intent, candidates, chosenId);
                } catch (Exception ignored) {
                    return List.of();
                }
            }
            return List.of();
        }
    }

    private static boolean shortlistContains(List<DomCandidate> shortlist, String candidateId) {
        if (candidateId == null || candidateId.isBlank() || shortlist == null) {
            return false;
        }
        return shortlist.stream().anyMatch(c -> c.id().equalsIgnoreCase(candidateId));
    }

    /** Re-bind a single intent preferring a shortlist candidateId. */
    public List<ProvenStep> stepsPreferringCandidate(
            String tcId,
            StepIntentBinder.IntentLine intent,
            List<DomCandidate> candidates,
            String candidateId
    ) {
        if (candidateId == null || candidateId.isBlank() || intent == null) {
            return List.of();
        }
        // Text-visibility intents: keep textContains (body text), don't swap to a weak landmark
        if (intent.kind() == StepIntentBinder.IntentKind.ASSERT_VISIBLE) {
            String phrase = StepIntentBinder.extractAssertTextPhrase(intent.text());
            if (phrase != null && !phrase.isBlank()) {
                String xpath = StepIntentBinder.xpathContainsText(phrase);
                LocatorValidator.ValidationResult vr = validator.validate(
                        new LocatorCandidate("xpath", xpath, "Page", ""));
                return List.of(new ProvenStep(
                        tcId, "Page", "elementAction", "assert",
                        "xpath", xpath, "",
                        "textContains", phrase, vr.valid(),
                        vr.valid() ? "heal:textContains:" + phrase : vr.reason()));
            }
        }
        StepIntentBinder.BindResult preferred =
                StepIntentBinder.bindSingle(intent, tcId, candidates, List.of(candidateId));
        if (preferred.ok() && !rejectsNamedActionMismatch(intent, preferred.steps(), candidates)) {
            return preferred.steps();
        }
        // Force locator from chosen candidate when binder still ambiguous —
        // but never swap named-entity clicks onto a different control type.
        DomCandidate chosen = DomCandidateExtractor.findById(candidates, candidateId);
        if (chosen == null) {
            return List.of(rejectStep(tcId, "candidateId not in table: " + candidateId));
        }
        if (intent.kind() == StepIntentBinder.IntentKind.CLICK
                && !StepIntentBinder.candidateCarriesDistinctiveTokens(intent.text(), chosen, candidates)) {
            return List.of(rejectStep(tcId,
                    "heal rejected: candidate lacks distinctive tokens for intent: " + intent.text()));
        }
        if (intent.kind() == StepIntentBinder.IntentKind.CLICK
                && StepIntentBinder.intentRequiresNamedActionControl(intent.text())
                && !StepIntentBinder.candidateMatchesNamedAction(intent.text(), chosen, candidates)) {
            return List.of(rejectStep(tcId,
                    "heal rejected: named-action intent requires an action control, got: "
                            + chosen.id() + " " + chosen.value()));
        }
        String action = intent.kind() == StepIntentBinder.IntentKind.ASSERT_VISIBLE ? "assert" : "click";
        if (intent.kind() == StepIntentBinder.IntentKind.TYPE_USER
                || intent.kind() == StepIntentBinder.IntentKind.TYPE_PASS
                || intent.kind() == StepIntentBinder.IntentKind.TYPE_FIELD) {
            action = "type";
        }
        String assertType = "";
        String assertExpected = "";
        String value = "";
        if (intent.kind() == StepIntentBinder.IntentKind.ASSERT_VISIBLE) {
            assertType = "visible";
            assertExpected = "";
        }
        return List.of(new ProvenStep(
                tcId, "Page", "elementAction", action,
                chosen.strategy(), chosen.value(), value,
                assertType, assertExpected, true,
                "heal:candidate:" + chosen.id()));
    }

    /** Discard binds that name an action+entity but landed on a non-action control. */
    private static boolean rejectsNamedActionMismatch(
            StepIntentBinder.IntentLine intent,
            List<ProvenStep> steps,
            List<DomCandidate> candidates) {
        if (intent == null || steps == null || steps.isEmpty()) {
            return false;
        }
        if (intent.kind() != StepIntentBinder.IntentKind.CLICK
                || !StepIntentBinder.intentRequiresNamedActionControl(intent.text())) {
            return false;
        }
        ProvenStep step = steps.get(0);
        DomCandidate bound = candidates == null ? null : candidates.stream()
                .filter(c -> c.strategy().equalsIgnoreCase(step.locatorStrategy())
                        && c.value().equals(step.locatorValue()))
                .findFirst()
                .orElse(null);
        if (bound != null) {
            return !StepIntentBinder.candidateMatchesNamedAction(intent.text(), bound, candidates);
        }
        String loc = step.locatorValue();
        if (loc == null) {
            return true;
        }
        String lower = loc.toLowerCase(Locale.ROOT);
        return StepIntentBinder.intentActionVerbs(intent.text()).stream().noneMatch(lower::contains);
    }

    private List<ProvenStep> resolveAmbiguousOrFallback(
            ManualTestCase tc,
            List<DomCandidate> candidates,
            String ambiguousReason,
            byte[] pngOrNull
    ) throws Exception {
        List<ProvenStep> resolved = resolveAmbiguousWithLlm(tc, candidates, ambiguousReason, pngOrNull);
        if (!resolved.isEmpty() && resolved.stream().allMatch(ProvenStep::validated)) {
            return resolved;
        }
        // Deterministic fallback: prefer first shortlist id (highest binder score)
        String[] parts = ambiguousReason.split(":", 3);
        if (parts.length >= 3) {
            String firstId = parts[2].split(",")[0].trim();
            if (!firstId.isBlank()) {
                StepIntentBinder.BindResult preferred =
                        StepIntentBinder.bindPreferring(tc, candidates, List.of(firstId));
                if (preferred.ok()) {
                    return preferred.steps();
                }
            }
        }
        if (!resolved.isEmpty()) {
            return resolved;
        }
        return List.of(rejectStep(tc.tcId(), ambiguousReason));
    }

    /**
     * Author login prelude steps against login-page HTML candidates only.
     */
    public List<ProvenStep> authorLoginPrelude(ManualTestCase tc, String loginHtml) throws Exception {
        List<DomCandidate> candidates = DomCandidateExtractor.extract(loginHtml);
        List<ProvenStep> heuristic = heuristicLoginSteps(tc.tcId(), candidates);
        if (!heuristic.isEmpty()) {
            return heuristic;
        }
        ManualTestCase loginTc = new ManualTestCase(
                tc.tcId(),
                "Login",
                "",
                """
                        1. Enter username into username field
                        2. Enter password into password field
                        3. Click login / sign-in button
                        """,
                "User is logged in",
                tc.priority(),
                tc.tags()
        );
        return authorWithCandidates(
                loginTc, loginHtml, candidates, LocatorPolicy.loginPreludeRules(), true);
    }

    private List<ProvenStep> resolveAmbiguousWithLlm(
            ManualTestCase tc,
            List<DomCandidate> candidates,
            String ambiguousReason,
            byte[] pngOrNull
    ) throws Exception {
        // AMBIGUOUS:KIND:id1,id2,id3
        String[] parts = ambiguousReason.split(":", 3);
        if (parts.length < 3) {
            return List.of(rejectStep(tc.tcId(), ambiguousReason));
        }
        List<String> shortlistIds = Arrays.stream(parts[2].split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toList());
        List<DomCandidate> shortlist = candidates.stream()
                .filter(c -> shortlistIds.stream().anyMatch(id -> id.equalsIgnoreCase(c.id())))
                .toList();
        if (shortlist.isEmpty()) {
            return List.of(rejectStep(tc.tcId(), "Ambiguous intent with empty shortlist"));
        }
        boolean hasImage = pngOrNull != null && pngOrNull.length > 0;
        String system = hasImage
                ? LocatorPolicy.visionHealRules()
                : """
                Pick exactly one candidateId from the shortlist that best matches the Excel step intent.
                Return ONLY JSON: {"candidateId":"<id from shortlist>"}.
                Do not invent ids. Do not return locators.
                """;
        String user = """
                TC_ID: %s
                Intent hint: %s
                Steps:
                %s
                ExpectedResult:
                %s

                Shortlist:
                %s
                %s
                """.formatted(
                tc.tcId(),
                parts[1],
                tc.steps(),
                tc.expectedResult(),
                DomCandidateExtractor.formatTable(shortlist),
                hasImage ? "(Screenshot of current page is attached as an image.)" : ""
        );
        try {
            String raw = hasImage
                    ? llmClient.completeJson(system, user, pngOrNull)
                    : llmClient.completeJson(system, user);
            return preferCandidate(tc, candidates, shortlist, raw, ambiguousReason);
        } catch (Exception e) {
            // Vision model missing / reject image — fall back to text-only once
            if (hasImage) {
                try {
                    String raw = llmClient.completeJson("""
                            Pick exactly one candidateId from the shortlist that best matches the Excel step intent.
                            Return ONLY JSON: {"candidateId":"<id from shortlist>"}.
                            """, user);
                    return preferCandidate(tc, candidates, shortlist, raw, ambiguousReason);
                } catch (Exception ignored) {
                    return List.of();
                }
            }
            return List.of();
        }
    }

    private List<ProvenStep> healBindFailureWithVision(
            ManualTestCase tc,
            List<DomCandidate> candidates,
            String reason,
            byte[] pngOrNull
    ) {
        if (pngOrNull == null || pngOrNull.length == 0 || candidates.isEmpty()) {
            return List.of();
        }
        List<DomCandidate> shortlist = candidates.stream().limit(12).toList();
        String user = """
                Bind failed: %s
                Excel steps:
                %s
                Pick the best candidateId from the shortlist for the visible UI.
                Shortlist:
                %s
                """.formatted(reason, tc.steps(), DomCandidateExtractor.formatTable(shortlist));
        try {
            String raw = llmClient.completeJson(LocatorPolicy.visionHealRules(), user, pngOrNull);
            return preferCandidate(tc, candidates, shortlist, raw, reason);
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<ProvenStep> preferCandidate(
            ManualTestCase tc,
            List<DomCandidate> candidates,
            List<DomCandidate> shortlist,
            String raw,
            String fallbackReason
    ) {
        String chosenId = parseCandidateId(raw);
        if (chosenId.isBlank() || shortlist.stream().noneMatch(c -> c.id().equalsIgnoreCase(chosenId))) {
            return List.of(rejectStep(tc.tcId(),
                    "LLM shortlist pick invalid: " + chosenId + " reason=" + fallbackReason));
        }
        StepIntentBinder.BindResult preferred =
                StepIntentBinder.bindPreferring(tc, candidates, List.of(chosenId));
        if (preferred.ok()) {
            return preferred.steps();
        }
        return List.of(rejectStep(tc.tcId(),
                preferred.rejectReason() == null || preferred.rejectReason().isBlank()
                        ? fallbackReason
                        : preferred.rejectReason()));
    }

    private static byte[] readPng(Path screenshotOrNull) {
        if (screenshotOrNull == null) {
            return new byte[0];
        }
        try {
            return java.nio.file.Files.readAllBytes(screenshotOrNull);
        } catch (Exception e) {
            return new byte[0];
        }
    }

    private static String parseCandidateId(String raw) {
        try {
            String text = raw == null ? "" : LocalLlmClient.stripThinkingWrappers(raw);
            int start = text.lastIndexOf('{');
            int end = text.lastIndexOf('}');
            if (start < 0 || end <= start) {
                return "";
            }
            JSONObject root = new JSONObject(text.substring(start, end + 1));
            return root.optString("candidateId", "").trim();
        } catch (RuntimeException e) {
            return "";
        }
    }

    private static String formatPriorSteps(List<String> priorStepSummaries) {
        if (priorStepSummaries == null || priorStepSummaries.isEmpty()) {
            return "(none)";
        }
        return priorStepSummaries.stream()
                .filter(s -> s != null && !s.isBlank())
                .limit(12)
                .map(s -> "- " + (s.length() > 240 ? s.substring(0, 240) + "..." : s))
                .collect(Collectors.joining("\n"));
    }

    private static ProvenStep rejectStep(String tcId, String reason) {
        return new ProvenStep(tcId, "Page", "elementAction", "click",
                "", "", "", "", "", false, reason);
    }

    private List<ProvenStep> authorWithCandidates(
            ManualTestCase tc,
            String slimHtml,
            List<DomCandidate> candidates,
            String systemRules,
            boolean loginMode
    ) throws Exception {
        String system = systemRules;
        String user = """
                TC_ID: %s
                Title: %s
                Preconditions: %s
                Steps:
                %s
                ExpectedResult:
                %s

                DOM candidates (pick candidateId ONLY from this table):
                id | strategy | value | tag | label
                %s
                """.formatted(
                tc.tcId(),
                tc.title(),
                tc.preconditions(),
                tc.steps(),
                tc.expectedResult(),
                DomCandidateExtractor.formatTable(candidates)
        );

        String raw = llmClient.completeJson(system, user);
        List<ProvenStep> steps;
        try {
            steps = parseSteps(tc.tcId(), raw, candidates, loginMode);
        } catch (RuntimeException parseError) {
            String repairUser = user + "\n\nREPAIR: prior response was not valid JSON ("
                    + parseError.getMessage()
                    + "). Return ONLY JSON with steps; each locator step needs candidateId from the table.";
            String repaired = llmClient.completeJson(system, repairUser);
            steps = parseSteps(tc.tcId(), repaired, candidates, loginMode);
        }
        if (hasInvalidLocator(steps)) {
            String repairUser = user + "\n\nREPAIR: invalid steps:\n" + invalidSummary(steps)
                    + "\nPick candidateId values ONLY from the table. Do not invent locators.";
            String repaired = llmClient.completeJson(system, repairUser);
            steps = parseSteps(tc.tcId(), repaired, candidates, loginMode);
        }
        return steps;
    }

    private static List<ProvenStep> heuristicLoginSteps(String tcId, List<DomCandidate> candidates) {
        DomCandidate user = findLoginField(candidates, "username", "user-name", "user");
        DomCandidate pass = findLoginField(candidates, "password");
        DomCandidate login = findLoginField(candidates, "login-button", "submit", "sign-in", "signin", "login");
        if (user == null || pass == null || login == null) {
            return List.of();
        }
        List<ProvenStep> steps = new ArrayList<>();
        steps.add(step(tcId, "Page", "type", user, "${TARGET_USERNAME}", true));
        steps.add(step(tcId, "Page", "type", pass, "${TARGET_PASSWORD}", true));
        steps.add(step(tcId, "Page", "click", login, "", true));
        return steps;
    }

    private static DomCandidate findLoginField(List<DomCandidate> candidates, String... needles) {
        for (String n : needles) {
            String needle = n.toLowerCase(Locale.ROOT);
            DomCandidate best = null;
            int bestRank = -1;
            for (DomCandidate c : candidates) {
                if (!DomCandidateExtractor.isBindableStrategy(c.strategy())) {
                    continue;
                }
                String hay = (c.value() + " " + c.label()).toLowerCase(Locale.ROOT);
                if (hay.equals(needle) || hay.contains(needle)) {
                    int rank = DomCandidateExtractor.strategyRank(c.strategy());
                    if (rank > bestRank) {
                        bestRank = rank;
                        best = c;
                    }
                }
            }
            if (best != null) {
                return best;
            }
        }
        return null;
    }

    private static ProvenStep step(String tcId, String page, String action, DomCandidate c,
                                   String value, boolean validated) {
        return new ProvenStep(
                tcId, page, "elementAction", action,
                c.strategy(), c.value(), value,
                "", "", validated, "heuristic:" + c.id()
        );
    }

    private static boolean hasInvalidLocator(List<ProvenStep> steps) {
        for (ProvenStep step : steps) {
            if (!step.validated()) {
                return true;
            }
        }
        return false;
    }

    private static String invalidSummary(List<ProvenStep> steps) {
        StringBuilder sb = new StringBuilder();
        for (ProvenStep step : steps) {
            if (!step.validated()) {
                sb.append("- action=").append(step.action())
                        .append(" strategy=").append(step.locatorStrategy())
                        .append(" value=").append(step.locatorValue())
                        .append(" reason=").append(step.rationale())
                        .append('\n');
            }
        }
        return sb.toString();
    }

    List<ProvenStep> parseSteps(String tcId, String rawJson) {
        return parseSteps(tcId, rawJson, List.of(), false);
    }

    List<ProvenStep> parseSteps(String tcId, String rawJson, List<DomCandidate> candidates, boolean loginMode) {
        JSONObject root = new JSONObject(extractJsonObject(rawJson));
        JSONArray steps = root.optJSONArray("steps");
        List<ProvenStep> result = new ArrayList<>();
        if (steps == null) {
            return result;
        }
        for (int i = 0; i < steps.length(); i++) {
            JSONObject step = steps.getJSONObject(i);
            String actionType = step.optString("actionType", "elementAction");
            String action = step.optString("action", "");
            String candidateId = step.optString("candidateId", "");
            String strategy = step.optString("locatorStrategy", "");
            String value = step.optString("locatorValue", "");
            boolean needsLocator = !"browserAction".equals(actionType);

            DomCandidate bound = null;
            boolean validated = true;
            String rationale = step.optString("rationale", "");

            if (needsLocator) {
                if (!candidateId.isBlank()) {
                    bound = DomCandidateExtractor.findById(candidates, candidateId);
                }
                if (bound == null && !strategy.isBlank() && !value.isBlank()) {
                    bound = DomCandidateExtractor.findByStrategyValue(candidates, strategy, value);
                }
                if (bound == null) {
                    validated = false;
                    rationale = "locator must reference a DOM candidateId from the table";
                } else {
                    strategy = bound.strategy();
                    value = bound.value();
                    LocatorCandidate lc = new LocatorCandidate(strategy, value,
                            step.optString("pageName", "Page"), rationale);
                    LocatorValidator.ValidationResult vr = validator.validate(lc);
                    if (!vr.valid()) {
                        validated = false;
                        rationale = vr.reason();
                    } else {
                        rationale = rationale.isBlank() ? "candidate:" + bound.id() : rationale;
                    }
                }
            }

            String typed = step.optString("value", "");
            if (loginMode && "type".equalsIgnoreCase(action) && bound != null) {
                String hay = bound.value().toLowerCase(Locale.ROOT);
                if (hay.contains("user") || hay.contains("email")) {
                    typed = "${TARGET_USERNAME}";
                } else if (hay.contains("pass")) {
                    typed = "${TARGET_PASSWORD}";
                }
            }

            result.add(new ProvenStep(
                    tcId,
                    step.optString("pageName", "Page"),
                    actionType,
                    action,
                    strategy,
                    value,
                    typed,
                    step.optString("assertionType", ""),
                    step.optString("assertionExpected", ""),
                    validated,
                    rationale
            ));
        }
        return result;
    }

    private static String extractJsonObject(String raw) {
        if (raw == null) {
            return "{}";
        }
        String trimmed = LocalLlmClient.stripThinkingWrappers(raw);
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        int arrStart = trimmed.indexOf('[');
        int arrEnd = trimmed.lastIndexOf(']');
        if (arrStart >= 0 && arrEnd > arrStart) {
            return "{\"steps\":" + trimmed.substring(arrStart, arrEnd + 1) + "}";
        }
        throw new IllegalArgumentException("LLM response did not contain a JSON object");
    }
}
