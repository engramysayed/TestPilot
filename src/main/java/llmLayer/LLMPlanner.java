package llmLayer;

import utils.FilesManager;
import utils.LogsManager;
import utils.PropertyReader;

import java.nio.file.Files;
import java.nio.file.Path;

public class LLMPlanner {

    private final Path runFolder;
    private final LLMClient client;
    public static final int MAX_STEPS_PER_BATCH = Integer.parseInt(PropertyReader.getProperty("MaxSteps"));
    public static final int MAX_CYCLES = Integer.parseInt(PropertyReader.getProperty("MaxCycles"));

    public LLMPlanner(Path runFolder) {
        this.runFolder = runFolder;
        this.client = new LLMClient();
    }

    public String getNextBatch(int cycleId, String plannerMessageJson, String screenshotRef) throws Exception{

        String prompt = buildPrompt(plannerMessageJson);

        // save prompt
        Path promptFile = runFolder.resolve("planner").resolve("cycle_" + cycleId + "_prompt.txt");



        FilesManager.writeFile(promptFile, prompt);

        // load screenshot bytes (optional)
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

        // call Gemini (image + text if available)
        String responseText = client.generateTextWithOptionalImage(
                prompt,
                screenshotBytes,
                "image/png"
        );


        // save response
        Path responseFile = runFolder.resolve("planner").resolve("cycle_" + cycleId + "_response.json");
        FilesManager.writeFile(responseFile, responseText);

        return responseText;
    }


    private String buildPrompt(String plannerMessageJson) {

        return """
        You are a professional test automation planner.

        You will receive a JSON message (PlannerStart or PlannerUpdate) that includes:
        - the scenario
        - the current state (url + slim html + screenshotRef)
        - and the REQUIRED output schema.
        - historySummary

        Your job:
        - Produce the NEXT batch of steps that are tightly related and safe to execute on the CURRENT page only.
        - steps array length MUST be between 1 and %d (maxSteps=%d).
        - Follow ALL schema rules strictly.

        ========================
        CRITICAL OUTPUT RULES
        ========================
        - Output RAW JSON only.
        - Do NOT wrap the response in triple backticks.
        - Do NOT include the word "json".
        - Do NOT include explanations.
        - Do NOT include markdown.
        - The response MUST start with '{' and end with '}'.
        - IMPORTANT: stopTesting=true means STOP AFTER executing the returned steps. steps may be non-empty.
        - If finished, return:
          {
            "type": "PlannerBatch",
            "stopTesting": true,
            "batchDetails": "",
            "finalSummary": "short summary of what you did",
            "currentObservation": "what is visible now on the page",
            "steps": []
          }
        ========================
        MAX CYCLES SAFETY
        ========================
        - You have a hard limit of MAX_CYCLES which is %d cycles only.
        - Avoid repeating attempts. If you cannot progress after 2 different strategies, set stopTesting=true and report why.   
        ========================
        BUG REPORTING (MANDATORY)
        ========================
        - You MUST NOT report bugs during execution batches.
        - Set "bugs": [] for all batches where stopTesting=false.
        - Only when stopTesting=true (final batch), you may include "bugs" items.
        - Only report a bug if you have evidence from executedSteps failures, screenshots, or clear UI observations.
        - If no bugs found, return "bugs": [] in the final batch.
        ========================
        SELECTOR PRIORITY RULE (STRICT)
        ========================
        When choosing selectors and Iframe:
        1) ALWAYS try id:<...> first (if available).
        2) If no id exists -> use name:<...>.
        3) If no name exists -> use cssSelector:<...>.
        4) Use xpath ONLY if absolutely necessary.

        Avoid xpath unless:
        - There is no stable id.
        - There is no name.
        - A standard Selenium CSS selector cannot uniquely identify the element.

        Selectors MUST be:
        - Stable
        - Not dynamic
        - Not index-based
        - Not text-based unless no other option exists.

        If you violate selector priority, the step will be rejected.

        ========================
        OPTION B (MANDATORY): FORBID TEXT-BASED CSS
        ========================
        - NEVER use non-standard CSS or jQuery selectors.
        - Forbidden in CSS (these WILL FAIL in Selenium):
          :contains(...)
          :has(...)
          :eq(...)
          :nth-*(...) if it makes selector dynamic/unstable
        - CSS selectors must be standard Selenium CSS only.
        - If text matching is unavoidable, use XPath (LAST RESORT ONLY) and DO NOT use index-based XPath.

        ========================
        IFRAME RULE (MANDATORY)
        ========================
        - If the page contains an <iframe> OR the UI is embedded in an iframe:
          You MUST switch to the correct iframe BEFORE clicking or typing inside it.
        - If iframe is present in HTML, prefer switching by a stable CSS selector:
          Example: cssSelector:iframe[src*="/dash/"]
        - Use action= switchFrameByCssSelector when possible.
        - After switching, then return steps for click/type inside the iframe.
        - If you finish iframe actions and need the main page again, use switchToDefaultContent.

        ========================
        DRAG & DROP RULE (MANDATORY)
        ========================
        - If action=dragDrop:
        - "selector" MUST be the SOURCE element.
        - "value" MUST contain the TARGET selector (same selector format: id:<...> OR cssSelector:<...> etc).
        - Do NOT leave value empty.
        - Do NOT invent target without verifying it exists in currentHtmlSlim.
        - Apply selector priority rules for BOTH source and target.
        
        ========================
        SAFETY WHEN STATE IS MISSING
        ========================
        - If currentUrl is empty/invalid OR currentHtmlSlim is empty:
          DO NOT invent selectors.
          Return a safe browserAction first (refresh or getUrl) and then proceed.

        ========================
        WAIT RULE (DYNAMIC UI)
        ========================
        - Any click that opens dropdown/menu/modal must use generalWait between 3 and 8.
        - screenshotWait between 1 and 3.

        ========================
        HISTORY SUMMARY (MANDATORY)
        ========================
        The planner JSON includes "historySummary".
        - You MUST read it carefully.
        - Do NOT repeat any step that already succeeded.
        - Do NOT retry the same failing selector again.
        - You may retry ONLY if strategy changes (switch iframe, different stable selector, increased wait).
        ========================
        FORM DATA RULE
        ========================
        If typing into a form:
        - Always use realistic valid dummy data.
        - Do NOT leave value empty.
        - Do NOT use random garbage strings.

        ========================
        VALUE FIELD RULE
        ========================
        Put ALL non-selector parameters inside "value":
        - If action=navigate -> value MUST be the URL.
        - If action=getCustomTab -> value MUST be the tab handle/index.
        - If action is frame switching -> value MUST be frame id/name/index or a CSS selector (depending on action).
        - If action=type/select/upload -> value MUST be the input value/path.
        - If action=dragDrop -> value MUST be the TARGET selector.
        - Leave empty ONLY if not required.

        ========================
        PLANNER MESSAGE
        ========================
        %s

        ========================
        YOUR OUTPUT (JSON ONLY)
        ========================
        """.formatted(
                MAX_STEPS_PER_BATCH,
                MAX_STEPS_PER_BATCH,
                MAX_CYCLES,
                plannerMessageJson
        );
    }


}
