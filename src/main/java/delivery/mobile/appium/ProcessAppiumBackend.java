package delivery.mobile.appium;

import delivery.mobile.MobileProperties;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/**
 * Spawns the Appium <strong>CLI</strong> via argument arrays (no Appium jars on portal).
 * Tracks only processes it started; {@link #stop} refuses non-owned PIDs and never runs
 * {@code adb kill-server}.
 */
public final class ProcessAppiumBackend implements AppiumProcessBackend {
    private final MobileProperties props;
    /** processRef → live Process */
    private final ConcurrentHashMap<String, Process> live = new ConcurrentHashMap<>();
    /** processRef → OwnedAppiumProcess */
    private final ConcurrentHashMap<String, OwnedAppiumProcess> owned = new ConcurrentHashMap<>();
    /** AE-L2: injectable for offline unit tests (default = HTTP /status on loopback). */
    private volatile Predicate<String> readyChecker = ProcessAppiumBackend::httpStatusReady;

    public ProcessAppiumBackend(MobileProperties props) {
        this.props = props == null ? new MobileProperties() : props;
    }

    /** Package-visible for tests: replace HTTP /status with a deterministic probe. */
    void setReadyChecker(Predicate<String> readyChecker) {
        this.readyChecker = readyChecker == null ? ProcessAppiumBackend::httpStatusReady : readyChecker;
    }

    @Override
    public boolean isAvailable() {
        String bin = props.getAppiumPath();
        return bin != null && !bin.isBlank();
    }

    @Override
    public OwnedAppiumProcess start(AppiumStartRequest request) throws AppiumLifecycleException {
        if (request == null || request.getPorts() == null) {
            throw new AppiumLifecycleException(AppiumErrorCode.INVALID_STATE, "start request required");
        }
        if (!isAvailable()) {
            throw new AppiumLifecycleException(AppiumErrorCode.APPIUM_UNAVAILABLE, "appium path not configured");
        }
        if (request.isCancelled()) {
            throw new AppiumLifecycleException(AppiumErrorCode.CANCELLED_WHILE_STARTING, "cancel before start");
        }
        List<String> cmd = buildCommand(request);
        AppiumProcessKillPolicy.rejectForbiddenArgs(cmd);
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process proc;
        try {
            proc = pb.start();
        } catch (IOException e) {
            throw new AppiumLifecycleException(AppiumErrorCode.APPIUM_UNAVAILABLE,
                    "appium failed to start: " + e.getMessage());
        }
        long pid = proc.pid();
        String ref = "appium_" + shortId();
        String baseUrl = request.getPorts().server().baseUrl();
        OwnedAppiumProcess handle = new OwnedAppiumProcess(
                ref,
                pid,
                request.getOwnerKey(),
                request.getPorts().server().getPort(),
                request.getPorts().system().getPort(),
                request.getPorts().chromedriver().getPort(),
                baseUrl);
        live.put(ref, proc);
        owned.put(ref, handle);

        Duration wait = Duration.ofSeconds(Math.max(1, props.getAppiumStartTimeoutSeconds()));
        long deadlineNs = System.nanoTime() + wait.toNanos();
        long pollMs = 100L;
        try {
            while (System.nanoTime() < deadlineNs) {
                if (request.isCancelled()) {
                    stopInternal(handle, proc);
                    throw new AppiumLifecycleException(AppiumErrorCode.CANCELLED_WHILE_STARTING,
                            "cancel while starting appium pid=" + pid);
                }
                if (!proc.isAlive()) {
                    live.remove(ref);
                    owned.remove(ref);
                    throw new AppiumLifecycleException(AppiumErrorCode.APPIUM_UNAVAILABLE,
                            "appium exited during start pid=" + pid);
                }
                // AE-L2: wait until owned loopback /status reports ready (not sleep-only).
                if (readyChecker.test(baseUrl)) {
                    return handle;
                }
                Thread.sleep(pollMs);
            }
            stopInternal(handle, proc);
            throw new AppiumLifecycleException(AppiumErrorCode.SESSION_START_TIMEOUT,
                    "appium start timeout waiting for /status pid=" + pid);
        } catch (AppiumLifecycleException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            stopInternal(handle, proc);
            throw new AppiumLifecycleException(AppiumErrorCode.CANCELLED_WHILE_STARTING, "interrupted while starting");
        }
    }

