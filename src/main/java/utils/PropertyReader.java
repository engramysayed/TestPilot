package utils;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.util.Collection;
import java.util.Properties;

public class PropertyReader {

    public static  void loadProperties() {
        try {
            Properties properties = new Properties();
            Collection<File> propertiesFile;
            propertiesFile = FileUtils.listFiles(new File("src/main/resources/"),
                    new String []{"properties"}, true);
            propertiesFile.forEach(file -> {
                try {
                    properties.load(FileUtils.openInputStream(file));
                } catch (Exception e) {
                    LogsManager.error("Error loading properties file: " + file.getName() + " - " + e.getMessage());
                }
                properties.putAll(System.getProperties());
                System.getProperties().putAll(properties);
            });
         } catch (Exception e) {
            LogsManager.error("Error loading properties "+ e.getMessage());
        }

     }

    public static String getProperty(String key) {
        try {
            return System.getProperty(key);
        }catch (Exception e) {
            LogsManager.error("Error getting property: " + key + " - " + e.getMessage());
            return null;
        }
     }

     public static void setProperty(String key, String value) {
        try {
            System.setProperty(key, value);
        }catch (Exception e) {
            LogsManager.error("Error setting property: " + key + " - " + e.getMessage());
        }
     }
}
