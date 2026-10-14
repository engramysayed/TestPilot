package delivery.mobile.adb;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses {@code pm path} / {@code dumpsys package} snippets for installed identity. */
public final class InstalledPackageParser {
    private static final Pattern PM_PATH = Pattern.compile("(?m)^package:(.+)$");
    private static final Pattern VERSION_CODE = Pattern.compile("versionCode[=:](\\d+)");
    private static final Pattern VERSION_NAME = Pattern.compile("versionName[=:]([^\\s\\r\\n]+)");

    private InstalledPackageParser() {
    }

    public static boolean pmPathIndicatesInstalled(String pmPathOutput) {
        if (pmPathOutput == null || pmPathOutput.isBlank()) {
            return false;
        }
        Matcher m = PM_PATH.matcher(pmPathOutput.trim());
        return m.find();
    }

    public static Long parseVersionCode(String dumpsys) {
        if (dumpsys == null) {
            return null;
        }
        Matcher m = VERSION_CODE.matcher(dumpsys);
        if (m.find()) {
            try {
                return Long.parseLong(m.group(1));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    public static String parseVersionName(String dumpsys) {
        if (dumpsys == null) {
            return null;
        }
        Matcher m = VERSION_NAME.matcher(dumpsys);
        if (m.find()) {
            String raw = m.group(1);
            // dumpsys may quote versionName
            if (raw.length() >= 2 && raw.startsWith("\"") && raw.endsWith("\"")) {
                return raw.substring(1, raw.length() - 1);
            }
            return raw;
        }
        return null;
    }
}
