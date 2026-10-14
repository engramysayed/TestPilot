package delivery.mobile.appium;

import delivery.mobile.MobileProperties;
import delivery.mobile.lease.MobileLeaseRecord;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/**
 * M1-06 supervised mobile worker: per-job Appium on owned loopback ports; start/stop
 * tied to lease ACTIVE→RELEASING; session create/quit bookkeeping; crash / cancel-while-starting /
 * cleanup failure → quarantine via {@link LeaseLifecycleGate}.
 * <p>
 * No Appium jars on portal — process supervisor + port ownership + raw-HTTP NewSession (AE-L6) only.
 * Never kills unrelated PIDs; never runs {@code adb kill-server}.
 */
@Component
public class SupervisedAppiumLifecycle {
    private final MobileProperties props;
    private final MobilePreflight preflight;
    private final OwnedLoopbackPortAllocator ports;
    private final AppiumProcessBackend backend;
    private final AppiumSessionFactory sessions;
    private final LeaseLifecycleGate leases;

    /** leaseId → active binding */
    private final ConcurrentHashMap<String, Binding> bindings = new ConcurrentHashMap<>();
    /** processRef → leaseId for crash routing */
    private final ConcurrentHashMap<String, String> processToLease = new ConcurrentHashMap<>();

    public SupervisedAppiumLifecycle(MobileProperties props,
                                     MobilePreflight preflight,
                                     OwnedLoopbackPortAllocator ports,
                                     AppiumProcessBackend backend,
                                     AppiumSessionFactory sessions,
                                     LeaseLifecycleGate leases) {
        this.props = props == null ? new MobileProperties() : props;
        this.preflight = Objects.requireNonNull(preflight, "preflight");
        this.ports = Objects.requireNonNull(ports, "ports");
        this.backend = Objects.requireNonNull(backend, "backend");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.leases = Objects.requireNonNull(leases, "leases");
    }

    /**
     * Preflight → allocate owned ports → start Appium → session bookkeeping → lease ACTIVE.
     * Cancel-while-starting aborts, releases ports, and does not activate.
     */
    public Binding start(String leaseId,
                         long fenceToken,
                         String deviceSerial,
                         BooleanSupplier cancel) {
        AtomicBoolean cancelFlag = new AtomicBoolean(false);
        BooleanSupplier combined = () -> cancelFlag.get()
                || (cancel != null && cancel.getAsBoolean());

        preflight.requireOk(deviceSerial);
        if (combined.getAsBoolean()) {
            throw new AppiumLifecycleException(AppiumErrorCode.CANCELLED_WHILE_STARTING,
                    "cancel before port allocation");
        }

        String ownerKey = ownerKey(leaseId, fenceToken);
        OwnedLoopbackPortAllocator.PortBundle bundle = ports.allocateBundle(ownerKey);
        OwnedAppiumProcess process = null;
        AppiumSessionRecord session = null;
        try {
            if (combined.getAsBoolean()) {
                throw new AppiumLifecycleException(AppiumErrorCode.CANCELLED_WHILE_STARTING,
                        "cancel after port allocation");
            }
            AppiumStartRequest req = new AppiumStartRequest(
                    ownerKey, leaseId, fenceToken, deviceSerial, bundle, combined);
            process = backend.start(req);
            if (combined.getAsBoolean()) {
                throw new AppiumLifecycleException(AppiumErrorCode.CANCELLED_WHILE_STARTING,
                        "cancel after process start");
            }
            session = sessions.createSession(leaseId, fenceToken, process, deviceSerial);
            MobileLeaseRecord activated = leases.activate(
                    leaseId, fenceToken, session.getSessionId(), process.getProcessRef());
            Binding binding = new Binding(leaseId, fenceToken, ownerKey, bundle, process, session, activated);
            bindings.put(leaseId, binding);
            processToLease.put(process.getProcessRef(), leaseId);
            return binding;
        } catch (AppiumLifecycleException e) {
            safeAbortStart(ownerKey, process, session);
            if (e.getCode() == AppiumErrorCode.CANCELLED_WHILE_STARTING) {
                throw e;
            }
            if (e.getCode() == AppiumErrorCode.APPIUM_UNAVAILABLE
                    || e.getCode() == AppiumErrorCode.SESSION_START_TIMEOUT) {
                throw e;
            }
            throw e;
        } catch (RuntimeException e) {
            safeAbortStart(ownerKey, process, session);
            throw new AppiumLifecycleException(AppiumErrorCode.APPIUM_UNAVAILABLE,
                    "appium start failed: " + e.getMessage());
        }
    }

