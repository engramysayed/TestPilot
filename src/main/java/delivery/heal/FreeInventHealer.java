package delivery.heal;

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
        if (!enabled || intent == null) {
            return Optional.empty();
        }
        if (slimHtml == null || slimHtml.isBlank()) {
            LogsManager.info("HEAL_INVENT_SKIPPED: empty_html");
            return Optional.empty();
        }
        List<String> history = sanitizeHistory(priorStepSummaries);
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
                        intent.text(), reason, history, html, screenshotPathOrNull);
            }
        } catch (Exception e) {
            LogsManager.warn("HEAL_INVENT_FAILED: " + e.getMessage());
            return Optional.empty();
        }

        List<ProvenStep> steps = parseAndValidate(tcId, intent, raw);
        if (steps.isEmpty()) {
            LogsManager.info("HEAL_INVENT_REJECTED: no valid intent-matching steps");
            return Optional.empty();
        }
        LogsManager.info("HEAL_INVENT: resolved " + tcId + " provider=" + provider
                + " steps=" + steps.size());
        return Optional.of(steps);
    }

    private List<ProvenStep> parseAndValidate(
            String tcId, StepIntentBinder.IntentLine intent, String raw) {
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
