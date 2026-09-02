package delivery.heal;

import delivery.authoring.HtmlLocatorPresence;
import delivery.authoring.LocatorCandidate;
import delivery.authoring.LocatorPolicy;
import delivery.authoring.LocatorValidator;
import delivery.authoring.StepIntentBinder;
import delivery.codegen.ProvenStep;
import delivery.revise.AgentRouterClient;
import org.json.JSONArray;
import org.json.JSONObject;
import utils.LogsManager;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Validated, one-shot locator invention used only after shortlist healing is exhausted. */
public class FreeInventHealer {
    private static final int MAX_STEPS = 3;
    private static final int MAX_HTML_CHARS = 16_000;

    private final CursorHealClient cursor;
    private final AgentRouterClient agentRouter;
    private final LocatorValidator validator;
    private final RecoveryPlanParser recoveryParser;
    private final String provider;
    private final boolean enabled;

    public FreeInventHealer(
            CursorHealClient cursor,
            AgentRouterClient agentRouter,
            LocatorValidator validator,
            String provider,
            boolean enabled
    ) {
        this.cursor = cursor == null ? new CursorHealClient() : cursor;
        this.agentRouter = agentRouter;
        this.validator = validator == null ? new LocatorValidator() : validator;
        this.recoveryParser = new RecoveryPlanParser(this.validator);
        this.provider = normalizeProvider(provider);
        this.enabled = enabled;
    }

    public static FreeInventHealer fromConfig(CursorHealClient cursor) {
        String provider = property("delivery.heal.invent.provider", "cursor");
        boolean enabled = booleanProperty("delivery.heal.invent.enabled", true);
        AgentRouterClient router = "agentrouter".equalsIgnoreCase(provider)
                ? AgentRouterClient.fromInventConfigOrNull()
                : null;
        return new FreeInventHealer(cursor, router, new LocatorValidator(), provider, enabled);
    }

    public Optional<List<ProvenStep>> invent(
            String tcId,
            StepIntentBinder.IntentLine intent,
            String slimHtml,
            byte[] pngOrNull,
            String failureReason,
            List<String> priorStepSummaries,
            String whyInvoked,
            Path screenshotPathOrNull
    ) {
        return invent(tcId, intent, slimHtml, pngOrNull, failureReason, priorStepSummaries,
                whyInvoked, screenshotPathOrNull, null);
    }

    public Optional<List<ProvenStep>> invent(
            String tcId,
            StepIntentBinder.IntentLine intent,
            String slimHtml,
            byte[] pngOrNull,
            String failureReason,
            List<String> priorStepSummaries,
            String whyInvoked,
            Path screenshotPathOrNull,
            String allowedOpenPath
    ) {
        if (!enabled || intent == null) {
            return Optional.empty();
        }
        boolean allowNavigate = allowedOpenPath != null && !allowedOpenPath.isBlank();
        if ((slimHtml == null || slimHtml.isBlank()) && !allowNavigate) {
            LogsManager.info("HEAL_INVENT_SKIPPED: empty_html");
            return Optional.empty();
        }
        List<String> history = sanitizeHistory(priorStepSummaries);
        List<String> vision = delivery.vision.VisionAttemptLog.linesForHeal();
        if (!vision.isEmpty()) {
            List<String> merged = new ArrayList<>(history);
            merged.add("## Vision attempts this intent");
            merged.addAll(vision);
            history = List.copyOf(merged);
        }
        String html = trim(slimHtml, MAX_HTML_CHARS);
        String reason = (failureReason == null ? "" : failureReason)
                + " [whyInvoked=" + (whyInvoked == null ? "" : whyInvoked) + "]";
        if ((pngOrNull == null || pngOrNull.length == 0) && screenshotPathOrNull == null) {
            LogsManager.info("HEAL_INVENT_NO_SCREENSHOT: " + tcId);
        }

        String raw;
        try {
            if ("agentrouter".equals(provider)) {
                if (agentRouter == null) {
                    LogsManager.warn("HEAL_INVENT_SKIPPED: AgentRouter is not configured");
                    return Optional.empty();
                }
                raw = agentRouter.completeJson(
                        LocatorPolicy.freeInventRules(),
                        inventPrompt(intent, reason, history, html,
                                pngOrNull != null && pngOrNull.length > 0),
                        pngOrNull);
            } else {
                raw = cursor.inventSteps(
                        intent.text(), reason, history, html, screenshotPathOrNull, allowedOpenPath);
            }
        } catch (Exception e) {
            LogsManager.warn("HEAL_INVENT_FAILED: " + e.getMessage());
            return Optional.empty();
        }

        Optional<HealResult> healed = parseInventResponse(tcId, intent, raw, html, allowedOpenPath);
        if (healed.isEmpty()) {
            LogsManager.info("HEAL_INVENT_REJECTED: no valid intent-matching steps");
            return Optional.empty();
        }
        LogsManager.info("HEAL_INVENT: resolved " + tcId + " provider=" + provider
                + " tier=" + healed.get().tierUsed()
                + " steps=" + healed.get().steps().size());
        LAST_INVENT_RESULT.set(healed.get());
        return Optional.of(healed.get().steps());
    }

