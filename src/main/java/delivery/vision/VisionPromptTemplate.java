package delivery.vision;

import delivery.authoring.StepIntentBinder;

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
        return """
                You are a web UI visual grounding engine.

                Your task is to locate the UI element referred to by a test step in the provided screenshot.

                You are NOT responsible for executing the action.
                Your only responsibility is to identify the visual target precisely.

                INSTRUCTIONS:
                1. Understand the intended UI element from the test step.
                2. Inspect the entire screenshot before selecting a target.
                3. Locate the UI element that best matches the requested target.
                4. Consider visible text, labels, icons, buttons, inputs, placeholders, roles,
                   spatial relationships, and surrounding elements.
                5. If the step refers to a spatial relationship, use that relationship.
                6. Prefer the element that most directly satisfies the test step's intent.
                7. Do not select an element merely because its text is similar.
                8. If multiple elements are plausible, return multiple candidates.
                9. Do not guess when the target is not visible.
                10. Do not generate XPath.
                11. Do not generate CSS selectors.
                12. Do not execute browser actions.
                13. Return ONLY valid JSON.

                OUTPUT FORMAT:
                {
                  "found": true,
                  "candidates": [
                    {
                      "description": "short description of target",
                      "bbox": { "x": 0, "y": 0, "width": 0, "height": 0 },
                      "confidence": 0.9
                    }
                  ]
                }

                If the target cannot be confidently located:
                { "found": false, "candidates": [] }

                bbox is CSS pixels, origin top-left of the screenshot image.
                confidence is in [0.0, 1.0]; use values like 0.7–0.99 when found (do not default to 0).
                Do not describe the whole page; target the single control named by TARGET.
                bbox width/height must be the tight widget size (typically under ~200px), not a large region.
                """;
    }

    public static String fewShotBlock() {
        return """
                EXAMPLES:
                - Click the Login button → find the visible Login button (not a nearby link).
                - Enter username → find the input associated with Username (not password/search).
                - Click the three dots next to John Smith → menu control next to that name.
                - Click Delete below the confirmation message → use the spatial relationship.
                """;
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
            case CLICK, CLICK_LOGIN -> "CLICK";
            case TYPE_USER, TYPE_PASS, TYPE_FIELD -> "INPUT";
            case ASSERT_VISIBLE -> "FIND_TEXT";
        };
    }
}
