package delivery.vision;

import delivery.authoring.StepIntentBinder;
import delivery.prompts.PromptCatalog;

/**
 * Versioned vision grounding prompt. Locate only — no XPath, no browser actions.
 */
public final class VisionPromptTemplate {
    public static final String VERSION = "v1";

    private VisionPromptTemplate() {
    }

    public static String version() {
        return VERSION;
    }

    public static String systemPrompt() {
        return PromptCatalog.get("vision/vision-template");
    }

    public static String fewShotBlock() {
        return PromptCatalog.get("vision/vision-few-shot");
    }

    public static String userPrompt(StepIntentBinder.IntentLine intent) {
        String action = actionOf(intent);
        String target = intent == null || intent.text() == null ? "" : intent.text().trim();
        return "ACTION:\n" + action
                + "\n\nTARGET:\n" + target
                + "\n\nSEMANTIC CONSTRAINTS:\n"
                + "(none — use the screenshot and TARGET only)\n\n"
                + fewShotBlock();
    }

    private static String actionOf(StepIntentBinder.IntentLine intent) {
        if (intent == null || intent.kind() == null) {
            return "UNKNOWN";
        }
        return switch (intent.kind()) {
            case CLICK, CLICK_LOGIN, HOVER, DOUBLE_CLICK, RIGHT_CLICK -> "CLICK";
            case TYPE_USER, TYPE_PASS, TYPE_FIELD -> "INPUT";
            case ASSERT_VISIBLE -> "FIND_TEXT";
            case DRAG -> "DRAG";
        };
    }
}
