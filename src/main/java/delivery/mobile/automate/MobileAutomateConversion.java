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
import delivery.mobile.persistence.MobileDeviceProfileEntity;
import delivery.mobile.persistence.MobileDeviceProfileRepository;
import delivery.mobile.persistence.MobileDeviceRepository;
import delivery.mobile.run.DeviceSerialResolver;
import delivery.mobile.run.MobileRun;
import delivery.mobile.run.MobileRunOrchestrator;
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
import java.util.function.BooleanSupplier;

/**
 * Android Automate: open the selected phone, follow the workbook, write the locators it bound.
 * No selected or connected phone → the same framework with text shells, and Chrome stays closed.
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
    private final MobileDeviceProfileRepository profiles;
    private final DeviceSerialResolver serials;
    private final MobileRunOrchestrator orchestrator;

    public MobileAutomateConversion(PortalStore portalStore,
                                    DeliveryPortalProperties portalProps,
                                    MobileProperties mobileProps,
                                    MobilePortalStore mobileStore,
                                    MobileDeviceRepository devices,
                                    MobileDeviceProfileRepository profiles,
                                    DeviceSerialResolver serials,
                                    MobileRunOrchestrator orchestrator) {
        this.portalStore = portalStore;
        this.portalProps = portalProps;
        this.mobileProps = mobileProps;
        this.mobileStore = mobileStore;
        this.devices = devices;
        this.profiles = profiles;
        this.serials = serials;
        this.orchestrator = orchestrator;
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
        Optional<MobileAppRevisionRecord> rev = mobileStore.findActiveAppRevision(tenant, job.getProjectId());
        String pkg = rev.map(r -> r.getPackageName() == null ? "" : r.getPackageName()).orElse("");
        String deviceId = latestDeviceId(tenant, job.getProjectId());

        Map<String, List<MobileAutomateIr.Locator>> walked = Map.of();
        String how;
        if (!mobileProps.isEnabled()) {
            how = "Mobile is turned off — wrote text shells and did not open a phone.";
        } else if (deviceId.isBlank()) {
            how = "No phone is selected on this project — wrote text shells.";
        } else if (pkg.isBlank()) {
            how = "No Android package is saved on this project — wrote text shells.";
        } else {
            String blocked = phoneBlocked(deviceId);
            if (blocked != null) {
                how = blocked + " Wrote text shells.";
            } else {
                job.setMessage("Opening the phone and walking the workbook steps");
                portalStore.syncJobPersistence(job);
                walked = walk(job, tenant, deviceId, rev.map(MobileAppRevisionRecord::getAppRevisionId).orElse(null), cases, cancel);
                how = walked.isEmpty()
                        ? "The phone walk did not finish — wrote text shells."
                        : "Walked the phone and wrote the locators it found. Misses stayed as text shells.";
            }
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
        Path zip = MobileAutomatePack.zip(templateRoot(), work, ir);
        String message = how + " " + cases.size() + " case(s), " + real + " phone locator(s), " + shells + " text shell(s).";
        job.setMessage(message);
        return Optional.of(new ConversionJobResult(zip, cases.size(), 0, null, message));
    }

    private Map<String, List<MobileAutomateIr.Locator>> walk(
            JobRecord job, String tenant, String deviceId, String revisionId,
            List<ManualTestCase> cases, BooleanSupplier cancel) {
        List<String> ids = cases.stream().map(ManualTestCase::tcId).toList();
        MobileRun run;
        try {
            run = orchestrator.start(tenant, job.getProjectId(), job.getOwnerUserId(), deviceId, ids, revisionId);
        } catch (Exception e) {
            return Map.of();
        }
        try {
            await(run, cancel);
        } catch (JobCancelledException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            orchestrator.cancel(run.runId());
            return Map.of();
        }
        if (!run.status().terminal()) {
            return Map.of();
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
                    return;
                }
            }
            if (Instant.now().isAfter(walkDeadline)) {
                orchestrator.cancel(run.runId());
                return;
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

    private String latestDeviceId(String tenant, String projectId) {
        MobileDeviceProfileEntity latest = null;
        for (MobileDeviceProfileEntity profile : profiles.findByTenantIdAndProjectId(tenant, projectId)) {
            if (profile.getDeviceId() == null || profile.getDeviceId().isBlank()) {
                continue;
            }
            if (latest == null || (profile.getCreatedAt() != null && latest.getCreatedAt() != null
                    && profile.getCreatedAt().isAfter(latest.getCreatedAt()))) {
                latest = profile;
            }
        }
        return latest == null ? "" : latest.getDeviceId();
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
                    o.optBoolean("shell", true)));
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
