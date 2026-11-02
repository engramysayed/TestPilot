package delivery.vision;

import delivery.authoring.StepIntentBinder;
import delivery.prompts.PromptCatalog;

/**
 * UI-TARS-specific prompts. Prefer native action grounding; JSON is fallback.
 */
public final class UiTarsPrompt {

    private UiTarsPrompt() {
    }

    public static String groundingSystem() {
        return PromptCatalog.get("vision/ui-tars");
    }

    /** Short instruction line matching UI-TARS training style. */
    public static String groundingUser(StepIntentBinder.IntentLine intent) {
        String target = intent == null || intent.text() == null ? "" : intent.text().trim();
        if (target.isBlank()) {
            target = "Click the target control";
        }
        return "User Instruction: " + target
                + "\n\nLocate the named control in the screenshot and click its center. "
                + "If the instruction is to click Login/Submit, click the Login/Submit button — "
                + "not username/password inputs, not the footer, not empty margins.";
    }

    public static String assertSystem() {
        return PromptCatalog.get("vision/ui-tars-assert");
    }

    public static String assertRetryNudge() {
        return "Return the full JSON again. observation and evidence must be real UI facts "
                + "(not angle-bracket schema text). confidence must be 0.7–0.99 for PASS/FAIL.";
    }
}
