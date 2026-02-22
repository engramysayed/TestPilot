package utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;

public class LogsManager {

    private static final Logger logger = LogManager.getLogger(LogsManager.class);

    private static String basePath() {
            return System.getProperty("user.dir") + File.separator + "test-output"+ File.separator +"default-run";
    }


    public static void cleanRunLog() {
        FilesManager.clearFiles(basePath() + File.separator + "logs" + File.separator + "logs.log");
    }

    public static void info(String message) { logger.info(message); }
    public static void error(String message) { logger.error(message); }
    public static void warn(String message) { logger.warn(message); }
    public static void debug(String message) { logger.debug(message); }
}
