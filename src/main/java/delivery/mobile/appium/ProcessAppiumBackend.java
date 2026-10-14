package delivery.mobile.appium;

import delivery.mobile.MobileProperties;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

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

    public ProcessAppiumBackend(MobileProperties props) {
        this.props = props == null ? new MobileProperties() : props;
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
        OwnedAppiumProcess handle = new OwnedAppiumProcess(
                ref,
                pid,
                request.getOwnerKey(),
                request.getPorts().server().getPort(),
                request.getPorts().system().getPort(),
                request.getPorts().chromedriver().getPort(),
                request.getPorts().server().baseUrl());
        live.put(ref, proc);
        owned.put(ref, handle);

        Duration wait = Duration.ofSeconds(Math.max(1, props.getAppiumStartTimeoutSeconds()));
        long deadlineNs = System.nanoTime() + wait.toNanos();
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
                // Portal readiness stub: alive after brief settle. Real /status wait is AE/M2.
                Thread.sleep(50);
                if (proc.isAlive() && !request.isCancelled()) {
                    return handle;
                }
            }
            stopInternal(handle, proc);
            throw new AppiumLifecycleException(AppiumErrorCode.SESSION_START_TIMEOUT,
                    "appium start timeout pid=" + pid);
        } catch (AppiumLifecycleException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            stopInternal(handle, proc);
            throw new AppiumLifecycleException(AppiumErrorCode.CANCELLED_WHILE_STARTING, "interrupted while starting");
        }
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