    /** Last invent call result (includes recovery metadata when tier is recovery). */
    private static final ThreadLocal<HealResult> LAST_INVENT_RESULT = new ThreadLocal<>();

    public static Optional<HealResult> lastInventResult() {
        HealResult result = LAST_INVENT_RESULT.get();
        return result == null ? Optional.empty() : Optional.of(result);
    }

    public static void clearLastInventResult() {
        LAST_INVENT_RESULT.remove();
    }

    /**
     * Full invent parse: recovery mode or classic steps array.
     */
    public Optional<HealResult> inventHealResult(
            String tcId,
            StepIntentBinder.IntentLine intent,
            String slimHtml,
            byte[] pngOrNull,
            String failureReason,
            List<String> priorStepSummaries,
            String whyInvoked,
            Path screenshotPathOrNull,
            String allowedOpenPath
    ) {
        LAST_INVENT_RESULT.remove();
        if (!enabled || intent == null) {
            return Optional.empty();
        }
        boolean allowNavigate = allowedOpenPath != null && !allowedOpenPath.isBlank();
        if ((slimHtml == null || slimHtml.isBlank()) && !allowNavigate) {
            LogsManager.info("HEAL_INVENT_SKIPPED: empty_html");
            return Optional.empty();
        }
        List<String> history = sanitizeHistory(priorStepSummaries);
        List<String> vision = delivery.vision.VisionAttemptLog.linesForHeal();
        if (!vision.isEmpty()) {
            List<String> merged = new ArrayList<>(history);
            merged.add("## Vision attempts this intent");
            merged.addAll(vision);
            history = List.copyOf(merged);
        }
        String html = trim(slimHtml, MAX_HTML_CHARS);
        String reason = (failureReason == null ? "" : failureReason)
                + " [whyInvoked=" + (whyInvoked == null ? "" : whyInvoked) + "]";
        if ((pngOrNull == null || pngOrNull.length == 0) && screenshotPathOrNull == null) {
            LogsManager.info("HEAL_INVENT_NO_SCREENSHOT: " + tcId);
        }

        String raw;
        try {
            if ("agentrouter".equals(provider)) {
                if (agentRouter == null) {
                    LogsManager.warn("HEAL_INVENT_SKIPPED: AgentRouter is not configured");
                    return Optional.empty();
                }
                raw = agentRouter.completeJson(
                        LocatorPolicy.freeInventRules(),
                        inventPrompt(intent, reason, history, html,
                                pngOrNull != null && pngOrNull.length > 0),
                        pngOrNull);
            } else {
                raw = cursor.inventSteps(
                        intent.text(), reason, history, html, screenshotPathOrNull, allowedOpenPath);
            }
        } catch (Exception e) {
            LogsManager.warn("HEAL_INVENT_FAILED: " + e.getMessage());
            return Optional.empty();
        }
        Optional<HealResult> healed = parseInventResponse(tcId, intent, raw, html, allowedOpenPath);
        healed.ifPresent(LAST_INVENT_RESULT::set);
        return healed;
    }

