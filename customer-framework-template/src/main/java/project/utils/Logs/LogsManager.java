package project.utils.Logs;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import project.utils.reports.AllurePaths;

import java.io.File;
import java.nio.file.Paths;

public class LogsManager {

    public static final String LOGS_PATH = String.valueOf(Paths.get(AllurePaths.userDir+ File.separator+"test-output/Logs"+File.separator));
    private static Logger logger(){
        return LogManager.getLogger(Thread.currentThread().getStackTrace()[3].getClassName());
    }


    public static void info(String message) {
        logger().info(message);
    }
    public static void error(String message) {
        logger().error(message);
    }
    public static void warn(String message) {
        logger().warn(message);
    }
    public static void debug(String message) {
        logger().debug(message);
    }
    public static void fatal(String message) {
        logger().fatal(message);
    }
    public static void trace(String message) {
        logger().trace(message);
    }


}
