package project.utils.reports;
import io.qameta.allure.Allure;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import project.utils.Logs.LogsManager;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

public class AllureAttachmentManager {

    public static void attachScreenShot(String name , String path){
        try {
            Path screenShot= Path.of(path);
            if (Files.exists(screenShot)) {
                Allure.addAttachment(name, Files.newInputStream(screenShot));
            } else {
                LogsManager.error("Screen shot file not found: " + path);
            }
        } catch (Exception e) {
            LogsManager.error("Failed to attach screen shot: " + e.getMessage());
        }
    }

    public static void attachLogs(){
        try{
             //LogManager.shutdown();
            File logFile = new File(LogsManager.LOGS_PATH+File.separator+"logs.log");
            ((LoggerContext) LogManager.getContext(false)).reconfigure();
            if (logFile.exists()) {
                Allure.addAttachment("logs.log", Files.readString(logFile.toPath()));
            } else {
                LogsManager.error("Log file not found: " + logFile.toString());
            }
        } catch (Exception e) {
            LogsManager.error("Failed to attach logs: " + e.getMessage());
        }

    }



}