    /**
     * GET {@code baseUrl/status} on owned loopback only. Appium 2 {@code value.ready=true}
     * or Appium 1 {@code status=0} counts as ready. Connection errors → not ready yet.
     */
    static boolean httpStatusReady(String baseUrl) {
        if (!isOwnedLoopbackBaseUrl(baseUrl)) {
            return false;
        }
        HttpURLConnection conn = null;
        try {
            URI uri = URI.create(trimSlash(baseUrl) + "/status");
            conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setConnectTimeout(500);
            conn.setReadTimeout(500);
            conn.setRequestMethod("GET");
            conn.setInstanceFollowRedirects(false);
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                return false;
            }
            String body;
            try (InputStream in = conn.getInputStream()) {
                body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            return statusBodyReady(body);
        } catch (Exception e) {
            return false;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /** Visible for tests: parse Appium /status JSON without network. */
    static boolean statusBodyReady(String body) {
        if (body == null || body.isBlank()) {
            return false;
        }
        String b = body.toLowerCase(Locale.ROOT);
        // Appium 2: {"value":{"ready":true,...}}
        if (b.contains("\"ready\"") && b.contains("true")) {
            // Prefer structured ready=true (avoid matching ready:false)
            if (b.contains("\"ready\":true") || b.contains("\"ready\": true")) {
                return true;
            }
        }
        // Appium 1: {"status":0,"value":{...}}
        if (b.contains("\"status\":0") || b.contains("\"status\": 0")) {
            return true;
        }
        return false;
    }

    static boolean isOwnedLoopbackBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return false;
        }
        String u = baseUrl.trim().toLowerCase(Locale.ROOT);
        return u.startsWith("http://127.0.0.1:") || u.startsWith("http://localhost:");
    }

    private static String trimSlash(String baseUrl) {
        String u = baseUrl.trim();
        while (u.endsWith("/")) {
            u = u.substring(0, u.length() - 1);
        }
        return u;
    }

    /** Visible for tests: allowlisted CLI args; never includes kill-server. */
    public List<String> buildCommand(AppiumStartRequest request) {
        List<String> cmd = new ArrayList<>();
        cmd.add(props.getAppiumPath().trim());
        cmd.add("--address");
        cmd.add(OwnedPort.LOOPBACK);
        cmd.add("--port");
        cmd.add(Integer.toString(request.getPorts().server().getPort()));
        cmd.add("--session-override");
        cmd.add("--log-no-colors");
        AppiumProcessKillPolicy.rejectForbiddenArgs(cmd);
        return cmd;
    }

    @Override
    public void stop(OwnedAppiumProcess process) throws AppiumLifecycleException {
        if (process == null) {
            return;
        }
        AppiumProcessKillPolicy.assertMayKill(process, owned.values());
        Process proc = live.get(process.getProcessRef());
        stopInternal(process, proc);
    }

    private void stopInternal(OwnedAppiumProcess process, Process proc) {
        live.remove(process.getProcessRef());
        owned.remove(process.getProcessRef());
        if (proc == null) {
            return;
        }
        // Only the Process we started — never a PID scan / unrelated kill.
        try {
            ProcessHandle handle = proc.toHandle();
            handle.descendants().forEach(ProcessHandle::destroyForcibly);
            handle.destroyForcibly();
        } catch (Exception e) {
            proc.destroyForcibly();
        }
        try {
            proc.waitFor(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public boolean isAlive(OwnedAppiumProcess process) {
        if (process == null) {
            return false;
        }
        Process proc = live.get(process.getProcessRef());
        return proc != null && proc.isAlive();
    }

    public Map<String, OwnedAppiumProcess> ownedSnapshot() {
        return Map.copyOf(owned);
    }

    private static String shortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
