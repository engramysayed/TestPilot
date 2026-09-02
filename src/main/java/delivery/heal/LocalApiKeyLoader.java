package delivery.heal;

import utils.LogsManager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loads gitignored local API keys into system properties so CLI conversions see the same
 * keys as {@code start-portal.bat}. Never logs secret values.
 */
public final class LocalApiKeyLoader {
    private static final Pattern BAT_SET = Pattern.compile(
            "^\\s*set\\s+\"?([A-Za-z_][A-Za-z0-9_]*)=(.*)\"?\\s*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PLACEHOLDER = Pattern.compile(
            "(?i)paste_|your_key_here|changeme|todo");

    private LocalApiKeyLoader() {
    }

    /** Walks up from {@code user.dir} looking for {@code api-keys.local.bat}. */
    public static int loadFromWorkingTree() {
        Path start = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        int loaded = 0;
        Path cursor = start;
        for (int i = 0; i < 5 && cursor != null; i++) {
            loaded = loadIntoSystemProperties(cursor);
            if (loaded > 0) {
                return loaded;
            }
            cursor = cursor.getParent();
        }
        return 0;
    }

    public static int loadIntoSystemProperties(Path projectRoot) {
        if (projectRoot == null) {
            return 0;
        }
        Path bat = projectRoot.resolve("api-keys.local.bat");
        Path props = projectRoot.resolve("api-keys.local.properties");
        Map<String, String> keys = new LinkedHashMap<>();
        readBat(bat, keys);
        readProperties(props, keys);
        int applied = 0;
        for (Map.Entry<String, String> e : keys.entrySet()) {
            if (apply(e.getKey(), e.getValue())) {
                applied++;
            }
        }
        if (applied > 0) {
            LogsManager.info("Local API keys loaded: " + applied + " (values not printed)");
        }
        return applied;
    }

    private static boolean apply(String key, String value) {
        if (key == null || key.isBlank() || value == null || value.isBlank()) {
            return false;
        }
        if (PLACEHOLDER.matcher(value).find()) {
            return false;
        }
        String env = System.getenv(key);
        if (env != null && !env.isBlank()) {
            return false;
        }
        String existing = System.getProperty(key);
        if (existing != null && !existing.isBlank()) {
            return false;
        }
        System.setProperty(key, value);
        return true;
    }

    private static void readBat(Path bat, Map<String, String> out) {
        if (bat == null || !Files.isRegularFile(bat)) {
            return;
        }
        try {
            for (String line : Files.readAllLines(bat, StandardCharsets.UTF_8)) {
                if (line == null) {
                    continue;
                }
                String trimmed = line.trim();
                if (trimmed.startsWith("REM") || trimmed.startsWith("::") || trimmed.startsWith("#")) {
                    continue;
                }
                Matcher m = BAT_SET.matcher(trimmed);
                if (!m.matches()) {
                    continue;
                }
                String key = m.group(1);
                String raw = m.group(2);
                if (raw.endsWith("\"") && !raw.startsWith("\"")) {
                    raw = raw.substring(0, raw.length() - 1);
                }
                if (raw.startsWith("\"") && raw.endsWith("\"") && raw.length() >= 2) {
                    raw = raw.substring(1, raw.length() - 1);
                }
                out.putIfAbsent(key, raw.trim());
            }
        } catch (IOException e) {
            LogsManager.warn("Could not read api-keys.local.bat: " + e.getMessage());
        }
    }

    private static void readProperties(Path props, Map<String, String> out) {
        if (props == null || !Files.isRegularFile(props)) {
            return;
        }
        try {
            for (String line : Files.readAllLines(props, StandardCharsets.UTF_8)) {
                if (line == null || line.isBlank() || line.trim().startsWith("#")) {
                    continue;
                }
                int eq = line.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                String key = line.substring(0, eq).trim();
                String value = line.substring(eq + 1).trim();
                if (!key.isBlank()) {
                    out.putIfAbsent(key, value);
                }
            }
        } catch (IOException e) {
            LogsManager.warn("Could not read api-keys.local.properties: " + e.getMessage());
        }
    }
}