    /**
     * Lease ACTIVE→RELEASING: quit session → stop owned process only → release ports → cleanup ACK.
     * Any cleanup failure quarantines the device.
     */
    public void teardown(String leaseId, long fenceToken) {
        Binding binding = bindings.get(leaseId);
        try {
            leases.beginRelease(leaseId, fenceToken);
        } catch (RuntimeException e) {
            quarantine(leaseId, fenceToken, "beginRelease failed: " + e.getMessage());
            throw new AppiumLifecycleException(AppiumErrorCode.CLEANUP_FAILED,
                    "beginRelease failed: " + e.getMessage());
        }

        try {
            if (binding != null && binding.session() != null) {
                sessions.quitSession(binding.session().getSessionId());
            }
            if (binding != null && binding.process() != null) {
                backend.stop(binding.process());
                processToLease.remove(binding.process().getProcessRef());
            }
            if (binding != null) {
                ports.releaseAll(binding.ownerKey());
            } else {
                ports.releaseAll(ownerKey(leaseId, fenceToken));
            }
            leases.acknowledgeCleanup(leaseId, fenceToken);
            bindings.remove(leaseId);
        } catch (RuntimeException e) {
            quarantine(leaseId, fenceToken, "cleanup failure: " + e.getMessage());
            throw new AppiumLifecycleException(AppiumErrorCode.CLEANUP_FAILED,
                    "cleanup failure → quarantine: " + e.getMessage());
        }
    }

    /**
     * Process crash / unexpected exit while ACTIVE → quarantine (DeviceLeaseGuard patterns).
     */
    public MobileLeaseRecord onProcessCrash(String processRef) {
        String leaseId = processToLease.get(processRef);
        if (leaseId == null) {
            Binding found = null;
            for (Binding b : bindings.values()) {
                if (b.process() != null && processRef.equals(b.process().getProcessRef())) {
                    found = b;
                    break;
                }
            }
            if (found == null) {
                throw new AppiumLifecycleException(AppiumErrorCode.NOT_OWNED,
                        "unknown processRef for crash: " + processRef);
            }
            leaseId = found.leaseId();
        }
        Binding binding = bindings.get(leaseId);
        long fence = binding == null ? -1L : binding.fenceToken();
        if (binding == null) {
            MobileLeaseRecord lease = leases.getLease(leaseId);
            fence = lease.getFenceToken();
        }
        return quarantine(leaseId, fence, "owned Appium process crashed: " + processRef);
    }

    /** Poll owned process liveness; crash → quarantine. */
    public void reconcileAlive(String leaseId) {
        Binding binding = bindings.get(leaseId);
        if (binding == null || binding.process() == null) {
            return;
        }
        if (!backend.isAlive(binding.process())) {
            onProcessCrash(binding.process().getProcessRef());
        }
    }

    public MobileLeaseRecord quarantine(String leaseId, long fenceToken, String reason) {
        Binding binding = bindings.get(leaseId);
        try {
            if (binding != null && binding.process() != null) {
                try {
                    backend.stop(binding.process());
                } catch (RuntimeException ignored) {
                    // best-effort stop of owned process only
                }
                processToLease.remove(binding.process().getProcessRef());
                ports.releaseAll(binding.ownerKey());
            }
        } finally {
            bindings.remove(leaseId);
        }
        return leases.quarantine(leaseId, fenceToken, reason == null ? "quarantine" : reason);
    }

    public Binding getBinding(String leaseId) {
        return bindings.get(leaseId);
    }

    public Map<String, Binding> bindingsSnapshot() {
        return Map.copyOf(bindings);
    }

    public int teardownDeadlineSeconds() {
        return props.getTeardownDeadlineSeconds();
    }

    private void safeAbortStart(String ownerKey, OwnedAppiumProcess process, AppiumSessionRecord session) {
        if (session != null) {
            try {
                sessions.quitSession(session.getSessionId());
            } catch (RuntimeException ignored) {
                // abort path
            }
        }
        if (process != null) {
            try {
                backend.stop(process);
            } catch (RuntimeException ignored) {
                // abort path
            }
            processToLease.remove(process.getProcessRef());
        }
        ports.releaseAll(ownerKey);
    }

    public static String ownerKey(String leaseId, long fenceToken) {
        return leaseId + "@" + fenceToken;
    }

    /**
     * Active supervised binding for one lease/job.
     */
    public record Binding(String leaseId,
                          long fenceToken,
                          String ownerKey,
                          OwnedLoopbackPortAllocator.PortBundle ports,
                          OwnedAppiumProcess process,
                          AppiumSessionRecord session,
                          MobileLeaseRecord lease) {
    }
}
