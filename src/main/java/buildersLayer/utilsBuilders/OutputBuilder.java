package buildersLayer.utilsBuilders;
import executionLayer.actionExecute;
import buildersLayer.mainBuilder.OrchestratorBuilder;
import org.json.JSONArray;
import org.json.JSONObject;
import utils.LogsManager;

import java.nio.file.Path;

import static buildersLayer.stateBuilders.StateVars.*;
import static buildersLayer.stateBuilders.HistoryMemory.updateHistoryMemory;
import static utils.FilesManager.createDirectory;
import static utils.FilesManager.writeFile;

public class OutputBuilder {
    public static Path summaryFile,bugsFile;

    public static void appendFinalInsights(OrchestratorBuilder helper) {
        try {
            String finalSummary = safe(helper.getFinalSummary());
            String currentObservation = safe(helper.getCurrentObservation());
            if (finalSummary.isBlank() && currentObservation.isBlank()) {
                return;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("==================================================\n");
            sb.append("Final LLM Insights\n");
            if (!finalSummary.isBlank()) {
                sb.append("FinalSummary: ").append(finalSummary).append("\n");
            }
            if (!currentObservation.isBlank()) {
                sb.append("CurrentObservation: ").append(currentObservation).append("\n");
            }
            setRunningSummary(sb);
            saveSummary();
        } catch (Exception e) {
            LogsManager.error("Failed appending final insights: " + e.getMessage());
        }
    }


    public static void recordBugs(OrchestratorBuilder helper) {
        try {
            if (bugsFile == null || helper == null) {
                return;
            }

            if (helper.getBugs().isEmpty()) {
                writeFile(bugsFile, "No bugs reported by agent.\n");
                return;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("Bugs reported by agent:\n\n");

            for (JSONObject b : helper.getBugs()) {
                sb.append("====================================\n");
                sb.append("ID: ").append(b.optString("id")).append("\n");
                sb.append("Title: ").append(b.optString("title")).append("\n");
                sb.append("Type: ").append(b.optString("type")).append("\n");
                sb.append("Severity: ").append(b.optString("severity")).append("\n");
                sb.append("Expected: ").append(b.optString("expected")).append("\n");
                sb.append("Actual: ").append(b.optString("actual")).append("\n");
                sb.append("Evidence: ").append(b.optString("evidence")).append("\n");
                JSONArray steps = b.optJSONArray("stepsToReproduce");
                if (steps != null) {
                    sb.append("StepsToReproduce:\n");
                    for (int i = 0; i < steps.length(); i++) {
                        sb.append("  - ").append(steps.getString(i)).append("\n");
                    }
                }
                sb.append("====================================\n\n");
            }

            writeFile(bugsFile, sb.toString());

        } catch (Exception e) {
            utils.LogsManager.error("Failed writing bugs file: "+e);
        }
    }

    public static void recordSummary(int cycleId, String batchDetails,
                                     JSONArray executedStepsJson, actionExecute executor) {
        try {
            StringBuilder sb = new StringBuilder();

            sb.append("==================================================\n");
            sb.append("Cycle ").append(cycleId)
                    .append(" | Batch: ").append(safe(batchDetails)).append("\n");

            for (int i = 0; i < executedStepsJson.length(); i++) {
                JSONObject step = executedStepsJson.getJSONObject(i);
                JSONObject res = step.optJSONObject("result");

                boolean ok = false;
                String msg = "";
                if (res != null) {
                    ok = res.optBoolean("success", false);
                    msg = res.optString("message", "");
                }

                sb.append("  ")
                        .append(icon(ok)).append(" Step ").append(step.optInt("stepId", i + 1))
                        .append(" | ").append(safe(step.optString("actionType", "")))
                        .append(" -> ").append(safe(step.optString("action", "")))
                        .append(" | ").append(safe(step.optString("selector", "")));

                String v = step.optString("value", "");
                if (!v.isBlank()) {
                    sb.append(" | value=").append(safe(v));
                }

                if (!ok) {
                    sb.append("\n      -> ").append(shortenError(msg));
                }

                sb.append("\n");
            }

            sb.append("URL: ").append(safe(executor.getUrl())).append("\n");
            sb.append("LastScreenshot: ").append(safe(getLastScreenshotRef())).append("\n");

            setRunningSummary(sb);
            updateHistoryMemory(
                    cycleId,
                    batchDetails,
                    executedStepsJson,
                    executor.getUrl(),
                    getLastScreenshotRef()
            );

        } catch (Exception e) {
            LogsManager.error("Failed to append cycle summary: " + e.getMessage());
        }
    }

    public static void saveSummary() {
        try {
            writeFile(summaryFile, getRunningSummary().toString());
        } catch (Exception e) {
            LogsManager.error("Failed to save running summary: " + e.getMessage());
        }
    }

    public static Path createNewRunFolder(Path runFolder) {
        String ts = getTimeStamp();

        Path runPath = Path.of(
                System.getProperty("user.dir"),
                "test-output",
                "runs",
                "run_" + ts
        );

        //Create main run directory
        createDirectory(runPath.toString());

        //Subfolders
        createDirectory(runPath.resolve("screenshots").toString());
        createDirectory(runPath.resolve("planner").toString());

        //set summary and bugs file path
        summaryFile = runPath.resolve("planner").resolve("running_summary.txt");
        bugsFile = runPath.resolve("planner").resolve("bugs.txt");
        return runPath;
    }





    private static String icon(boolean ok) {
        if (ok) {
            return "PASS ";
        }
        return "FAIL ";
    }

    //avoids NullPointerException when building logs/JSON
    private static String safe(String s) {
        if (s == null) {
            return "";
        }
        return s.replaceAll("\\s+", " ").trim();
    }

    private static String shortenError(String msg) {
        if (msg == null) {
            return "";
        }
        String m = msg.replaceAll("\\s+", " ").trim();

        String[] cutMarkers = {
                "(Session info:", "Build info:", "System info:", "Driver info:",
                "Capabilities", "Command:", "For documentation"
        };
        for (String marker : cutMarkers) {
            int i = m.indexOf(marker);
            if (i > 0) {
                m = m.substring(0, i).trim();
            }
        }

        int max = 220;
        if (m.length() > max){
            m = m.substring(0, max) + "...";
        }
        return m;
    }

    private static String getTimeStamp(){
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date())
                .replace(":", "-").replace(" ", "_");
    }


}
