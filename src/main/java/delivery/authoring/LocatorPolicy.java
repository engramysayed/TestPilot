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

    /**
     * Selector priority for Bug Hunter planner (page-map grounded actions).
     * Prefer project preferred-hook attributes when listed in the user prompt.
     */
    public static String huntPlannerRules() {
        return """
                Locator rules (CRITICAL):
                - Emit ONLY locators that appear in the page map (or slim DOM when that section is present).
                - Prefer locatorStrategy+locatorValue from the page map Controls list over free-form CSS.
                - Priority when several controls match: project preferred-hook attributes (if listed),
                  then id, data-testid / data-test / data-qa, name, then CSS/XPath attribute locators.
                - Never invent ids, data-test values, or absolute /html/body XPath.
                - Never use volatile framework ids (_r_*, :r*, ember*, mui-*).
                - Prefer accessible name / label text shown on the map when choosing among equals.
                """;
    }

    public static String freeInventRules() {
        return """
                You are the final one-shot healer for one failed Excel UI intent.
                You may invent locators only for this intent. Return 1 to 3 steps maximum.
                Prefer stable id, name, data-test*, CSS attribute, or XPath attribute locators.
                Never use absolute /html/body locators or volatile UUID-like values.
                Never build on a framework-generated id such as _r_15_, :r0:, ember1423 or mui-42 —
                those change on every reload. For such elements use a CSS or XPath attribute
                locator on a human attribute (aria-label, placeholder, title, name, role).
                If the named field is not on this page and the failure names an Excel open-path,
                you MAY emit one navigate step whose value is exactly that path. Do not invent
                any other URL, login, or unrelated workflow.
                Respond with strict JSON:
                {"thought":"short reason","steps":[{"action":"click|type|select|assert|navigate",
                "locatorStrategy":"id|name|css|xpath|data-testid|data-test|data-qa",
                "locatorValue":"...","value":"","assertionType":"","assertionExpected":""}]}
                """;
    }
}
