package project.utils.reports;
import com.google.common.collect.ImmutableMap;
import project.utils.Logs.LogsManager;
import project.utils.dataReader.PropertyReader;

import java.io.File;

import static com.github.automatedowl.tools.AllureEnvironmentWriter.allureEnvironmentWriter;

public class AllureEnviromentsManager {

         public static void setAllureEnvironment() {
            allureEnvironmentWriter(
                    ImmutableMap.<String, String>builder()
                            .put("OS", PropertyReader.getProperty("OS_NAME"))
                            .put("OS Version", PropertyReader.getProperty("OS_VERSION"))
                            .put("Java Version", PropertyReader.getProperty("java.runtime.version"))
                            .put("Browser", PropertyReader.getProperty("BROWSER_TYPE"))
                            .put("Browser Version", PropertyReader.getProperty("BROWSER_VERSION"))
                            .put("Execution Type.Version", PropertyReader.getProperty("EXECUTION_TYPE"))
                            .put("URL", PropertyReader.getProperty("BASE_WEB"))
                            .build(),String.valueOf(AllurePaths.RESULTS_FOLDER)+ File.separator
            );
             LogsManager.info("Environment set for allure reports");
             AllureDownloadManager.installAllure();

         }
    }

