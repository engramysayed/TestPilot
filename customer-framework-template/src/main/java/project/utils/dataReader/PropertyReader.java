package project.utils.dataReader;

import org.apache.commons.io.FileUtils;
import project.utils.Logs.LogsManager;

import java.io.File;
import java.io.FileInputStream;
import java.util.Collection;
import java.util.Properties;

public class PropertyReader {

    public static  void loadProperties() {
        try {
            Properties properties = new Properties();
            loadFromDir(properties, new File("src/main/resources/"));
            loadFromDir(properties, new File("src/test/resources/"));
            properties.putAll(System.getProperties());
            System.getProperties().putAll(properties);
         } catch (Exception e) {
            LogsManager.error("Error loading properties "+ e.getMessage());
        }

     }

    private static void loadFromDir(Properties properties, File dir) {
        if (dir == null || !dir.isDirectory()) {
            return;
        }
        Collection<File> propertiesFile = FileUtils.listFiles(dir, new String[]{"properties"}, true);
        propertiesFile.forEach(file -> {
            try {
                properties.load(FileUtils.openInputStream(file));
            } catch (Exception e) {
                LogsManager.error("Error loading properties file: " + file.getName() + " - " + e.getMessage());
            }
        });
    }

    public static String getProperty(String key) {
        try {
            return System.getProperty(key);
        }catch (Exception e) {
            LogsManager.error("Error getting property: " + key + " - " + e.getMessage());
            return null;
        }
     }
}
