package delivery.mobile.run;

import delivery.excel.ManualTestCase;
import delivery.mobile.MobilePortalStore;
import delivery.mobile.MobileProperties;
import delivery.mobile.appium.SupervisedAppiumLifecycle;
import delivery.mobile.lease.DeviceLeaseGuard;
import delivery.mobile.lease.LeaseHandle;
import delivery.mobile.lease.MobileLeaseRecord;
import delivery.mobile.persistence.MobileDeviceEntity;
import delivery.mobile.persistence.MobileDeviceRepository;
import delivery.portal.DeliveryPortalProperties;
import delivery.portal.service.GeneratedWorkbookService;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Mobile Execute end to end (portal side):
 * <ol>
 *   <li>{@link #start}: validate project/device/cases, create the run.</li>
 *   <li>Reserve the device lease, resolve the ADB serial, start the owned Appium process + session
 *       ({@link SupervisedAppiumLifecycle#start}) → lease ACTIVE. The lease is heartbeated from the moment it is
 *       reserved (Appium start can exceed the 30 s lease expiry).</li>
 *   <li>A mobile runner ({@code delivery.mobile.runner.agent.MobileRunnerAgent}) claims the run over
 *       {@code /api/v1/runners/mobile/**}, drives the session, heartbeats, and posts results.</li>
 *   <li>{@link #finish}: teardown (quit session → stop process → release ports → cleanup ACK → device AVAILABLE).
 *       Any cleanup failure quarantines the device; {@link MobileLeaseMaintenance} reconciles it later.</li>
 * </ol>
 * The lease is renewed only while the run is starting or the runner keeps heartbeating, so a dead runner lets
 * the lease lapse and the device is recovered instead of being held forever.
 */
@Service
public class MobileRunOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(MobileRunOrchestrator.class);
    static final Duration CLAIM_TIMEOUT = Duration.ofMinutes(5);
    static final Duration RUNNER_SILENCE = Duration.ofSeconds(45);

    private final MobileProperties props;
    private final DeviceLeaseGuard leases;
    private final SupervisedAppiumLifecycle appium;
    private final MobileDeviceRepository devices;
    private final DeviceSerialResolver serials;
    private final GeneratedWorkbookService workbooks;
    private final MobilePortalStore mobileStore;
    private final Path runsRoot;

    private final Map<String, MobileRun> runs = new ConcurrentHashMap<>();
    private final ExecutorService starter = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "mobile-run-starter");
        t.setDaemon(true);
        return t;
    });
    private final ScheduledExecutorService ticker = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "mobile-run-ticker");
        t.setDaemon(true);
        return t;
    });

    public MobileRunOrchestrator(MobileProperties props,
                                 DeviceLeaseGuard leases,
                                 SupervisedAppiumLifecycle appium,
                                 MobileDeviceRepository devices,
                                 DeviceSerialResolver serials,
                                 GeneratedWorkbookService workbooks,
                                 MobilePortalStore mobileStore,
                                 DeliveryPortalProperties portalProps) {
        this.props = props;
        this.leases = leases;
        this.appium = appium;
        this.devices = devices;
        this.serials = serials;
        this.workbooks = workbooks;
        this.mobileStore = mobileStore;
        this.runsRoot = Path.of(portalProps.getStoreRoot()).resolve("mobile-runs");
        long every = Math.max(1, props.getLeaseHeartbeatSeconds());
        ticker.scheduleAtFixedRate(this::tick, every, every, TimeUnit.SECONDS);
    }

    @PreDestroy
    void shutdown() {
        ticker.shutdownNow();
        starter.shutdownNow();
    }

    // ------------------------------------------------------------------ user API

    public MobileRun start(String tenantId, String projectId, Long ownerUserId, String deviceId, List<String> tcIds)
            throws Exception {
        if (!props.isEnabled()) {
            throw new IllegalStateException("Mobile is disabled (delivery.mobile.enabled=false)");
        }
        MobileDeviceEntity device = devices.findByDeviceId(deviceId)
                .orElseThrow(() -> new IllegalArgumentException("device not found"));
        if (!tenantId.equals(device.getTenantId())) {
            throw new IllegalArgumentException("device not found");
        }
        List<ManualTestCase> all = workbooks.readCases(projectId);
        Set<String> wanted = tcIds == null ? Set.of() : Set.copyOf(tcIds);
        List<MobileRun.CaseInput> cases = new ArrayList<>();
        for (ManualTestCase tc : all) {
            if (!wanted.isEmpty() && !wanted.contains(tc.tcId())) {
                continue;
            }
            if ("MANUAL".equalsIgnoreCase(tc.keelPath())) {
                continue;
            }
            cases.add(new MobileRun.CaseInput(tc.tcId(), nz(tc.title()),
                    lines(tc.steps()), lines(tc.expectedResult()), lines(tc.testData())));
        }
        if (cases.isEmpty()) {
            throw new IllegalArgumentException("No runnable test cases selected");
        }
        var rev = mobileStore.findActiveAppRevision(tenantId, projectId);
        String pkg = rev.map(r -> nz(r.getPackageName())).orElse("");
        String act = rev.map(r -> nz(r.getLaunchActivity())).orElse("");
        MobileRun run = new MobileRun("mrun_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12),
                tenantId, projectId, ownerUserId, deviceId, cases, pkg, act);
        runs.put(run.runId, run);
        starter.submit(() -> prepare(run));
        return run;
    }

    public Optional<MobileRun> get(String runId) {
        MobileRun live = runs.get(runId);
        return Optional.ofNullable(live);
    }

    public Optional<JSONObject> view(String runId) {
        MobileRun live = runs.get(runId);
        if (live != null) {
            return Optional.of(live.view());
        }
        Path f = runsRoot.resolve(runId + ".json");
        try {
            if (Files.isRegularFile(f)) {
                return Optional.of(new JSONObject(Files.readString(f, StandardCharsets.UTF_8)));
            }
        } catch (Exception ignored) {
        }
        return Optional.empty();
    }

    public void cancel(String runId) {
        MobileRun run = runs.get(runId);
        if (run == null || run.status.terminal()) {
            return;
        }
        run.cancelRequested = true;
        if (run.status == MobileRun.Status.QUEUED || run.status == MobileRun.Status.WAITING_FOR_RUNNER) {
            finish(run, MobileRun.Status.CANCELLED, "Cancelled by user", "");
        }
        // STARTING: the start path sees cancelRequested; RUNNING: the runner sees it on heartbeat.
    }

    // ------------------------------------------------------------------ runner API

    /** First run waiting for a runner in this tenant (oldest first). */
    public synchronized Optional<JSONObject> claim(String tenantId, String runnerId) {
        return runs.values().stream()
                .filter(r -> r.status == MobileRun.Status.WAITING_FOR_RUNNER)
                .filter(r -> tenantId == null || tenantId.equals(r.tenantId))
                .min((a, b) -> a.createdAt.compareTo(b.createdAt))
                .map(r -> {
                    r.status = MobileRun.Status.RUNNING;
                    r.runnerId = runnerId == null ? "" : runnerId;
                    r.claimedAt = Instant.now();
                    r.lastRunnerHeartbeat = r.claimedAt;
                    r.message = "Runner " + r.runnerId + " is running the cases";
                    return r.claimPayload();
                });
    }

    /** @return {@code cancel=true} when the runner must stop. */
    public JSONObject runnerHeartbeat(String runId, String tenantId, String progress) {
        MobileRun run = requireRunnerRun(runId, tenantId);
        run.lastRunnerHeartbeat = Instant.now();
        if (progress != null && !progress.isBlank()) {
            run.message = progress.length() > 200 ? progress.substring(0, 200) : progress;
        }
        return new JSONObject().put("cancel", run.cancelRequested || run.status.terminal());
    }

    public void runnerResult(String runId, String tenantId, JSONArray results, String error) {
        MobileRun run = requireRunnerRun(runId, tenantId);
        synchronized (run.results) {
            run.results.clear();
            if (results != null) {
                for (int i = 0; i < results.length(); i++) {
                    JSONObject r = results.optJSONObject(i);
                    if (r != null) {
                        run.results.add(r);
                    }
                }
            }
        }
        boolean failed = error != null && !error.isBlank();
        finish(run, failed ? MobileRun.Status.FAILED : MobileRun.Status.COMPLETED,
                failed ? "Runner error" : "Run finished", failed ? error : "");
    }

    private MobileRun requireRunnerRun(String runId, String tenantId) {
        MobileRun run = runs.get(runId);
        if (run == null || (tenantId != null && !tenantId.equals(run.tenantId))) {
            throw new IllegalArgumentException("run not found");
        }
        return run;
    }

    // ------------------------------------------------------------------ lifecycle

    void prepare(MobileRun run) {
        try {
            if (run.cancelRequested) {
                finish(run, MobileRun.Status.CANCELLED, "Cancelled before start", "");
                return;
            }
            MobileDeviceEntity device = devices.findByDeviceId(run.deviceId).orElseThrow();
            String serial = serials.resolve(device.getSerialFingerprint())
                    .orElseThrow(() -> new IllegalStateException(
                            "Device is not connected to this Keel host (adb does not list it). Plug it in or start the emulator."));
            // STARTING before leaseId is visible: tick() skips blank lease ids and would otherwise see a
            // QUEUED run with a lease (not alive) and fail it in the middle of the start.
            run.status = MobileRun.Status.STARTING;
            LeaseHandle handle = leases.reserve(run.tenantId, run.deviceId, run.runId, "att_1", null);
            MobileLeaseRecord lease = handle.record();
            run.fenceToken = lease.getFenceToken();
            run.leaseId = lease.getLeaseId();
            run.message = "Starting Appium on the device";
            SupervisedAppiumLifecycle.Binding binding = appium.start(
                    run.leaseId, run.fenceToken, serial, () -> run.cancelRequested);
            run.appiumUrl = binding.process().getBaseUrl();
            run.sessionId = binding.session().getSessionId();
            run.status = MobileRun.Status.WAITING_FOR_RUNNER;
            run.message = "Device ready — waiting for a mobile runner";
            run.lastRunnerHeartbeat = Instant.now();
        } catch (Exception e) {
            log.warn("Mobile run {} failed to start: {}", run.runId, e.getMessage());
            finish(run, MobileRun.Status.FAILED, "Could not start the device session", e.getMessage());
        }
    }

    /** Heartbeat leases of live runs; fail runs whose runner went silent or never claimed. */
    void tick() {
        Instant now = Instant.now();
        for (MobileRun run : runs.values()) {
            try {
                if (run.status.terminal() || run.leaseId.isBlank()) {
                    continue;
                }
                boolean alive = switch (run.status) {
                    case STARTING -> true;
                    case WAITING_FOR_RUNNER -> run.lastRunnerHeartbeat == null
                            || now.isBefore(run.lastRunnerHeartbeat.plus(CLAIM_TIMEOUT));
                    case RUNNING -> run.lastRunnerHeartbeat != null
                            && now.isBefore(run.lastRunnerHeartbeat.plus(RUNNER_SILENCE));
                    default -> false;
                };
                if (!alive) {
                    String why = run.status == MobileRun.Status.WAITING_FOR_RUNNER
                            ? "No mobile runner claimed the run within " + CLAIM_TIMEOUT.toMinutes() + " minutes"
                            : "Mobile runner stopped responding";
                    finish(run, MobileRun.Status.FAILED, why, run.status == MobileRun.Status.RUNNING
                            ? "INTERRUPTED_UNCERTAIN" : "RUNNER_NOT_AVAILABLE");
                    continue;
                }
                leases.heartbeat(run.leaseId, run.fenceToken);
                appium.reconcileAlive(run.leaseId); // Appium process crash → quarantine
            } catch (RuntimeException e) {
                log.warn("Mobile run {} lease heartbeat failed: {}", run.runId, e.getMessage());
                finish(run, MobileRun.Status.FAILED, "Device lease lost", e.getMessage());
            }
        }
        // keep finished runs in memory for 30 minutes, then rely on the JSON file
        runs.values().removeIf(r -> r.status.terminal() && r.finishedAt != null
                && r.finishedAt.isBefore(now.minus(Duration.ofMinutes(30))));
    }

    synchronized void finish(MobileRun run, MobileRun.Status status, String message, String error) {
        if (run.status.terminal()) {
            return;
        }
        run.status = status;
        run.message = message;
        run.error = error == null ? "" : error;
        run.finishedAt = Instant.now();
        if (!run.leaseId.isBlank()) {
            try {
                appium.teardown(run.leaseId, run.fenceToken);
            } catch (RuntimeException e) {
                // teardown quarantines on failure; MobileLeaseMaintenance reconciles quarantined leases
                log.warn("Mobile run {} teardown: {}", run.runId, e.getMessage());
                try {
                    leases.reconcile(run.leaseId, null); // RESERVED without session → frees immediately
                } catch (RuntimeException ignored) {
                }
            }
        }
        persist(run);
    }

    private void persist(MobileRun run) {
        try {
            Files.createDirectories(runsRoot);
            Files.writeString(runsRoot.resolve(run.runId + ".json"), run.view().toString(2), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("Could not persist mobile run {}: {}", run.runId, e.getMessage());
        }
    }

    /** Leases currently owned by a live run (maintenance must not touch them). */
    public Set<String> liveLeaseIds() {
        Set<String> out = new java.util.HashSet<>();
        for (MobileRun r : runs.values()) {
            if (!r.status.terminal() && !r.leaseId.isBlank()) {
                out.add(r.leaseId);
            }
        }
        return out;
    }

    static List<String> lines(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split("\\r?\\n"))
                .map(s -> s.replaceFirst("^\\s*\\d+[.)]\\s*", "").trim())
                .toList();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
