package delivery.authoring;

import delivery.prompts.PromptCatalog;

/**
 * Locator preference rules for DOM-first authoring.
 * LLM must pick candidateId from the provided table only.
 */
public final class LocatorPolicy {
    private LocatorPolicy() {
    }

    public static String promptRules() {
        return PromptCatalog.get("grounding/locator-policy");
    }

    public static String loginPreludeRules() {
        return PromptCatalog.get("grounding/login-prelude");
    }

    public static String visionHealRules() {
        return PromptCatalog.get("grounding/vision-heal");
    }

    /**
     * Selector priority for Bug Hunter planner (page-map grounded actions).
     * Prefer project preferred-hook attributes when listed in the user prompt.
     */
    public static String huntPlannerRules() {
        return PromptCatalog.get("grounding/hunt-planner");
    }

    public static String freeInventRules() {
        return PromptCatalog.get("grounding/free-invent");
    }
}
