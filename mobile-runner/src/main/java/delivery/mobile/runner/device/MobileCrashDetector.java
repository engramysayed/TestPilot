package delivery.mobile.runner.device;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Classifies bounded {@code adb logcat -b crash} output since step start for the app package.
 * Maps FATAL EXCEPTION / ANR / native Fatal signal → APP_CRASHED. Other packages ignored.
 */
public final class MobileCrashDetector {
    public static final int MAX_LINES = 500;
    public static final int MAX_BYTES = 64 * 1024;

    private static final Pattern FATAL = Pattern.compile("FATAL EXCEPTION", Pattern.CASE_INSENSITIVE);
    private static final Pattern ANR = Pattern.compile("ANR in\\s+(\\S+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern NATIVE = Pattern.compile("Fatal signal\\s+(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PROCESS = Pattern.compile("Process:\\s*([^,\\s]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PKG_IN_LINE = Pattern.compile("([a-zA-Z0-9_.]+)");

    /**
     * @param logcatCrashBuffer raw text from {@code adb logcat -b crash} (already bounded by caller)
     * @param stepStartedAtEpochMs step start; lines with older epoch stamps (when parseable) ignored
     */
    public CrashDetectionResult detect(String appPackage, long stepStartedAtEpochMs, String logcatCrashBuffer) {
        if (appPackage == null || appPackage.isBlank() || logcatCrashBuffer == null || logcatCrashBuffer.isBlank()) {
            return CrashDetectionResult.none();
        }
        String bounded = bound(logcatCrashBuffer);
        List<String> lines = List.of(bounded.split("\\R", -1));
        StringBuilder relevant = new StringBuilder();
        String kind = null;

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (isPreStep(line, stepStartedAtEpochMs)) continue;

            Matcher anr = ANR.matcher(line);
            if (anr.find()) {
                String pkg = anr.group(1);
                if (packageMatches(appPackage, pkg)) {
                    kind = "ANR";
                    appendWindow(relevant, lines, i);
                    break;
                }
                continue;
            }
            if (FATAL.matcher(line).find()) {
                String pkg = findProcessPackage(lines, i);
                if (packageMatches(appPackage, pkg)) {
                    kind = "FATAL_EXCEPTION";
                    appendWindow(relevant, lines, i);
                    break;
                }
                continue;
            }
            Matcher nat = NATIVE.matcher(line);
            if (nat.find()) {
                String pkg = findProcessPackage(lines, i);
                if (pkg.isEmpty() || packageMatches(appPackage, pkg)) {
                    // native lines often include package nearby
                    if (pkg.isEmpty()) {
                        // search nearby lines for package
                        pkg = findProcessPackage(lines, i);
                    }
                    if (packageMatches(appPackage, pkg) || lineContainsPackage(line, appPackage)
                            || nearbyContainsPackage(lines, i, appPackage)) {
                        kind = "NATIVE_SIGNAL";
                        appendWindow(relevant, lines, i);
                        break;
                    }
                }
            }
        }

        if (kind == null) return CrashDetectionResult.none();
        String excerpt = redact(relevant.toString());
        return new CrashDetectionResult(true, kind, excerpt, "mem:logcat-crash:" + excerpt.length());
    }

    static String bound(String raw) {
        byte[] bytes = raw.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String s = raw;
        if (bytes.length > MAX_BYTES) {
            s = new String(bytes, 0, MAX_BYTES, java.nio.charset.StandardCharsets.UTF_8);
        }
        String[] lines = s.split("\\R", -1);
        if (lines.length > MAX_LINES) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < MAX_LINES; i++) {
                if (i > 0) sb.append('\n');
                sb.append(lines[i]);
            }
            return sb.toString();
        }
        return s;
    }

    private static boolean isPreStep(String line, long stepStartedAtEpochMs) {
        // Optional epoch prefix: epochMs|...
        int bar = line.indexOf('|');
        if (bar > 0 && bar < 20) {
            try {
                long ts = Long.parseLong(line.substring(0, bar).trim());
                return ts < stepStartedAtEpochMs;
            } catch (NumberFormatException ignored) {
                return false;
            }
        }
        return false;
    }

    private static String findProcessPackage(List<String> lines, int at) {
        for (int i = at; i < Math.min(lines.size(), at + 8); i++) {
            Matcher m = PROCESS.matcher(lines.get(i));
            if (m.find()) return m.group(1).trim();
            // "Cmd line: com.example.app"
            String l = lines.get(i);
            int idx = l.toLowerCase(Locale.ROOT).indexOf("cmd line:");
            if (idx >= 0) {
                return l.substring(idx + "cmd line:".length()).trim();
            }
        }
        return "";
    }

    private static boolean packageMatches(String app, String candidate) {
        if (candidate == null || candidate.isBlank()) return false;
        String a = app.trim();
        String c = candidate.trim();
        return c.equals(a) || c.startsWith(a + ":") || c.startsWith(a + "/");
    }

    private static boolean lineContainsPackage(String line, String app) {
        return line != null && line.contains(app);
    }

    private static boolean nearbyContainsPackage(List<String> lines, int at, String app) {
        for (int i = Math.max(0, at - 2); i < Math.min(lines.size(), at + 6); i++) {
            if (lines.get(i).contains(app)) return true;
        }
        return false;
    }

    private static void appendWindow(StringBuilder sb, List<String> lines, int at) {
        int from = Math.max(0, at);
        int to = Math.min(lines.size(), at + 12);
        for (int i = from; i < to; i++) {
            sb.append(lines.get(i)).append('\n');
        }
    }

    private static String redact(String excerpt) {
        // strip emails / long tokens lightly
        return excerpt
                .replaceAll("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}", "(redacted-email)")
                .replaceAll("(?i)password[=:]\\S+", "password=(redacted)");
    }
}
