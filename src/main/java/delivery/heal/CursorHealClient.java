package delivery.heal;

import org.json.JSONArray;
import org.json.JSONObject;
import utils.LogsManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Invokes the Node Cursor-heal sidecar over stdin/stdout JSON.
 * Model id is hard-locked to Auto inside the sidecar — never pinned here.
 */
public class CursorHealClient {
    private static final long DEFAULT_TIMEOUT_SEC = 120;

    private final boolean enabled;
    private final String command;
    private final long timeoutSec;

    public CursorHealClient() {
        ensureDeliveryPropsLoaded();
        this.enabled = resolveEnabled();
        this.command = resolveCommand();
        this.timeoutSec = DEFAULT_TIMEOUT_SEC;
    }

    public CursorHealClient(boolean enabled, String command, long timeoutSec) {
        this.enabled = enabled;
        this.command = command == null || command.isBlank()
                ? "node tools/cursor-heal/heal.mjs"
                : command.trim();
        this.timeoutSec = timeoutSec <= 0 ? DEFAULT_TIMEOUT_SEC : timeoutSec;
    }

    private static void ensureDeliveryPropsLoaded() {
        try {
            utils.PropertyReader.loadProperties();
        } catch (Exception ignored) {
            // optional — env vars still work
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Ask Cursor Auto to pick one candidateId from the shortlist.
     * Returns blank when skipped / failed (never invents ids).
     */
    public String pickCandidateId(
            String intentText,
            String failureReason,
            String shortlistTable,
            String slimHtmlExcerpt,
            Path screenshotPathOrNull
    ) {
        return pickCandidateId(
                intentText, failureReason, shortlistTable, slimHtmlExcerpt,
                screenshotPathOrNull, List.of());
    }

    public String pickCandidateId(
            String intentText,
            String failureReason,
            String shortlistTable,
            String slimHtmlExcerpt,
            Path screenshotPathOrNull,
            List<String> priorSteps
    ) {
        JSONObject req = new JSONObject();
        req.put("mode", "pick");
        req.put("intent", intentText == null ? "" : intentText);
        req.put("failureReason", failureReason == null ? "" : failureReason);
        req.put("shortlist", shortlistTable == null ? "" : shortlistTable);
        req.put("slimHtmlExcerpt", slimHtmlExcerpt == null ? "" : slimHtmlExcerpt);
        req.put("priorSteps", priorSteps == null ? List.of() : priorSteps);
        putVisionAttempts(req);
        if (screenshotPathOrNull != null && Files.isRegularFile(screenshotPathOrNull)) {
            req.put("screenshotPath", screenshotPathOrNull.toAbsolutePath().toString());
        }
        String raw = invoke(req);
        String id = parseCandidateId(raw);
        if (id.isBlank() && !raw.isBlank()) {
            LogsManager.warn("CURSOR_HEAL_SKIPPED: no candidateId in sidecar output: " + trim(raw, 200));
        } else if (!id.isBlank()) {
            LogsManager.info("CURSOR_HEAL: Auto picked candidateId=" + id);
        }
        return id;
    }

    /**
     * Escalation after the local pick failed: Cursor sees the shortlist, the HTML and the
     * screenshot, and may answer with a row or with a locator of its own. Returns the raw JSON so
     * both shapes go through the same validation as any other healed step.
     */
    public String solve(
            String intentText,
            String failureReason,
            String shortlistTable,
            String slimHtmlExcerpt,
            Path screenshotPathOrNull,
            List<String> priorSteps
    ) {
        JSONObject req = new JSONObject();
        req.put("mode", "solve");
        req.put("intent", intentText == null ? "" : intentText);
        req.put("failureReason", failureReason == null ? "" : failureReason);
        req.put("shortlist", shortlistTable == null ? "" : shortlistTable);
        req.put("slimHtmlExcerpt", slimHtmlExcerpt == null ? "" : slimHtmlExcerpt);
        req.put("priorSteps", priorSteps == null ? List.of() : priorSteps);
        putVisionAttempts(req);
        putExcelOpenPath(req, failureReason);
        if (screenshotPathOrNull != null && Files.isRegularFile(screenshotPathOrNull)) {
            req.put("screenshotPath", screenshotPathOrNull.toAbsolutePath().toString());
        }
        return invoke(req);
    }

    /** One-shot free-invent request. Returns the raw JSON response for validation by FreeInventHealer. */
    public String inventSteps(
            String intentText,
            String failureReason,
            List<String> priorSteps,
            String slimHtmlExcerpt,
            Path screenshotPathOrNull
    ) {
        JSONObject req = new JSONObject();
        req.put("mode", "invent");
        req.put("intent", intentText == null ? "" : intentText);
        req.put("failureReason", failureReason == null ? "" : failureReason);
        req.put("priorSteps", priorSteps == null ? List.of() : priorSteps);
        req.put("slimHtmlExcerpt", slimHtmlExcerpt == null ? "" : slimHtmlExcerpt);
        putVisionAttempts(req);
        putExcelOpenPath(req, failureReason);
        if (screenshotPathOrNull != null && Files.isRegularFile(screenshotPathOrNull)) {
            req.put("screenshotPath", screenshotPathOrNull.toAbsolutePath().toString());
        }
        return invoke(req);
    }

    public String inventSteps(
            String intentText,
            String failureReason,
            List<String> priorSteps,
            String slimHtmlExcerpt,
            Path screenshotPathOrNull,
            String excelOpenPath
    ) {
        String reason = failureReason == null ? "" : failureReason;
        if (excelOpenPath != null && !excelOpenPath.isBlank()
                && !reason.contains("Excel open-path")) {
            reason = reason + "\nExcel open-path (only allowed navigation target): " + excelOpenPath.trim();
        }
        return inventSteps(intentText, reason, priorSteps, slimHtmlExcerpt, screenshotPathOrNull);
    }

    static void putVisionAttempts(JSONObject req) {
        if (req == null) {
            return;
        }
        List<String> lines = delivery.vision.VisionAttemptLog.linesForHeal();
        if (!lines.isEmpty()) {
            req.put("visionAttempts", lines);
        }
    }

    static void putExcelOpenPath(JSONObject req, String failureReason) {
        if (req == null) {
            return;
        }
        String path = extractExcelOpenPath(failureReason);
        if (!path.isBlank()) {
            req.put("excelOpenPath", path);
        }
    }

    static String extractExcelOpenPath(String failureReason) {
        if (failureReason == null || failureReason.isBlank()) {
            return "";
        }
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("Excel open-path[^:]*:\\s*(\\S+)")
                .matcher(failureReason);
        return m.find() ? m.group(1).trim() : "";
    }

    private String invoke(JSONObject req) {
        if (!enabled) {
            LogsManager.warn("CURSOR_HEAL_SKIPPED: delivery.cursor-heal.enabled=false");
            return "";
        }
        String apiKey = System.getenv("CURSOR_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = System.getProperty("CURSOR_API_KEY", "");
        }
        if (apiKey == null || apiKey.isBlank()) {
            LogsManager.warn("CURSOR_HEAL_SKIPPED: CURSOR_API_KEY not set");
            return "";
        }
        try {
            List<String> cmd = parseCommand(command);
            ProcessBuilder pb = new ProcessBuilder(cmd);
            // D8: run from repo root so relative sidecar paths resolve
            Path cwd = resolveRepoRoot(command);
            if (cwd != null && Files.isDirectory(cwd)) {
                pb.directory(cwd.toFile());
            }
            // D9: keep stderr separate; parse stdout only
            pb.redirectErrorStream(false);
            pb.environment().put("CURSOR_API_KEY", apiKey);
            Process proc = pb.start();
            try (OutputStreamWriter w = new OutputStreamWriter(proc.getOutputStream(), StandardCharsets.UTF_8)) {
                w.write(req.toString());
                w.flush();
            }
            StringBuilder out = new StringBuilder();
            StringBuilder err = new StringBuilder();
            Thread errReader = new Thread(() -> {
                try (BufferedReader r = new BufferedReader(
                        new InputStreamReader(proc.getErrorStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        err.append(line).append('\n');
                    }
                } catch (Exception ignored) {
                }
            }, "cursor-heal-stderr");
            errReader.setDaemon(true);
            errReader.start();
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    out.append(line).append('\n');
                }
            }
            boolean finished = proc.waitFor(timeoutSec, TimeUnit.SECONDS);
            try {
                errReader.join(2000);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
            if (!err.isEmpty()) {
                LogsManager.warn("CURSOR_HEAL_STDERR: " + trim(err.toString().trim(), 400));
            }
            if (!finished) {
                proc.destroyForcibly();
                LogsManager.warn("CURSOR_HEAL_SKIPPED: sidecar timed out after " + timeoutSec + "s");
                return "";
            }
            int code = proc.exitValue();
            String raw = out.toString().trim();
            if (code != 0) {
                LogsManager.warn("CURSOR_HEAL_SKIPPED: sidecar exit=" + code + " out=" + trim(raw, 200));
                return "";
            }
            return raw;
        } catch (Exception e) {
            LogsManager.warn("CURSOR_HEAL_SKIPPED: " + e.getMessage());
            return "";
        }
    }

    /** Prefer user.dir; if command contains tools/cursor-heal, walk up to find that folder's parent. */
    static Path resolveRepoRoot(String command) {
        Path userDir = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        if (command != null && command.contains("tools/cursor-heal")
                || (command != null && command.contains("tools\\cursor-heal"))) {
            Path probe = userDir.resolve("tools/cursor-heal/heal.mjs");
            if (Files.isRegularFile(probe)) {
                return userDir;
            }
            Path walk = userDir;
            for (int i = 0; i < 4; i++) {
                Path p = walk.resolve("tools/cursor-heal/heal.mjs");
                if (Files.isRegularFile(p)) {
                    return walk;
                }
                walk = walk.getParent();
                if (walk == null) {
                    break;
                }
            }
        }
        return userDir;
    }

    static String parseCandidateId(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        // Prefer last JSON object in output (sidecar may log lines before JSON)
        int start = raw.lastIndexOf('{');
        int end = raw.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return "";
        }
        try {
            JSONObject obj = new JSONObject(raw.substring(start, end + 1));
            return obj.optString("candidateId", "").trim();
        } catch (RuntimeException e) {
            return "";
        }
    }

    private static boolean resolveEnabled() {
        String p = System.getProperty("delivery.cursor-heal.enabled");
        if (p == null || p.isBlank()) {
            p = System.getenv("DELIVERY_CURSOR_HEAL_ENABLED");
        }
        if (p == null || p.isBlank()) {
            return true; // enabled by default; still skips without API key
        }
        return "true".equalsIgnoreCase(p.trim()) || "1".equals(p.trim());
    }

    private static String resolveCommand() {
        String p = System.getProperty("delivery.cursor-heal.command");
        if (p == null || p.isBlank()) {
            p = System.getenv("DELIVERY_CURSOR_HEAL_COMMAND");
        }
        if (p == null || p.isBlank()) {
            return "node tools/cursor-heal/heal.mjs";
        }
        return p.trim();
    }

    private static List<String> parseCommand(String command) {
        List<String> parts = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuote = false;
        for (int i = 0; i < command.length(); i++) {
            char c = command.charAt(i);
            if (c == '"') {
                inQuote = !inQuote;
            } else if (Character.isWhitespace(c) && !inQuote) {
                if (!cur.isEmpty()) {
                    parts.add(cur.toString());
                    cur.setLength(0);
                }
            } else {
                cur.append(c);
            }
        }
        if (!cur.isEmpty()) {
            parts.add(cur.toString());
        }
        if (parts.isEmpty()) {
            parts.add("node");
            parts.add("tools/cursor-heal/heal.mjs");
        }
        return parts;
    }

    private static String trim(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    /** Test helper: validate a shortlist table string contains candidate ids (unused by runtime). */
    static List<String> idsFromShortlistTable(String table) {
        List<String> ids = new ArrayList<>();
        if (table == null || table.isBlank()) {
            return ids;
        }
        for (String line : table.split("\n")) {
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("id ")) {
                continue;
            }
            int bar = t.indexOf('|');
            String id = (bar > 0 ? t.substring(0, bar) : t).trim();
            if (!id.isBlank()) {
                ids.add(id);
            }
        }
        return ids;
    }

    /** Package-visible for tests — unused JSON array shape guard. */
    static boolean looksLikeShortlistArray(String json) {
        try {
            new JSONArray(json);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
