package delivery.vision;

import delivery.authoring.StepIntentBinder;

/**
 * UI-TARS-specific prompts. Prefer native action grounding; JSON is fallback.
 */
public final class UiTarsPrompt {

    private UiTarsPrompt() {
    }

    public static String groundingSystem() {
        return """
                You are a GUI agent. Given a screenshot and a user instruction, output the next action.

                ## Output Format
                Thought: <one short line>
                Action: click(start_box='<|box_start|>(x,y)<|box_end|>')

                ## Action Space
                click(start_box='<|box_start|>(x,y)<|box_end|>')
                finished()

                ## Rules
                1. (x,y) use the 0–1000 relative grid (not raw pixels). Point at the control center.
                2. Prefer click whenever any matching control is visible. Do NOT call finished() when
                   a Login/Submit button or matching input is visible on the screenshot.
                3. finished() only when the requested control is completely absent.
                4. No XPath, no CSS, no full-page description.
                """;
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
        return """
                You judge a screenshot against a visual assertion.

                Return JSON only. No markdown. Schema:
                {"status":"PASS|FAIL|UNCERTAIN","confidence":0.0,
                "observation":"<concrete UI text and controls you see>",
                "evidence":"<why status matches the assertion>"}
                status must be PASS, FAIL, or UNCERTAIN.
                Judge this screenshot only. Do not compare to Figma or a reference image.
                Do not invent locators or CSS selectors.
                Do not copy placeholder angle-bracket text into observation or evidence.
                Never set observation or evidence to schema examples like "what is visible" or "why".
                observation and evidence must be concrete visible UI facts; never leave them blank when status is PASS or FAIL.
                confidence is in 0 to 1; use values like 0.7–0.99 (do not default to 0).
                """;
    }

    public static String assertRetryNudge() {
        return "Return the full JSON again. observation and evidence must be real UI facts "
                + "(not angle-bracket schema text). confidence must be 0.7–0.99 for PASS/FAIL.";
    }
}
