package delivery.evidence;

import delivery.prompts.PromptCatalog;
import org.json.JSONArray;
import org.json.JSONObject;
import org.openqa.selenium.WebDriver;
import utils.LogsManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Writes Phase 2 failure replay bundle under {@code {tcEvidenceDir}/failure/{stepIndex}/}.
 * Best-effort: always tries meta.json; omits blobs that failed to capture.
 */
public final class FailureReplayWriter {
    private FailureReplayWriter() {
    }

    public record CaptureRequest(
            Path tcEvidenceDir,
            String jobId,
            String tcId,
            int stepIndex,
            String reason,
            String pageUrl,
            String source,
            byte[] screenPng
    ) {
    }

    /** Write failure evidence using current {@link FailureDiagCapture} buffer when present. */
    public static Path write(CaptureRequest req, WebDriver driverOrNull) {
        if (!Phase2EvidenceConfig.failureEvidenceEnabled() || req == null || req.tcEvidenceDir() == null) {
            return null;
        }
        int step = Math.max(0, req.stepIndex());
        Path dir = FailureReplayPaths.failureDir(req.tcEvidenceDir(), step);
        boolean hasScreen = false;
        boolean hasNetwork = false;
        boolean hasConsole = false;
        try {
            Files.createDirectories(dir);
        } catch (Exception e) {
            LogsManager.warn("PHASE2_FAILURE: mkdir " + e.getMessage());
            return null;
        }

        if (req.screenPng() != null && req.screenPng().length > 0) {
            try {
                Files.write(FailureReplayPaths.screen(dir), req.screenPng());
                hasScreen = true;
            } catch (Exception e) {
                LogsManager.warn("PHASE2_FAILURE: screen " + e.getMessage());
            }
        }

        FailureDiagCapture diag = FailureDiagCapture.current();
        if (diag != null && driverOrNull != null && !diag.supported()) {
            diag.pullSeleniumBrowserLogs(driverOrNull);
        }
        List<Map<String, Object>> net = diag == null ? List.of() : diag.snapshotNetwork();
        List<Map<String, Object>> cons = diag == null ? List.of() : diag.snapshotConsole();

        if (!net.isEmpty()) {
            try {
                JSONObject wrap = new JSONObject();
                wrap.put("entries", toJsonArray(net));
                Files.writeString(FailureReplayPaths.network(dir), wrap.toString(2));
                hasNetwork = true;
            } catch (Exception e) {
                LogsManager.warn("PHASE2_FAILURE: network " + e.getMessage());
            }
        }

        if (!cons.isEmpty()) {
            try {
                Files.writeString(FailureReplayPaths.console(dir), toJsonArray(cons).toString(2));
                hasConsole = true;
            } catch (Exception e) {
                LogsManager.warn("PHASE2_FAILURE: console " + e.getMessage());
            }
        }

        try {
            JSONObject meta = new JSONObject();
            meta.put("jobId", nz(req.jobId()));
            meta.put("tcId", nz(req.tcId()));
            meta.put("stepIndex", step);
            meta.put("failedAt", Instant.now().toString());
            meta.put("reason", nz(req.reason()));
            if (req.pageUrl() != null && !req.pageUrl().isBlank()) {
                meta.put("pageUrl", req.pageUrl());
            }
            meta.put("hasScreen", hasScreen);
            meta.put("hasNetwork", hasNetwork);
            meta.put("hasConsole", hasConsole);
            String source = req.source();
            if (source == null || source.isBlank()) {
                source = Phase2Session.source();
            }
            meta.put("source", "execute".equalsIgnoreCase(source) ? "execute" : "prove");
            meta.put("promptsVersion", PromptCatalog.version());
            Files.writeString(FailureReplayPaths.meta(dir), meta.toString(2));
        } catch (Exception e) {
            LogsManager.warn("PHASE2_FAILURE: meta " + e.getMessage());
            return null;
        }
        return dir;
    }

    private static JSONArray toJsonArray(List<Map<String, Object>> rows) {
        JSONArray arr = new JSONArray();
        if (rows == null) {
            return arr;
        }
        for (Map<String, Object> row : rows) {
            if (row == null) {
                continue;
            }
            arr.put(new JSONObject(row));
        }
        return arr;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
