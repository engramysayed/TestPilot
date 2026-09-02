package project.utils.reports;

import project.utils.dataReader.PropertyReader;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;


public class AllurePaths {

    public static final Path userDir= Paths.get(PropertyReader.getProperty("user.dir"), File.separator);
    public static final Path userHome= Paths.get(PropertyReader.getProperty("user.home"), File.separator);


    public static final Path RESULTS_FOLDER =
            Paths.get(System.getProperty("allure.results.directory",
                    userDir.resolve("test-output").resolve("allure-results").toString()
            ));


    public static final Path REPORT_PATH =
            Paths.get(String.valueOf(userDir), "test-output", "reports", File.separator);

    public static final Path FULL_REPORT_PATH =
            Paths.get(String.valueOf(userDir), "test-output", "full-report", File.separator);


    public static final Path HISTORY_FOLDER =
            Paths.get(FULL_REPORT_PATH.toString(), "history", File.separator);

    public static final Path RESULTS_HISTORY_FOLDER =
            Paths.get(RESULTS_FOLDER.toString(), "history", File.separator);


    public static final String INDEX_HTML = "index.html";
    public static final String REPORT_PREFIX = "AllureReport_";
    public static final String REPORT_EXTENSION = ".html";


    public static final String ALLURE_ZIP_BASE_URL =
            "https://repo.maven.apache.org/maven2/io/qameta/allure/allure-commandline/";


    public static final Path EXTRACTION_DIR =
            Paths.get(String.valueOf(userHome), ".m2/repository/allure", File.separator);

}
