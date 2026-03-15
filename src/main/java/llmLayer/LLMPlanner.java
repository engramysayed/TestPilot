package llmLayer;

import utils.FilesManager;
import utils.LogsManager;
import utils.Config.AppConfigProvider;

import java.nio.file.Files;
import java.nio.file.Path;

public class LLMPlanner {

    private final Path runFolder;
    private final LLMClient client;
    public static final int MAX_STEPS_PER_BATCH = AppConfigProvider.get().maxSteps();
    public static final int MAX_CYCLES = AppConfigProvider.get().maxCycles();

    public LLMPlanner(Path runFolder) {
        this.runFolder = runFolder;
        this.client = new LLMClient();
    }

    public String getNextBatch(int cycleId, String plannerMessageJson, String screenshotRef) throws Exception{

        String prompt = buildPrompt(plannerMessageJson);

        Path promptFile = runFolder.resolve("planner").resolve("cycle_" + cycleId + "_prompt.txt");


        FilesManager.writeFile(promptFile, prompt);

        byte[] screenshotBytes = null;
        Path screenshotPath = runFolder.resolve("screenshots").resolve(screenshotRef);


        try {
            if (Files.exists(screenshotPath)) {
                screenshotBytes = Files.readAllBytes(screenshotPath);
                LogsManager.info("Loaded screenshot: " + screenshotPath);
            } else {
                LogsManager.warn("Screenshot not found, sending text-only: " + screenshotPath);
            }
        } catch (Exception e) {
            LogsManager.warn("Failed to read screenshot, sending text-only. " + e.getMessage());
        }

        String responseText = client.generateTextWithOptionalImage(
                prompt,
                screenshotBytes,
                "image/png"
        );


        Path responseFile = runFolder.resolve("planner").resolve("cycle_" + cycleId + "_response.json");
        FilesManager.writeFile(responseFile, responseText);

        return responseText;
    }


    private String buildPrompt(String plannerMessageJson) {

        return """
        ROLE
        - You are a professional test automation planner.

        INPUT YOU WILL RECEIVE
        - scenario
        - currentState (currentUrl, currentHtmlSlim, currentScreenshotRef)
        - historySummary
        - requiredOutputSchema

        OBJECTIVE
        - Return the NEXT safe batch for the CURRENT page only.
        - Batch size MUST be 1..%d (maxSteps=%d).
        - Follow requiredOutputSchema strictly.

        OUTPUT CONTRACT (STRICT)
        - Output RAW JSON only.
        - No markdown, no code fences, no explanation text.
        - Response MUST start with '{' and end with '}'.
        - All required keys MUST exist and data types MUST match schema.
        - stopTesting=true means stop after executing returned steps (steps may be non-empty).
        - If fully finished, return exactly:
          {
            "type": "PlannerBatch",
            "stopTesting": true,
            "batchDetails": "",
            "finalSummary": "short summary of what you did",
            "currentObservation": "what is visible now on the page",
            "bugs": [],
            "steps": []
          }

        DECISION ORDER (FOLLOW IN THIS ORDER)
        1) Validate state availability.
        2) Check iframe requirement.
        3) Choose stable selectors by priority.
        4) Set waits for dynamic UI actions.
        5) Respect historySummary (avoid repeats).
        6) Return schema-valid JSON only.

        MAX CYCLES POLICY
        - Hard limit: %d cycles.
        - If no progress after 2 different strategies, set stopTesting=true and explain why in finalSummary/currentObservation.

        BUG REPORTING POLICY (STRICT)
        - If stopTesting=false => bugs MUST be [].
        - If stopTesting=true => bugs may contain 0..N items.
        - Report bugs only with evidence from executed step failures, screenshots, or clear UI observation.
        - If no bug exists in final batch, return bugs: [].

        SELECTOR POLICY (STRICT)
        - Priority order: id > name > cssSelector > xpath.
        - Use xpath only as last resort.
        - Selectors MUST be stable, non-dynamic, and non-index-based.
        - Avoid text-based selectors unless no other option exists.

        FORBIDDEN CSS POLICY
        - Never use non-standard CSS/jQuery selectors.
        - Forbidden: :contains(...), :has(...), :eq(...), unstable :nth-* usage.
        - If text matching is unavoidable, use xpath (last resort), not index-based xpath.

        IFRAME POLICY
        - If target UI is inside iframe, switch first before any elementAction.
        - Prefer switchFrameByCssSelector with stable iframe selector when available.
        - After iframe work, use switchToDefaultContent when main page context is needed.

        DRAG & DROP POLICY
        - action=dragDrop:
          - selector = source selector
          - value = target selector
        - value MUST NOT be empty.
        - Target selector must exist in currentHtmlSlim.
        - Apply selector priority to both source and target.

        MISSING STATE SAFETY POLICY
        - If currentUrl is empty/invalid OR currentHtmlSlim is empty:
          - DO NOT invent selectors.
          - Return one safe browserAction first (refresh or getUrl), then proceed.

        WAIT POLICY
        - Clicks opening dropdown/menu/modal must use:
          - generalWait: 3..8
          - screenshotWait: 1..3

        HISTORY POLICY
        - Read historySummary carefully.
        - Do NOT repeat successful steps.
        - Do NOT retry the same failing selector unchanged.
        - Retry only with changed strategy (iframe switch, different stable selector, increased wait).
        - Continue from latest successful sub-goal.

        FORM DATA POLICY
        - For typing into forms, use realistic valid data.
        - Do NOT leave required value empty.
        - Do NOT use random garbage strings.

        VALUE FIELD POLICY
        - Put all non-selector parameters in "value":
          - navigate -> URL
          - getCustomTab -> tab handle/index
          - frame switch actions -> frame id/name/index/css selector (as applicable)
          - type/select/upload -> input/path
          - dragDrop -> target selector
        - Leave value empty only when truly not required.

        PLANNER MESSAGE
        %s

        RETURN JSON ONLY.
        """.formatted(
                MAX_STEPS_PER_BATCH,
                MAX_STEPS_PER_BATCH,
                MAX_CYCLES,
                plannerMessageJson
        );
    }


}
