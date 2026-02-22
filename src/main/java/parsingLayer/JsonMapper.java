package parsingLayer;

import org.json.JSONArray;
import org.json.JSONObject;
import utils.PropertyReader;

import java.util.ArrayList;
import java.util.List;

public class JsonMapper {
    public static final int MAX_STEPS_PER_BATCH = Integer.parseInt(PropertyReader.getProperty("MaxSteps"));


    //Parse LLM JSON response into simple List[] of strings
    public static List<String[]> parseBatchToArrays(String rawResponse) {

        String response = buildResult(rawResponse);

        JSONObject root = new JSONObject(response);

        JSONArray arr = root.optJSONArray("steps");
        List<String[]> steps = new ArrayList<>();

        //stop early if completed
        if (arr == null) return steps;


        int limit = Math.min(arr.length(), MAX_STEPS_PER_BATCH);

        for (int i = 0; i < limit; i++) {
            JSONObject o = arr.getJSONObject(i);

            int stepId = o.optInt("stepId", i + 1);
            String stepDetails = o.optString("stepDetails", "");
            String actionType = o.optString("actionType", "");
            String action = o.optString("action", "");
            String selector = o.optString("selector", "");
            String value = o.optString("value", "");

            int generalWait = o.optInt("generalWait", 5);
            int screenshotWait = o.optInt("screenshotWait", 1);

            boolean screenshot = o.optBoolean("screenshot", true);
            boolean stopTestingStep = o.optBoolean("stopTesting", false);

            steps.add(new String[]{
                    String.valueOf(stepId),
                    stepDetails,
                    actionType,
                    action,
                    selector,
                    value,
                    String.valueOf(generalWait),
                    String.valueOf(screenshotWait),
                    String.valueOf(screenshot),
                    String.valueOf(stopTestingStep)
            });

            if (stopTestingStep) break;
        }

        return steps;
    }

    //Build execution result JSON
    public static String buildResult(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("LLM response is null");
        }

        String s = raw.trim();

        //remove common markdown if present
        if (s.startsWith("```")) {
            int firstNewLine = s.indexOf('\n');
            int lastFence = s.lastIndexOf("```");
            if (firstNewLine != -1 && lastFence != -1 && lastFence > firstNewLine) {
                s = s.substring(firstNewLine + 1, lastFence).trim();
            }
        }

        //keep only the JSON object
        int first = s.indexOf('{');
        int last = s.lastIndexOf('}');
        if (first == -1 || last == -1 || last <= first) {
            throw new IllegalArgumentException("No JSON object found in LLM response: " + raw);
        }

        return s.substring(first, last + 1).trim();
    }




    //Build the FIRST message to the LLM (PlannerStart).
    public static String buildPlannerStart(
            String scenario, String currentUrl,
            String currentHtmlSlim, String currentScreenshotRef
    ,String historySummary)
    {
        JSONObject output = new JSONObject();
        output.put("type", "PlannerStart");
        output.put("scenario", scenario);

        JSONArray rules = new JSONArray();
        rules.put("Return ONLY a single JSON object (no code fences, no explanation).");
        rules.put("ALL keys required.");
        rules.put("Return PlannerBatch JSON object only.");
        rules.put("steps MUST contain 1 to " + MAX_STEPS_PER_BATCH + " items.");
        rules.put("If finished: stopTesting=true and steps=[].");
        output.put("rules", rules);

        output.put("requiredOutputSchema", getRequiredBatchSchemaText());

        JSONObject state = new JSONObject();
        state.put("currentUrl",  currentUrl);
        state.put("currentHtmlSlim",  currentHtmlSlim);
        state.put("currentScreenshotRef",  currentScreenshotRef);
        output.put("currentState", state);
        output.put("historySummary", historySummary);
        return output.toString();
    }



    //The exact schema text we want the LLM to output each batch.
    private static String getRequiredBatchSchemaText() {
        return """
            Return ONLY a single JSON object (no code fences, no explanation).
            Output schema:
            {
              "type": "PlannerBatch",
              "stopTesting": false,
              "batchDetails": "short description",
              "finalSummary": "",
              "currentObservation": "",
              "steps": [
                {
                  "stepId": 1,
                  "stepDetails": "very short details about what we will do on this step",
                  "actionType": "browserAction|elementAction|frameAction",
                  "action": "click|type|clear|select|getText|getAttr|scroll|upload|dragDrop|navigate|refresh|back|maximize|getUrl|close|openNewWindow|getCustomTab|switchFrameById|switchFrameByName|switchFrameByIndex|switchFrameByCssSelector|switchToParent|switchToDefaultContent",                  "selector": "id:<...> OR name:<...> OR cssSelector:<...> OR xpath:<...> OR className:<...> OR linkText:<...> OR partialLinkText:<...> (empty allowed for pure browser actions)",
                  "value": "STRING. IMPORTANT: use it for ALL extra parameters. Empty if not needed.",
                  "generalWait": give integer value to use in explicit wait (max =15),
                  "screenshotWait": give integer value to use after the action to take screenshot(max =15),
                  "screenshot": true,
                  "stopTesting": false
                }
              ]
            }

            Rules:
            - Output valid JSON only.
            - steps MUST contain 1 to %d items (maxSteps=%d).
            - Steps must be tightly related and safe to execute in sequence on the current page.
            - If finished, set stopTesting=true and steps=[] and you MUST fill finalSummary and currentObservation.
            - finalSummary and currentObservation MUST be empty strings unless stopTesting=true.
            - If stopTesting=true and steps is not empty, stop AFTER executing the returned steps.
            """.formatted(MAX_STEPS_PER_BATCH, MAX_STEPS_PER_BATCH);
    }



}
