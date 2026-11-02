package delivery.evidence;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chromium.HasCdp;
import org.openqa.selenium.devtools.DevTools;
import org.openqa.selenium.devtools.HasDevTools;
import org.openqa.selenium.devtools.v153.log.Log;
import org.openqa.selenium.devtools.v153.log.model.LogEntry;
import org.openqa.selenium.devtools.v153.network.Network;
import org.openqa.selenium.devtools.v153.network.model.LoadingFailed;
import org.openqa.selenium.devtools.v153.network.model.RequestWillBeSent;
import org.openqa.selenium.devtools.v153.network.model.ResponseReceived;
import org.openqa.selenium.logging.LogEntries;
import org.openqa.selenium.logging.LogType;
import utils.LogsManager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Best-effort CDP (or Selenium log) buffer for Phase 2 failure replay.
 * Never throws out of attach/snapshot/close. Active buffer via ThreadLocal.
 */
public final class FailureDiagCapture implements AutoCloseable {
    private static final ThreadLocal<FailureDiagCapture> ACTIVE = new ThreadLocal<>();

    private final List<Map<String, Object>> network = new CopyOnWriteArrayList<>();
    private final List<Map<String, Object>> console = new CopyOnWriteArrayList<>();
    private final boolean supported;
    private DevTools tools;

    private FailureDiagCapture(boolean supported) {
        this.supported = supported;
    }

    public static FailureDiagCapture attach(WebDriver driver) {
        if (!Phase2EvidenceConfig.diagAttachEnabled() || driver == null) {
            return new FailureDiagCapture(false);
        }
        try {
            if (!(driver instanceof HasDevTools hasDevTools)) {
                FailureDiagCapture fb = seleniumFallback(driver);
                ACTIVE.set(fb);
                return fb;
            }
            FailureDiagCapture cap = new FailureDiagCapture(true);
            cap.tools = hasDevTools.getDevTools();
            cap.tools.createSession();
            cap.tools.send(Network.enable(
                    Optional.empty(), Optional.empty(), Optional.empty(),
                    Optional.empty(), Optional.empty()));
            cap.tools.send(Log.enable());
            cap.tools.addListener(Network.requestWillBeSent(), cap::onRequest);
            cap.tools.addListener(Network.responseReceived(), cap::onResponse);
            cap.tools.addListener(Network.loadingFailed(), cap::onLoadingFailed);
            cap.tools.addListener(Log.entryAdded(), cap::onLog);
            if (driver instanceof HasCdp) {
                LogsManager.info("PHASE2_DIAG: CDP network+console attached");
            } else {
                LogsManager.info("PHASE2_DIAG: DevTools network+console attached");
            }
            ACTIVE.set(cap);
            return cap;
        } catch (Exception e) {
            LogsManager.warn("PHASE2_DIAG: CDP unsupported — " + e.getMessage());
            FailureDiagCapture fb = seleniumFallback(driver);
            ACTIVE.set(fb);
            return fb;
        }
    }

    private static FailureDiagCapture seleniumFallback(WebDriver driver) {
        FailureDiagCapture cap = new FailureDiagCapture(false);
        try {
            LogEntries entries = driver.manage().logs().get(LogType.BROWSER);
            if (entries != null) {
                for (var e : entries) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("level", e.getLevel() == null ? "" : e.getLevel().toString());
                    row.put("message", e.getMessage());
                    row.put("timestamp", e.getTimestamp());
                    cap.console.add(row);
                }
            }
        } catch (Exception ignored) {
        }
        return cap;
    }

    public static FailureDiagCapture current() {
        return ACTIVE.get();
    }

    public static void clearCurrent() {
        FailureDiagCapture cur = ACTIVE.get();
        if (cur != null) {
            try {
                cur.close();
            } catch (Exception ignored) {
            }
        }
        ACTIVE.remove();
    }

    private void onRequest(RequestWillBeSent event) {
        try {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("type", "request");
            row.put("requestId", String.valueOf(event.getRequestId()));
            row.put("url", event.getRequest().getUrl());
            row.put("method", event.getRequest().getMethod());
            network.add(row);
            trim(network, 500);
        } catch (Exception ignored) {
        }
    }

    private void onResponse(ResponseReceived event) {
        try {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("type", "response");
            row.put("requestId", String.valueOf(event.getRequestId()));
            row.put("status", event.getResponse().getStatus());
            row.put("url", event.getResponse().getUrl());
            row.put("mimeType", event.getResponse().getMimeType());
            network.add(row);
            trim(network, 500);
        } catch (Exception ignored) {
        }
    }

    private void onLoadingFailed(LoadingFailed event) {
        try {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("type", "loading_failed");
            row.put("requestId", String.valueOf(event.getRequestId()));
            row.put("errorText", event.getErrorText());
            row.put("canceled", event.getCanceled());
            network.add(row);
            trim(network, 500);
        } catch (Exception ignored) {
        }
    }

    private void onLog(LogEntry entry) {
        try {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("level", entry.getLevel() == null ? "" : entry.getLevel().toString());
            row.put("text", entry.getText());
            row.put("source", entry.getSource() == null ? "" : entry.getSource().toString());
            row.put("timestamp", entry.getTimestamp() == null ? null : String.valueOf(entry.getTimestamp()));
            console.add(row);
            trim(console, 400);
        } catch (Exception ignored) {
        }
    }

    private static void trim(List<?> list, int max) {
        while (list.size() > max) {
            list.remove(0);
        }
    }

    public boolean supported() {
        return supported;
    }

    public List<Map<String, Object>> snapshotNetwork() {
        return new ArrayList<>(network);
    }

    public List<Map<String, Object>> snapshotConsole() {
        return new ArrayList<>(console);
    }

    /** Drain browser logs into console buffer (Selenium fallback path). */
    public void pullSeleniumBrowserLogs(WebDriver driver) {
        if (driver == null) {
            return;
        }
        try {
            LogEntries entries = driver.manage().logs().get(LogType.BROWSER);
            if (entries == null) {
                return;
            }
            for (var e : entries) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("level", e.getLevel() == null ? "" : e.getLevel().toString());
                row.put("message", e.getMessage());
                row.put("timestamp", e.getTimestamp());
                console.add(row);
            }
            trim(console, 400);
        } catch (Exception ignored) {
        }
    }

    @Override
    public void close() {
        try {
            if (tools != null) {
                tools.close();
            }
        } catch (Exception ignored) {
        } finally {
            if (ACTIVE.get() == this) {
                ACTIVE.remove();
            }
        }
    }
}
