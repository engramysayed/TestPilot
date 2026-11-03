package llmLayer;

import delivery.prompts.PromptCatalog;

import utils.FilesManager;
import utils.LogsManager;
import utils.Config.AppConfigProvider;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

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
        return PromptCatalog.format("planner/llm-planner", Map.of(
                "maxSteps", String.valueOf(MAX_STEPS_PER_BATCH),
                "maxCycles", String.valueOf(MAX_CYCLES),
                "plannerMessageJson", plannerMessageJson == null ? "" : plannerMessageJson
        ));
    }


}
