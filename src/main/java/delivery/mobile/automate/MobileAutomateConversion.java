package delivery.mobile.automate;

import delivery.excel.ExcelTcReader;
import delivery.excel.KeelPathCaseFilter;
import delivery.excel.ManualTestCase;
import delivery.job.ConversionJobRequest;
import delivery.job.ConversionJobResult;
import delivery.job.JobCancelledException;
import delivery.job.JobWorkLayout;
import delivery.job.SuiteCaseFilter;
import delivery.mobile.MobilePortalStore;
import delivery.mobile.MobileProperties;
import delivery.mobile.codegen.MobileAutomateIr;
import delivery.mobile.codegen.MobileAutomatePack;
import delivery.mobile.ir.MobileCaseIr;
import delivery.mobile.model.MobileAppRevisionRecord;
import delivery.mobile.persistence.MobileDeviceEntity;
import delivery.mobile.persistence.MobileDeviceRepository;
import delivery.mobile.run.DeviceSerialResolver;
import delivery.mobile.run.MobileRun;
import delivery.mobile.run.MobileRunOrchestrator;
import delivery.agent.validation.contract.MobileFrameworkContractValidator;
import delivery.mobile.run.MobileCursorPass;
import delivery.mobile.run.MobileRunnerSupervisor;
import delivery.portal.DeliveryPortalProperties;
import delivery.portal.model.JobRecord;
import delivery.portal.service.PortalStore;
import delivery.store.ProjectStore;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

/**
 * Android Automate: open the selected phone, follow the workbook, write the locators it bound.
 * The job must name a connected phone. Chrome stays closed.
 */
@Service
public class MobileAutomateConversion {

    private static final Duration RUNNER_CLAIM_WAIT = Duration.ofSeconds(45);
    private static final Duration WALK_WAIT = Duration.ofMinutes(3);

    private final PortalStore portalStore;
    private final DeliveryPortalProperties portalProps;
    private final MobileProperties mobileProps;
    private final MobilePortalStore mobileStore;
    private final MobileDeviceRepository devices;
    private final DeviceSerialResolver serials;
    private final MobileRunOrchestrator orchestrator;
    private final MobileRunnerSupervisor runners;
    private final MobileCursorPass cursorPass;
    private final ConcurrentHashMap<String, String> deviceByJob = new ConcurrentHashMap<>();
    private static final java.util.Set<String> BLOCKING_RULES = java.util.Set.of(
            "FW_RULE_M_APPIUM_ONLY", "FW_RULE_M_NO_CSS", "FW_RULE_M_NO_SLEEP",
            "FW_RULE_M_SETTLE_DELEGATE", "FW_RULE_M_TEST_BASE");

    public MobileAutomateConversion(PortalStore portalStore,
                                    DeliveryPortalProperties portalProps,
                                    MobileProperties mobileProps,
                                    MobilePortalStore mobileStore,
                                    MobileDeviceRepository devices,
                                    DeviceSerialResolver serials,
                                    MobileRunOrchestrator orchestrator,
                                    MobileRunnerSupervisor runners,
                                    MobileCursorPass cursorPass) {
        this.portalStore = portalStore;
        this.portalProps = portalProps;
        this.mobileProps = mobileProps;
        this.mobileStore = mobileStore;
        this.devices = devices;
        this.serials = serials;
        this.orchestrator = orchestrator;
        this.runners = runners;
        this.cursorPass = cursorPass;
    }

    /** Remember the phone chosen on the Automate page. Blank means the user chose none. */
    public void rememberDevice(String jobId, String deviceId) {
        if (jobId == null || jobId.isBlank()) {
            return;
        }
        deviceByJob.put(jobId, deviceId == null ? "" : deviceId.trim());
    }

    public Map<String, Object> plan(String tenantId, String projectId) {
        return plan(tenantId, projectId, null);
    }

    /** Enroll and start the local runner before the page asks whether it is up. */
    private void ensureRunner(String tenantId, String projectId) {
        try {
            portalStore.getProject(projectId).ifPresent(project -> {
                Long owner = project.getOwnerUserId();
                if (owner != null && owner > 0) {
                    runners.ensureForProject(projectId, owner, tenantId == null ? "" : tenantId);
                }
            });
        } catch (RuntimeException ignored) {
            // The status line reports a missing runner. The page stays usable.
        }
    }

