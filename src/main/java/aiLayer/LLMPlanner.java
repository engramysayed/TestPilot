package aiLayer;

import utils.FilesManager;
import utils.LogsManager;
import utils.PropertyReader;

import java.nio.file.Files;
import java.nio.file.Path;

public class LLMPlanner {

    private final Path runFolder;
    private final LLMClient client;
    public static final int MAX_STEPS_PER_BATCH = Integer.parseInt(PropertyReader.getProperty("MaxSteps"));

    public LLMPlanner(Path runFolder) {
        this.runFolder = runFolder;
        FilesManager.createDirectory(runFolder.resolve("planner").toString());
        this.client = new LLMClient();
        //free model -> gemini-3-flash-preview
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
        You are a test automation planner.

        You will receive a JSON message (PlannerStart or PlannerUpdate) that includes:
        - the scenario
        - the current state (url + slim html + screenshotRef)
        - and the REQUIRED output schema.

        Your job:
        - Output ONLY ONE JSON object (no markdown, no explanations)
        - It MUST match the requiredOutputSchema inside the message
        - Produce NEXT steps that are tightly related and safe to execute on the CURRENT page only
        - steps array length MUST be 1 to %d (maxSteps=%d).

        CRITICAL OUTPUT RULES:
        - Output raw JSON only.
        - Do NOT wrap the response in triple backticks.
        - Do NOT include the word "json".
        - Do NOT include explanations.
        - The response must begin with '{' and end with '}'.
        - If you violate this format, the system will fail.

        IMPORTANT RULE ABOUT "value":
        - Put ALL non-selector parameters inside "value":
          - If action=navigate -> value MUST be the URL
          - If action=getCustomTab -> value MUST be the tab handle/index
          - If action is frame switching -> value MUST be frame id/name/index
          - If action=type/select/upload -> value MUST be the input value/path

        PLANNER_MESSAGE_JSON:
        %s

        YOUR OUTPUT (JSON only):
        """.formatted(
                MAX_STEPS_PER_BATCH,
                MAX_STEPS_PER_BATCH,
                plannerMessageJson
        );
    }


}