    public Optional<HealResult> parseInventResponse(
            String tcId,
            StepIntentBinder.IntentLine intent,
            String raw,
            String slimHtml,
            String allowedOpenPath
    ) {
        Optional<RecoveryPlanParser.RecoveryPlan> recovery =
                recoveryParser.parse(tcId, raw, slimHtml);
        if (recovery.isPresent()) {
            RecoveryPlanParser.RecoveryPlan plan = recovery.get();
            LogsManager.info("HEAL_RECOVERY: parsed " + plan.steps().size() + " steps for " + tcId);
            return Optional.of(HealResult.recovery(
                    plan.steps(), plan.automationNotes(), plan.thought()));
        }
        List<ProvenStep> steps = parseClassicSteps(tcId, intent, raw, slimHtml, allowedOpenPath);
        if (steps.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(HealResult.success(steps, "invent"));
    }

    /**
     * Same gate for any locator a model writes, whichever call produced it: shape allowlist,
     * presence in the page, and an action that matches the intent.
     */
    public List<ProvenStep> validateWrittenSteps(
            String tcId, StepIntentBinder.IntentLine intent, String raw, String slimHtml) {
        return validateWrittenSteps(tcId, intent, raw, slimHtml, null);
    }

    public List<ProvenStep> validateWrittenSteps(
            String tcId, StepIntentBinder.IntentLine intent, String raw, String slimHtml,
            String allowedOpenPath) {
        if (intent == null || raw == null || raw.isBlank()) {
            return List.of();
        }
        return parseClassicSteps(tcId, intent, raw, slimHtml, allowedOpenPath);
    }

    private List<ProvenStep> parseClassicSteps(
            String tcId, StepIntentBinder.IntentLine intent, String raw, String slimHtml) {
        return parseClassicSteps(tcId, intent, raw, slimHtml, null);
    }

    private List<ProvenStep> parseClassicSteps(
            String tcId, StepIntentBinder.IntentLine intent, String raw, String slimHtml,
            String allowedOpenPath) {
        JSONObject root;
        try {
            String text = raw == null ? "" : raw.trim();
            int start = text.indexOf('{');
            int end = text.lastIndexOf('}');
            if (start < 0 || end <= start) {
                return List.of();
            }
            root = new JSONObject(text.substring(start, end + 1));
        } catch (RuntimeException e) {
            return List.of();
        }
        JSONArray items = root.optJSONArray("steps");
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        List<ProvenStep> result = new ArrayList<>();
        for (int i = 0; i < items.length() && result.size() < MAX_STEPS; i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) {
                continue;
            }
            String action = item.optString("action", "").trim().toLowerCase(Locale.ROOT);
            if (isNavigate(action)) {
                String requested = item.optString("value", "").trim();
                if (!delivery.job.ExcelPathNavigator.isAllowed(requested, allowedOpenPath)) {
                    LogsManager.info("HEAL_INVENT_REJECTED: navigate not on Excel open-path → " + requested);
                    continue;
                }
                result.add(new ProvenStep(
                        tcId, "Page", "browserAction", "navigate",
                        "", "", requested, "", "", true, "heal:invent:navigate"));
                continue;
            }
            if (!actionMatches(intent.kind(), action)) {
                continue;
            }
            String strategy = item.optString("locatorStrategy", "").trim();
            String locatorValue = item.optString("locatorValue", "").trim();
            LocatorValidator.ValidationResult validation = validator.validate(
                    new LocatorCandidate(strategy, locatorValue, "Page", "free-invent"));
            if (!validation.valid()) {
                continue;
            }
            if (!HtmlLocatorPresence.present(strategy, locatorValue, slimHtml)) {
                LogsManager.info("HEAL_INVENT_REJECTED: locator absent from page → "
                        + strategy + "=" + locatorValue);
                continue;
            }
            result.add(new ProvenStep(
                    tcId, "Page", "elementAction", action, strategy, locatorValue,
                    item.optString("value", ""),
                    item.optString("assertionType", ""),
                    item.optString("assertionExpected", ""),
                    true, "heal:invent:" + provider));
        }
        return result;
    }

    private static boolean actionMatches(StepIntentBinder.IntentKind kind, String action) {
        return switch (kind) {
            case TYPE_USER, TYPE_PASS, TYPE_FIELD -> "type".equals(action);
            case ASSERT_VISIBLE -> "assert".equals(action);
            case CLICK_LOGIN, CLICK -> "click".equals(action);
        };
    }

    private static boolean isNavigate(String action) {
        return "navigate".equals(action) || "open".equals(action) || "goto".equals(action)
                || "go".equals(action);
    }

    private static String inventPrompt(
            StepIntentBinder.IntentLine intent,
            String failureReason,
            List<String> history,
            String html,
            boolean screenshotAttached
    ) {
        return """
                Excel intent:
                Kind: %s
                Text: %s

                Failure reason:
                %s

                ## Already completed in this TC
                %s

                Slim HTML excerpt:
                %s

                Screenshot:
                %s
                """.formatted(
                intent.kind(), intent.text(), failureReason,
                history.isEmpty() ? "(none)" : String.join("\n", history),
                html, screenshotAttached ? "Attached as PNG." : "Not available.");
    }

    private static List<String> sanitizeHistory(List<String> priorSteps) {
        if (priorSteps == null) {
            return List.of();
        }
        return priorSteps.stream()
                .filter(s -> s != null && !s.isBlank())
                .limit(12)
                .map(s -> trim(s, 240))
                .toList();
    }

    private static String normalizeProvider(String provider) {
        String normalized = provider == null ? "cursor" : provider.trim().toLowerCase(Locale.ROOT);
        return "agentrouter".equals(normalized) ? normalized : "cursor";
    }

    private static boolean booleanProperty(String key, boolean defaultValue) {
        String value = property(key, Boolean.toString(defaultValue));
        return "true".equalsIgnoreCase(value) || "1".equals(value);
    }

    private static String property(String key, String defaultValue) {
        String value = System.getProperty(key);
        if (value == null || value.isBlank()) {
            value = utils.PropertyReader.getProperty(key);
        }
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private static String trim(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max) + "...";
    }
}
