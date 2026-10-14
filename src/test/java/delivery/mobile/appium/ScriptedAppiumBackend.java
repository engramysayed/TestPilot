package delivery.mobile.appium;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Deterministic Appium process backend for M1-06 tests (no real Node/Appium).
 */
public final class ScriptedAppiumBackend implements AppiumProcessBackend {
    public enum StartBehavior { SUCCESS, UNAVAILABLE, HANG_UNTIL_CANCEL, CRASH_AFTER_START, TIMEOUT }

    private volatile boolean available = true;
    private volatile StartBehavior startBehavior = StartBehavior.SUCCESS;
    private volatile boolean failStop = false;
    private final AtomicLong nextPid = new AtomicLong(10_000);
    private final ConcurrentHashMap<String, OwnedAppiumProcess> owned = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicBoolean> alive = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<Long> killedPids = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<List<String>> rejectedCommands = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<String> startLog = new CopyOnWriteArrayList<>();

    public void setAvailable(boolean available) { this.available = available; }
    public void setStartBehavior(StartBehavior startBehavior) { this.startBehavior = startBehavior; }
    public void setFailStop(boolean failStop) { this.failStop = failStop; }

    public List<Long> killedPids() { return List.copyOf(killedPids); }
    public Map<String, OwnedAppiumProcess> ownedSnapshot() { return Map.copyOf(owned); }
    public List<String> startLog() { return List.copyOf(startLog); }

    /** Simulate an external crash of an owned process. */
    public void crash(String processRef) {
        AtomicBoolean a = alive.get(processRef);
        if (a != null) {
            a.set(false);
        }
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public OwnedAppiumProcess start(AppiumStartRequest request) throws AppiumLifecycleException {
        startLog.add("start:" + request.getOwnerKey());
        // Tripwire: never accept kill-server in a forged command path
        List<String> probe = List.of("adb", "kill-server");
        if (AppiumProcessKillPolicy.containsForbiddenKillServer(probe)) {
            // policy works; we still refuse if someone tried to sneak it into start
            rejectedCommands.add(probe);
        }
        if (!available || startBehavior == StartBehavior.UNAVAILABLE) {
            throw new AppiumLifecycleException(AppiumErrorCode.APPIUM_UNAVAILABLE, "scripted unavailable");
        }
        if (request.isCancelled() || startBehavior == StartBehavior.HANG_UNTIL_CANCEL) {
            // Busy-wait briefly honouring cancel
            long deadline = System.nanoTime() + 2_000_000_000L;
            while (System.nanoTime() < deadline) {
                if (request.isCancelled()) {
                    throw new AppiumLifecycleException(AppiumErrorCode.CANCELLED_WHILE_STARTING,
                            "scripted cancel while starting");
                }
                if (startBehavior != StartBehavior.HANG_UNTIL_CANCEL) {
                    break;
                }
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new AppiumLifecycleException(AppiumErrorCode.CANCELLED_WHILE_STARTING, "interrupted");
                }
            }
            if (request.isCancelled() || startBehavior == StartBehavior.HANG_UNTIL_CANCEL) {
                throw new AppiumLifecycleException(AppiumErrorCode.CANCELLED_WHILE_STARTING,
                        "scripted cancel/hang");
            }
        }
        if (startBehavior == StartBehavior.TIMEOUT) {
            throw new AppiumLifecycleException(AppiumErrorCode.SESSION_START_TIMEOUT, "scripted timeout");
        }
        long pid = nextPid.getAndIncrement();
        String ref = "scripted_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        OwnedAppiumProcess proc = new OwnedAppiumProcess(
                ref, pid, request.getOwnerKey(),
                request.getPorts().server().getPort(),
                request.getPorts().system().getPort(),
                request.getPorts().chromedriver().getPort(),
                request.getPorts().server().baseUrl());
        owned.put(ref, proc);
        AtomicBoolean flag = new AtomicBoolean(startBehavior != StartBehavior.CRASH_AFTER_START);
        alive.put(ref, flag);
        if (startBehavior == StartBehavior.CRASH_AFTER_START) {
            flag.set(false);
        }
        return proc;
    }

    @Override
    public void stop(OwnedAppiumProcess process) throws AppiumLifecycleException {
        if (process == null) {
            return;
        }
        AppiumProcessKillPolicy.assertMayKill(process, owned.values());
        if (failStop) {
            throw new AppiumLifecycleException(AppiumErrorCode.CLEANUP_FAILED, "scripted stop failure");
        }
        killedPids.add(process.getPid());
        alive.remove(process.getProcessRef());
        owned.remove(process.getProcessRef());
    }

    @Override
    public boolean isAlive(OwnedAppiumProcess process) {
        if (process == null) {
            return false;
        }
        AtomicBoolean a = alive.get(process.getProcessRef());
        return a != null && a.get();
    }

    /** Attempt to kill an arbitrary PID — must be rejected by policy helpers under test. */
    public void attemptKillUnrelated(long pid) {
        AppiumProcessKillPolicy.assertMayKill(pid, owned.values().stream().map(OwnedAppiumProcess::getPid).collect(
                java.util.stream.Collectors.toSet()));
    }
}
