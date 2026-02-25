package helpers.utilsBuilders;
import executionLayer.actionExecute;
import helpers.mainHelper.OrchestratorHelper;
import org.json.JSONArray;
import org.json.JSONObject;
import utils.LogsManager;

import java.nio.file.Path;

import static helpers.stateBuilders.StateVars.*;
import static utils.FilesManager.createDirectory;
import static utils.FilesManager.writeFile;

public class OutputBuilder {
    public static Path summaryFile,bugsFile;
    private static OrchestratorHelper helper;


    public static void recordBugs() {
        try {
            if (bugsFile == null) {return;}

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

                boolean ok = res != null && res.optBoolean("success", false);
                String msg = (res == null) ? "" : res.optString("message", "");

                sb.append("  ")
                        .append(icon(ok)).append(" Step ").append(step.optInt("stepId", i + 1))
                        .append(" | ").append(safe(step.optString("actionType", "")))
                        .append(" -> ").append(safe(step.optString("action", "")))
                        .append(" | ").append(safe(step.optString("selector", "")));

                String v = step.optString("value", "");
                if (v != null && !v.isBlank()) {
                    sb.append(" | value=").append(safe(v));
                }

                if (!ok) {
                    sb.append("\n      ↳ ").append(shortenError(msg));
                }

                sb.append("\n");
            }

            sb.append("URL: ").append(safe(executor.getUrl())).append("\n");
            sb.append("LastScreenshot: ").append(safe(getLastScreenshotRef())).append("\n");

            setRunningSummary(sb);

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
        summaryFile = runFolder.resolve("planner").resolve("running_summary.txt");
        bugsFile = runFolder.resolve("planner").resolve("bugs.txt");
        return runPath;
    }





    private static String icon(boolean ok) { return ok ? "PASS " : "FAIL "; }

    private static String safe(String s) {
        return (s == null) ? "" : s.replaceAll("\\s+", " ").trim();
    }

    private static String shortenError(String msg) {
        if (msg == null) return "";
        String m = msg.replaceAll("\\s+", " ").trim();

        String[] cutMarkers = {
                "(Session info:", "Build info:", "System info:", "Driver info:",
                "Capabilities", "Command:", "For documentation"
        };
        for (String marker : cutMarkers) {
            int idx = m.indexOf(marker);
            if (idx > 0) { m = m.substring(0, idx).trim(); }
        }

        int max = 220;
        if (m.length() > max) m = m.substring(0, max) + "...";
        return m;
    }

    private static String getTimeStamp(){
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date())
                .replace(":", "-").replace(" ", "_");
    }


}
