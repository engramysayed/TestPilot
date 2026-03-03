package utils;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
public class FilesManager {


    public static void  createDirectory(String path){
        try{
             File file=new File(path);
            if(!file.exists()){
                file.mkdirs();
                LogsManager.info("File created "+file);
            }else{
              LogsManager.info("File exist "+file);

            }

        } catch (Exception e) {

          LogsManager.error(" Error creating file:"+e.getMessage());


        }

    }


    public static void writeFile(Path path, String content) {
        try {
            Files.writeString(path, content);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write file: " + path, e);
        }
    }

    public static void clearFiles(String path) {
        try {
            Path logFile = Paths.get(path);
            if (Files.exists(logFile)) {
                Files.write(logFile, new byte[0], StandardOpenOption.TRUNCATE_EXISTING);
                LogsManager.info("File cleared successfully: " + path);
            }
        } catch (Exception e) {
            LogsManager.error("Error clearing file: " + e.getMessage());

        }
    }


}
