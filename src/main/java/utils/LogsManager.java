package utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;

public class LogsManager {

    private static final Logger logger = LogManager.getLogger(LogsManager.class);

    private static String basePath() {
        String basePathFolder = System.getProperty("basePath");
        if (basePathFolder == null) {
            return System.getProperty("user.dir") + File.separator + "test-output";
        }
        return basePathFolder;
    }

    public static void createRunDirs(String runFolder) {
        FilesManager.createDirectory(runFolder);
        FilesManager.createDirectory(runFolder + File.separator + "logs");
        FilesManager.createDirectory(runFolder + File.separator + "screenshots");
        FilesManager.createDirectory(runFolder + File.separator + "llm");
    }

    public static void cleanRunLog(String runFolder) {
        FilesManager.clearFiles(runFolder + File.separator + "logs" + File.separator + "logs.log");
    }

    public static void info(String message) { logger.info(message); }
    public static void error(String message) { logger.error(message); }
    public static void warn(String message) { logger.warn(message); }
    public static void debug(String message) { logger.debug(message); }
}
