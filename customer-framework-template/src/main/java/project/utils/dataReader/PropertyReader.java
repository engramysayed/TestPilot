package project.utils.dataReader;

import org.apache.commons.io.FileUtils;
import project.utils.Logs.LogsManager;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collection;
import java.util.Properties;

public class PropertyReader {

    private static final Properties FILE_PROPS = new Properties();

    public static final String OVERRIDE_FILE_NAME = "delivery-testdata.override.properties";
    private static final File OVERRIDE_FILE =
            new File("src/test/resources/test-data/" + OVERRIDE_FILE_NAME);

    public static void loadProperties() {
        try {
            Properties properties = new Properties();
            loadFromDir(properties, new File("src/main/resources/"));
            loadFromDir(properties, new File("src/test/resources/"));
            loadOverride(properties);
            synchronized (FILE_PROPS) {
                FILE_PROPS.clear();
                FILE_PROPS.putAll(properties);
            }
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
            if (OVERRIDE_FILE_NAME.equals(file.getName())) {
                return;
            }
            try {
                loadFile(properties, file);
            } catch (Exception e) {
                LogsManager.error("Error loading properties file: " + file.getName() + " - " + e.getMessage());
            }
        });
    }

    private static void loadOverride(Properties properties) {
        if (!OVERRIDE_FILE.isFile()) {
            return;
        }
        try {
            loadFile(properties, OVERRIDE_FILE);
        } catch (IOException e) {
            LogsManager.error("Error loading override properties: " + e.getMessage());
        }
    }

    /** UTF-8 properties load. {@code Properties.load(InputStream)} is ISO-8859-1 and must not be used. */
    public static void loadFile(Properties properties, File file) throws IOException {
        try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
    }

    public static String getProperty(String key) {
        try {
            String override = System.getProperty(key);
            if (override != null) {
                return override;
            }
            synchronized (FILE_PROPS) {
                return FILE_PROPS.getProperty(key);
            }
        }catch (Exception e) {
            LogsManager.error("Error getting property: " + key + " - " + e.getMessage());
            return null;
        }
     }
}
