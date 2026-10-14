package project.utils.dataReader;

import project.config.MobileProperties;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Property loader. UTF-8. Precedence: CLI {@code -D} → local resources → defaults.
 * Secrets are never loaded into this map.
 */
public final class PropertyReader {

    private static MobileProperties cached;

    private PropertyReader() {}

    public static void loadProperties() {
        Map<String, String> map = new LinkedHashMap<>();
        loadResource(map, "config/appium.properties");
        loadResource(map, "config/profiles.properties");
        loadResource(map, "test-data/mobile-testdata.properties");
        // CLI -D overrides (non-secret keys only)
        for (Map.Entry<Object, Object> e : System.getProperties().entrySet()) {
            String key = String.valueOf(e.getKey());
            if (key.startsWith("mobile.") && !project.utils.reports.Secrets.looksLikeSecretKey(key)) {
                map.put(key, String.valueOf(e.getValue()));
            }
        }
        cached = new MobileProperties(map);
    }

    public static void resetForTests() {
        cached = null;
    }

    public static void loadFromMapForTests(Map<String, String> map) {
        cached = new MobileProperties(map);
    }

    public static MobileProperties mobileProperties() {
        if (cached == null) {
            loadProperties();
        }
        return cached;
    }

    public static String required(String key) {
        return mobileProperties().require(key);
    }

    private static void loadResource(Map<String, String> into, String resource) {
        try (InputStream in = PropertyReader.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                return;
            }
            Properties p = new Properties();
            p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            for (String name : p.stringPropertyNames()) {
                if (project.utils.reports.Secrets.looksLikeSecretKey(name)) {
                    continue;
                }
                into.put(name, p.getProperty(name));
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load " + resource, e);
        }
    }
}