    /**
     * @param preferred the phone chosen for this run. Blank or null means none was chosen.
     */
    public Map<String, Object> plan(String tenantId, String projectId, String preferred) {
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        Optional<MobileAppRevisionRecord> rev = mobileStore.findActiveAppRevision(tenantId, projectId);
        String pkg = rev.map(r -> r.getPackageName() == null ? "" : r.getPackageName()).orElse("");
        String version = rev.map(r -> r.getVersionName() == null ? "" : r.getVersionName()).orElse("");
        String deviceId = preferred == null ? "" : preferred.trim();
        out.put("deviceId", deviceId);
        out.put("appPackage", pkg);
        out.put("appVersion", version);
        out.put("appRevisionId", rev.map(MobileAppRevisionRecord::getAppRevisionId).orElse(""));
        String appLabel = pkg.isBlank() ? "No app saved" : (pkg + (version.isBlank() ? "" : " " + version));
        out.put("appLabel", appLabel);
        ensureRunner(tenantId, projectId);
        runners.ensureStarted();
        out.put("runnerOnline", orchestrator.runnerOnline());
        out.put("runnerMessage", runners.line());
        if (!mobileProps.isEnabled()) {
            out.put("phoneName", "");
            out.put("phoneState", "NONE");
            out.put("blocked", false);
            out.put("message", "Mobile is turned off on this server. No phone will open.");
            return out;
        }
        if (deviceId.isBlank()) {
            out.put("phoneName", "");
            out.put("phoneState", "NONE");
            out.put("blocked", false);
            out.put("message", "No phone is connected. App: " + appLabel + ".");
            return out;
        }
        MobileDeviceEntity device = devices.findByDeviceId(deviceId).orElse(null);
        String name = device == null || device.getDisplayName() == null || device.getDisplayName().isBlank()
                ? "Selected phone" : device.getDisplayName();
        out.put("phoneName", name);
        String blocked = phoneBlocked(deviceId);
        if (blocked != null) {
            boolean busy = device != null && !"AVAILABLE".equalsIgnoreCase(device.getBusy() == null ? "" : device.getBusy());
            out.put("phoneState", busy ? "BUSY" : "OFFLINE");
            out.put("blocked", true);
            out.put("message", blocked);
            return out;
        }
        if (!orchestrator.runnerOnline()) {
            out.put("phoneState", "READY");
            out.put("blocked", true);
            out.put("runnerOnline", false);
            out.put("message", runners.line());
            return out;
        }
        out.put("phoneState", "READY");
        out.put("blocked", false);
        out.put("message", "Phone: " + name + ". App: " + appLabel + ".");
        return out;
    }

    /** Empty when the project is not Android — the caller keeps the web conversion path. */
    public Optional<ConversionJobResult> tryConvert(JobRecord job, ConversionJobRequest request, BooleanSupplier cancel)
            throws Exception {
        var project = portalStore.getProject(job.getProjectId()).orElse(null);
        if (project == null || !"ANDROID".equalsIgnoreCase(project.getPlatform())) {
            return Optional.empty();
        }
        List<ManualTestCase> cases = KeelPathCaseFilter.requireForSurface(
                new ExcelTcReader(true).read(request.excel()),
                KeelPathCaseFilter.Surface.AUTOMATE,
                "No runnable test cases in workbook - MANUAL rows are skipped on Automate");
        Path projectRoot = new ProjectStore(request.storeRoot(), request.baseUrl(), request.tenantId())
                .projectRoot(request.projectId());
        cases = SuiteCaseFilter.requireFiltered(cases, projectRoot, "No test cases match the suite filter");

        String tenant = job.getTenantId() == null ? "" : job.getTenantId();
        String chosen = deviceByJob.get(job.getJobId());
        if (chosen == null || chosen.isBlank()) {
            throw new IllegalStateException("Choose a connected phone.");
        }
        Map<String, Object> plan = plan(tenant, job.getProjectId(), chosen);
        String pkg = String.valueOf(plan.getOrDefault("appPackage", ""));
        String deviceId = String.valueOf(plan.getOrDefault("deviceId", ""));
        boolean blocked = Boolean.TRUE.equals(plan.get("blocked"));
        if (blocked || !"READY".equals(String.valueOf(plan.get("phoneState")))) {
            String why = String.valueOf(plan.getOrDefault("message", "Choose a connected phone."));
            throw new IllegalStateException(why);
        }
        if (pkg.isBlank()) {
            throw new IllegalStateException("Save an Android app on this project before Automate.");
        }

        Map<String, List<MobileAutomateIr.Locator>> walked = Map.of();
        String how;
        {
            job.setMessage("Opening the phone and walking the workbook steps");
            portalStore.syncJobPersistence(job);
            runners.startForRun();
            walked = walk(job, tenant, deviceId,
                    plan.get("appRevisionId") == null ? null : String.valueOf(plan.get("appRevisionId")),
                    cases, cancel);
            how = "Walked the phone and wrote the locators it found. A control that was not on screen stayed as the Excel wording.";
        }

        List<MobileCaseIr> ir = new ArrayList<>();
        int real = 0;
        int shells = 0;
        for (ManualTestCase tc : cases) {
            List<MobileAutomateIr.Locator> locs = walked.getOrDefault(tc.tcId(), List.of());
            MobileCaseIr built = MobileAutomateIr.build(
                    tc.tcId(), pkg, lines(tc.steps()), lines(tc.testData()), lines(tc.expectedResult()), locs);
            ir.add(built);
            for (var step : built.steps()) {
                if (step.target() == null) {
                    continue;
                }
                if ("HIERARCHY".equals(step.target().origin())) {
                    real++;
                } else {
                    shells++;
                }
            }
        }
        Path work = JobWorkLayout.create(request);
        String[] cursorNote = {""};
        Path zip = MobileAutomatePack.zip(templateRoot(), work, ir, projectDir -> {
            var findings = MobileFrameworkContractValidator.validate(projectDir);
            StringBuilder blocking = new StringBuilder();
            StringBuilder notes = new StringBuilder();
            for (var finding : findings) {
                if (BLOCKING_RULES.contains(finding.code)) {
                    blocking.append(finding).append(' ');
                } else {
                    notes.append(finding).append(' ');
                }
            }
            if (!blocking.isEmpty()) {
                throw new IllegalStateException("Android rules failed. " + blocking.toString().trim());
            }
            cursorNote[0] = cursorPass.reviewFramework(
                    projectDir, job.getJobId(), job.getProjectId(), notes.toString().trim());
        });
        String message = how + " " + cases.size() + " case(s), " + real + " phone locator(s), " + shells + " text shell(s).";
        if (!cursorNote[0].isBlank()) {
            message = message + " " + cursorNote[0];
        }
        job.setMessage(message);
        return Optional.of(new ConversionJobResult(zip, cases.size(), 0, null, message));
    }

