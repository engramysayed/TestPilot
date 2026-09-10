package delivery.hunt;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chromium.HasCdp;
import org.openqa.selenium.devtools.DevTools;
import org.openqa.selenium.devtools.HasDevTools;
import org.openqa.selenium.devtools.v153.network.Network;
import org.openqa.selenium.devtools.v153.network.model.LoadingFailed;
import org.openqa.selenium.devtools.v153.network.model.ResponseReceived;
import utils.LogsManager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Best-effort CDP network failure capture. Never throws out of attach/snapshot.
 */
public final class HuntNetworkCapture implements AutoCloseable {
    private final List<Map<String, Object>> failures = new CopyOnWriteArrayList<>();
    private final boolean supported;
    private DevTools tools;

    private HuntNetworkCapture(boolean supported) {
        this.supported = supported;
    }

    public static HuntNetworkCapture attach(WebDriver driver) {
        if (driver == null) {
            return new HuntNetworkCapture(false);
        }
        try {
            if (!(driver instanceof HasDevTools hasDevTools)) {
                return new HuntNetworkCapture(false);
            }
            HuntNetworkCapture cap = new HuntNetworkCapture(true);
            cap.tools = hasDevTools.getDevTools();
            cap.tools.createSession();
            cap.tools.send(Network.enable(
                    Optional.empty(), Optional.empty(), Optional.empty(),
                    Optional.empty(), Optional.empty()));
            cap.tools.addListener(Network.responseReceived(), cap::onResponse);
            cap.tools.addListener(Network.loadingFailed(), cap::onLoadingFailed);
            // Touch HasCdp so Chromium paths stay warm when available
            if (driver instanceof HasCdp) {
                LogsManager.info("HUNT_NETWORK: CDP session attached (devtools-v153)");
            } else {
                LogsManager.info("HUNT_NETWORK: CDP session attached");
            }
            return cap;
        } catch (Exception e) {
            LogsManager.warn("HUNT_NETWORK: unsupported — " + e.getMessage());
            return new HuntNetworkCapture(false);
        }
    }

    private void onResponse(ResponseReceived event) {
        try {
            int status = event.getResponse().getStatus();
            if (status < 400) {
                return;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("type", "http_error");
            row.put("status", status);
            row.put("url", event.getResponse().getUrl());
            row.put("mimeType", event.getResponse().getMimeType());
            failures.add(row);
        } catch (Exception ignored) {
        }
    }

    private void onLoadingFailed(LoadingFailed event) {
        try {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("type", "loading_failed");
            row.put("errorText", event.getErrorText());
            row.put("canceled", event.getCanceled());
            String err = event.getErrorText() == null ? "" : event.getErrorText();
            if (err.toLowerCase(java.util.Locale.ROOT).contains("net::err")) {
                row.put("type", "net_error");
            }
            failures.add(row);
        } catch (Exception ignored) {
        }
    }

    public boolean supported() {
        return supported;
    }

    public List<Map<String, Object>> snapshotAndClear() {
        List<Map<String, Object>> copy = new ArrayList<>(failures);
        failures.clear();
        return copy;
    }

    public String statusLabel() {
        return supported ? "enabled" : "unsupported";
    }

    @Override
    public void close() {
        try {
            if (tools != null) {
                tools.close();
            }
        } catch (Exception ignored) {
        }
    }
}
