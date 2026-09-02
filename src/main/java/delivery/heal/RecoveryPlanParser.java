package delivery.heal;

import delivery.authoring.HtmlLocatorPresence;
import delivery.authoring.LocatorCandidate;
import delivery.authoring.LocatorValidator;
import delivery.codegen.ProvenStep;
import delivery.job.ExcelPathNavigator;
import org.json.JSONArray;
import org.json.JSONObject;
import utils.LogsManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Validates structured recovery JSON from invent/solve when page state mismatches intent. */
public final class RecoveryPlanParser {
    private static final int MAX_STEPS = 5;
    private static final Set<String> ALLOWED_ACTIONS = Set.of("clear", "click", "type", "select", "navigate");

    private final LocatorValidator validator;

    public RecoveryPlanParser() {
        this(new LocatorValidator());
    }

    public RecoveryPlanParser(LocatorValidator validator) {
        this.validator = validator == null ? new LocatorValidator() : validator;
    }

    public record RecoveryPlan(
            List<ProvenStep> steps,
            List<String> automationNotes,
            String thought
    ) {
    }

    public java.util.Optional<RecoveryPlan> parse(String tcId, String raw, String slimHtml) {
        return parse(tcId, raw, slimHtml, null, null);
    }

    public java.util.Optional<RecoveryPlan> parse(String tcId, String raw, String slimHtml, String fullHtml) {
        return parse(tcId, raw, slimHtml, fullHtml, null);
    }

    /**
     * @param fullHtml optional fuller page HTML when slim dropped attributes
     * @param allowedOpenPath Excel open-path; required for any navigate recovery step
     */
    public java.util.Optional<RecoveryPlan> parse(
            String tcId, String raw, String slimHtml, String fullHtml, String allowedOpenPath) {
        if (raw == null || raw.isBlank()) {
            return java.util.Optional.empty();
        }
        JSONObject root = extractRoot(raw);
        if (root == null || !"recovery".equalsIgnoreCase(root.optString("mode", "").trim())) {
            return java.util.Optional.empty();
        }
        JSONArray items = root.optJSONArray("recoverySteps");
        if (items == null || items.isEmpty()) {
            LogsManager.info("HEAL_RECOVERY_REJECTED: recoverySteps empty");
            return java.util.Optional.empty();
        }
        List<ProvenStep> steps = new ArrayList<>();
        for (int i = 0; i < items.length(); i++) {
            if (i >= MAX_STEPS) {
                LogsManager.info("HEAL_RECOVERY_REJECTED: more than " + MAX_STEPS + " recovery steps");
                return java.util.Optional.empty();
            }
            JSONObject item = items.optJSONObject(i);
            if (item == null) {
                LogsManager.info("HEAL_RECOVERY_REJECTED: non-object recovery step at index " + i);
                return java.util.Optional.empty();
            }
            ProvenStep step = validateStep(tcId, item, slimHtml, fullHtml, allowedOpenPath);
            if (step == null) {
                LogsManager.info("HEAL_RECOVERY_REJECTED: invalid step at index " + i + " (all-or-nothing)");
                return java.util.Optional.empty();
            }
            steps.add(step);
        }
        if (steps.isEmpty()) {
            LogsManager.info("HEAL_RECOVERY_REJECTED: no valid recovery steps");
            return java.util.Optional.empty();
        }
        List<String> notes = parseNotes(root.optJSONArray("automationNotes"));
        String thought = root.optString("thought", "").trim();
        return java.util.Optional.of(new RecoveryPlan(steps, notes, thought));
    }

    private ProvenStep validateStep(
            String tcId, JSONObject item, String slimHtml, String fullHtml, String allowedOpenPath) {
        String action = item.optString("action", "").trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED_ACTIONS.contains(action)) {
            LogsManager.info("HEAL_RECOVERY_REJECTED: disallowed action → " + action);
            return null;
        }
        if ("navigate".equals(action)) {
            String target = item.optString("value", "").trim();
            if (target.isBlank()) {
                target = item.optString("locatorValue", "").trim();
            }
            if (!ExcelPathNavigator.isAllowed(target, allowedOpenPath)) {
                LogsManager.info("HEAL_RECOVERY_REJECTED: navigate not on Excel open-path → " + target);
                return null;
            }
            return new ProvenStep(
                    tcId, "Page", "browserAction", "navigate", "", "",
                    target, "", "", true, "heal:recovery");
        }
        String strategy = firstNonBlank(item, "locatorStrategy", "strategy");
        String locatorValue = item.optString("locatorValue", "").trim();
        String stepValue = "";
        if ("type".equals(action) || "select".equals(action)) {
            stepValue = item.optString("value", "");
            if (locatorValue.isBlank() && !strategy.isBlank()) {
                locatorValue = item.optString("targetHint", "").trim();
            }
        } else if (locatorValue.isBlank()) {
            locatorValue = item.optString("value", "").trim();
        }
        if (locatorValue.isBlank()) {
            LogsManager.info("HEAL_RECOVERY_REJECTED: missing locator for " + action);
            return null;
        }
        LocatorValidator.ValidationResult validation = validator.validate(
                new LocatorCandidate(strategy, locatorValue, "Page", "heal-recovery"));
        if (!validation.valid()) {
            LogsManager.info("HEAL_RECOVERY_REJECTED: invalid locator → "
                    + strategy + "=" + locatorValue);
            return null;
        }
        if (!locatorPresent(strategy, locatorValue, slimHtml, fullHtml)) {
            LogsManager.info("HEAL_RECOVERY_REJECTED: locator absent from page → "
                    + strategy + "=" + locatorValue);
            return null;
        }
        return new ProvenStep(
                tcId, "Page", "elementAction", action, strategy, locatorValue,
                stepValue, "", "", true, "heal:recovery");
    }

    private static boolean locatorPresent(
            String strategy, String locatorValue, String slimHtml, String fullHtml) {
        boolean inSlim = slimHtml != null && !slimHtml.isBlank()
                && HtmlLocatorPresence.present(strategy, locatorValue, slimHtml);
        if (inSlim) {
            return true;
        }
        if (slimHtml == null || slimHtml.isBlank()) {
            return fullHtml == null || fullHtml.isBlank()
                    || HtmlLocatorPresence.present(strategy, locatorValue, fullHtml);
        }
        if (fullHtml != null && !fullHtml.isBlank()
                && HtmlLocatorPresence.present(strategy, locatorValue, fullHtml)) {
            LogsManager.info("HEAL_RECOVERY_SLIM_MISS: accepted via full HTML → "
                    + strategy + "=" + locatorValue);
            return true;
        }
        return false;
    }

    private static List<String> parseNotes(JSONArray arr) {
        if (arr == null || arr.isEmpty()) {
            return List.of();
        }
        List<String> notes = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            String note = arr.optString(i, "").trim();
            if (!note.isBlank()) {
                notes.add(note);
            }
        }
        return List.copyOf(notes);
    }

    private static JSONObject extractRoot(String raw) {
        try {
            String text = raw.trim();
            int start = text.indexOf('{');
            int end = text.lastIndexOf('}');
            if (start < 0 || end <= start) {
                return null;
            }
            return new JSONObject(text.substring(start, end + 1));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String firstNonBlank(JSONObject obj, String... keys) {
        for (String key : keys) {
            String value = obj.optString(key, "").trim();
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}
