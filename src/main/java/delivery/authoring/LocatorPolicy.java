package delivery.authoring;

/**
 * Locator preference rules for DOM-first authoring.
 * LLM must pick candidateId from the provided table only.
 */
public final class LocatorPolicy {
    private LocatorPolicy() {
    }

    public static String promptRules() {
        return """
                You map Excel test steps to actions using ONLY the provided DOM candidate table.
                NEVER invent locators. NEVER guess data-test / id / name / CSS values.

                Locator priority already applied in the table: id, data-test*, name, then CSS/XPath attr.
                Prefer higher-priority candidates when equally plausible.

                Each step MUST include candidateId (e.g. "c3") from the table when a locator is needed.
                Optionally also echo locatorStrategy/locatorValue — they MUST match that candidate exactly.

                Intent fidelity (CRITICAL):
                - Implement ONLY the Excel Steps and ExpectedResult.
                - Do NOT invent extra clicks, navigation, form fills, or login
                  unless the Excel case says so.
                - Prefer assertionType visible|urlContains|textContains when Excel says confirm/verify/see.
                - For type actions, put the typed text in "value".

                Context (iframe / Shadow DOM):
                - The target control may live inside an iframe or an open shadow root.
                - If the shortlist looks wrong for the visible UI, prefer candidates whose label/tag
                  match what you see; the runtime will also search iframes and open shadow roots.
                - When a screenshot is attached, use it only to choose among shortlist candidateIds.

                Output strict JSON only:
                {"steps":[{"pageName":"","actionType":"elementAction|browserAction","action":"click|type|...",
                "candidateId":"c1","locatorStrategy":"","locatorValue":"","value":"",
                "assertionType":"","assertionExpected":"","rationale":""}]}
                """;
    }

    public static String loginPreludeRules() {
        return """
                Author ONLY login steps: enter username, enter password, click login.
                Use ONLY candidateIds from the login-page candidate table.
                Do not navigate elsewhere. Do not invent non-login steps.
                Login widgets may be inside iframe or shadow DOM — still pick candidateId only.
                Output strict JSON steps array as specified.
                """;
    }

    public static String visionHealRules() {
        return """
                You are healing an ambiguous or failed locator bind.
                A screenshot of the current browser page is attached (base64 image).
                Pick exactly one candidateId from the shortlist that matches the Excel intent
                AND the visible UI in the screenshot.
                Consider that the control may be inside an iframe or shadow DOM — still pick
                only from the shortlist ids; do not invent locators.
                Respond with a short Thought, then an Action JSON object:
                Thought: <brief reason>
                Action: {"candidateId":"<id from shortlist>"}
                The candidateId must come from the shortlist. Do not return locators.
                """;
    }

    public static String freeInventRules() {
        return """
                You are the final one-shot healer for one failed Excel UI intent.
                You may invent locators only for this intent. Do not add navigation, login,
                or unrelated workflow steps. Return 1 to 3 steps maximum.
                Prefer stable id, name, data-test*, CSS attribute, or XPath attribute locators.
                Never use absolute /html/body locators or volatile UUID-like values.
                Respond with strict JSON:
                {"thought":"short reason","steps":[{"action":"click|type|select|assert",
                "locatorStrategy":"id|name|css|xpath|data-testid|data-test|data-qa",
                "locatorValue":"...","value":"","assertionType":"","assertionExpected":""}]}
                """;
    }
}