    private Map<String, List<MobileAutomateIr.Locator>> walk(
            JobRecord job, String tenant, String deviceId, String revisionId,
            List<ManualTestCase> cases, BooleanSupplier cancel) throws Exception {
        MobileRun run = orchestrator.startWalkProvided(
                tenant, job.getProjectId(), job.getOwnerUserId(), deviceId, cases, revisionId);
        try {
            await(run, cancel);
        } catch (JobCancelledException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            orchestrator.cancel(run.runId());
            throw new IllegalStateException("The phone walk was interrupted.");
        }
        if (!run.status().terminal()) {
            throw new IllegalStateException("The phone walk did not finish.");
        }
        if (run.status() == MobileRun.Status.FAILED || run.status() == MobileRun.Status.CANCELLED) {
            JSONObject view = run.view();
            String why = view.optString("message", "The phone walk failed");
            String err = view.optString("error", "");
            throw new IllegalStateException(err.isBlank() ? why : why + " " + err);
        }
        Map<String, List<MobileAutomateIr.Locator>> out = new LinkedHashMap<>();
        JSONArray results = run.view().optJSONArray("results");
        if (results == null) {
            return Map.of();
        }
        for (int i = 0; i < results.length(); i++) {
            JSONObject row = results.optJSONObject(i);
            if (row == null) {
                continue;
            }
            String tcId = row.optString("tcId", "");
            if (tcId.isBlank()) {
                continue;
            }
            out.put(tcId, readLocators(row.optJSONArray("locators")));
        }
        return out;
    }

    private void await(MobileRun run, BooleanSupplier cancel) throws InterruptedException {
        Instant walkDeadline = Instant.now().plus(WALK_WAIT);
        Instant waitingSince = null;
        while (!run.status().terminal()) {
            if (cancel != null && cancel.getAsBoolean()) {
                orchestrator.cancel(run.runId());
                throw new JobCancelledException();
            }
            if (run.status() == MobileRun.Status.WAITING_FOR_RUNNER) {
                if (waitingSince == null) {
                    waitingSince = Instant.now();
                } else if (Instant.now().isAfter(waitingSince.plus(RUNNER_CLAIM_WAIT))) {
                    orchestrator.cancel(run.runId());
                    throw new IllegalStateException(
                            "The mobile runner did not take the phone within 45 seconds. Start it, then try again.");
                }
            }
            if (Instant.now().isAfter(walkDeadline)) {
                orchestrator.cancel(run.runId());
                throw new IllegalStateException("The phone walk took longer than 3 minutes and was stopped.");
            }
            Thread.sleep(500);
        }
    }

    private String phoneBlocked(String deviceId) {
        MobileDeviceEntity device = devices.findByDeviceId(deviceId).orElse(null);
        if (device == null) {
            return "The selected phone is not registered.";
        }
        if (!"AVAILABLE".equalsIgnoreCase(device.getBusy() == null ? "" : device.getBusy())) {
            return "The selected phone is " + device.getBusy() + ".";
        }
        if (serials.resolve(device.getSerialFingerprint()).isEmpty()) {
            return "The selected phone is not connected to this computer.";
        }
        return null;
    }

    private Path templateRoot() {
        Path configured = Path.of(portalProps.getTemplateRoot() == null ? "" : portalProps.getTemplateRoot());
        Path parent = configured.toAbsolutePath().getParent();
        Path sibling = parent == null
                ? Path.of("customer-mobile-framework-template")
                : parent.resolve("customer-mobile-framework-template");
        if (java.nio.file.Files.isDirectory(sibling)) {
            return sibling;
        }
        return Path.of("customer-mobile-framework-template");
    }

    private static List<MobileAutomateIr.Locator> readLocators(JSONArray arr) {
        List<MobileAutomateIr.Locator> out = new ArrayList<>();
        if (arr == null) {
            return out;
        }
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) {
                continue;
            }
            out.add(new MobileAutomateIr.Locator(
                    o.optString("strategy", "text"),
                    o.optString("value", ""),
                    o.optBoolean("shell", true),
                    o.optString("screenKey", "")));
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
}
