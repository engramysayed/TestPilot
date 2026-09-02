package project.utils.reports;
import org.apache.commons.io.FileUtils;
import project.utils.FilesManager;
import project.utils.Logs.LogsManager;
import project.utils.Terminal;
import project.utils.TimeManager;
import project.utils.dataReader.PropertyReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class AllureReportGenerator {

    //single-file
    public static void generateReports(boolean isSingleFile) {

        Path outputFolder = isSingleFile
                ? AllurePaths.REPORT_PATH
                : AllurePaths.FULL_REPORT_PATH;

        // allure generate -o reports --single-file --clean
        List<String> command = new ArrayList<>(List.of(
                AllureDownloadManager.getExecutable().toString(),
                "generate",
                AllurePaths.RESULTS_FOLDER.toString(),
                "-o", outputFolder.toString(),
                "--clean"
        ));

        if (isSingleFile) {
            command.add("--single-file");
         }
        Terminal.executeCommand(command.toArray(new String[0]));
    }

    // Open Allure report in browser after execution
    public static void openReport(String reportFileName) {
        if (!PropertyReader.getProperty("EXECUTION_TYPE").toLowerCase()
                .equalsIgnoreCase("local")
                &&PropertyReader.getProperty("OpenAllureAfterExecution").equalsIgnoreCase("true")) {
            return;
        }

        Path reportPath = AllurePaths.REPORT_PATH.resolve(reportFileName);
        if (PropertyReader.getProperty("OS_NAME").toLowerCase().equals("windows")) {
            Terminal.executeCommand("cmd.exe", "/c", "start", reportPath.toString()
            );
        } else {
            LogsManager.warn("Opening Allure Report is not supported on this OS.");
        }
    }

    // copy history folder to results folder
    public static void copyHistory() {
        try {
            FileUtils.copyDirectory(
                    AllurePaths.HISTORY_FOLDER.toFile(),
                    AllurePaths.RESULTS_HISTORY_FOLDER.toFile()
            );
        } catch (Exception e) {
            LogsManager.error("Error copying history files"+ e.getMessage());
        }
    }

    //rename report file to AllureReport_timestamp.html
    public static String renameReportFile() {
        try {
            //safe load to timestamp
            String ts = TimeManager.getTimeStamp().replace(":", "-").replace(" ", "_");
            String reportFileName = "AllureReport_" + ts + AllurePaths.REPORT_EXTENSION;
            FilesManager.renameDirectory(AllurePaths.REPORT_PATH.resolve(AllurePaths.INDEX_HTML).toString(), reportFileName);
            LogsManager.info("Renamed Allure report file to: " + reportFileName);
            return reportFileName;
        } catch (Exception e) {
            LogsManager.error("Error renaming report file: " + e.getMessage());
        }
        return null;
    }


}