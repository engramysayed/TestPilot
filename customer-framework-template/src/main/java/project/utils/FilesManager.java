package project.utils;

import org.apache.commons.io.FileUtils;
import project.utils.Logs.LogsManager;
import project.utils.dataReader.PropertyReader;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

import static org.apache.commons.io.FileUtils.copyFile;
import static org.apache.commons.io.FileUtils.deleteQuietly;

public class FilesManager {

    private static final String userDir= PropertyReader.getProperty("user.dir")+File.separator;


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

    public static void  cleanDirectory(File file){
            try{
                FileUtils.deleteQuietly(file);
                LogsManager.info("File deleted "+file);
            } catch (Exception e) {
                LogsManager.error("Error cleanDirectory :"+e.getMessage());
            }

    }


    public static void forceDelete(File file){
        try{
            FileUtils.forceDelete(file);
            LogsManager.info("File deleted "+file);
        } catch (Exception e) {
            LogsManager.error(" Error forceDelete file:"+e.getMessage());
        }

    }

    public static void  renameDirectory(String oldName , String newName){
        try{
           var targetFile= new File(oldName);
            String targetDir=targetFile.getParentFile().getAbsolutePath();
            File newFile=new File(targetDir+File.separator+newName);
            if(!targetFile.getPath().equals(newFile.getPath())){
                copyFile(targetFile,newFile);
                deleteQuietly(targetFile);
                LogsManager.info("File renamed from "+targetFile+" to "+newFile);
            }

        } catch (Exception e) {
            LogsManager.error(" Error renaming file:"+e.getMessage());
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
