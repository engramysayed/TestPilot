package delivery.mobile;

import delivery.mobile.apk.ApkErrorCode;
import delivery.mobile.apk.ApkErrorMessages;
import delivery.mobile.apk.ApkInspectionException;
import delivery.mobile.apk.ApkInspectionOutcome;
import delivery.mobile.apk.ApkInspectionRequest;
import delivery.mobile.apk.ApkInspector;
import delivery.mobile.apk.ApkRevisionPolicy;
import delivery.mobile.apk.ApkUploadQuarantine;
import delivery.mobile.lease.DeviceLeaseException;
import delivery.mobile.lease.DeviceLeaseGuard;
import delivery.mobile.lease.LeaseHandle;
import delivery.mobile.model.MobileApkUploadResult;
import delivery.mobile.model.MobileAppRevisionRecord;
import delivery.mobile.model.MobileAppSource;
import delivery.mobile.model.MobileAuthorizedState;
import delivery.mobile.model.MobileDeviceBusy;
import delivery.mobile.model.MobileDeviceReadiness;
import delivery.mobile.model.MobileDeviceRecord;
import delivery.mobile.model.MobileVerification;
import delivery.mobile.model.ProjectPlatform;
import delivery.mobile.persistence.MobileAppEntity;
import delivery.mobile.persistence.MobileAppRepository;
import delivery.mobile.persistence.MobileAppRevisionEntity;
import delivery.mobile.persistence.MobileAppRevisionRepository;
import delivery.mobile.persistence.MobileDeviceEntity;
import delivery.mobile.persistence.MobileDeviceLeaseEntity;
import delivery.mobile.persistence.MobileDeviceLeaseRepository;
import delivery.mobile.persistence.MobileDeviceProfileEntity;
import delivery.mobile.persistence.MobileDeviceProfileRepository;
import delivery.mobile.persistence.MobileDeviceRepository;
import delivery.portal.DeliveryPortalProperties;
import delivery.portal.persistence.ProjectEntity;
import delivery.portal.persistence.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Portal-side mobile persistence (no Appium). M1-01 entities + M1-02 APK + M1-04 lease/fence.
 * Tenant-scoped foreign references are rejected. App revisions are immutable inserts only.
 */
@Service
public class MobilePortalStore {
    private final ProjectRepository projectRepository;
    private final MobileAppRepository appRepository;
    private final MobileAppRevisionRepository revisionRepository;
    private final MobileDeviceProfileRepository profileRepository;
    private final MobileDeviceRepository deviceRepository;
    private final MobileDeviceLeaseRepository leaseRepository;
    private final ApkInspector apkInspector;
    private final MobileProperties mobileProperties;
    private final DeliveryPortalProperties portalProperties;
    private final DeviceLeaseGuard deviceLeaseGuard;

    public MobilePortalStore(ProjectRepository projectRepository,
                             MobileAppRepository appRepository,
                             MobileAppRevisionRepository revisionRepository,
                             MobileDeviceProfileRepository profileRepository,
                             MobileDeviceRepository deviceRepository,
                             MobileDeviceLeaseRepository leaseRepository,
                             ApkInspector apkInspector,
                             MobileProperties mobileProperties,
                             DeliveryPortalProperties portalProperties,
                             DeviceLeaseGuard deviceLeaseGuard) {
        this.projectRepository = projectRepository;
        this.appRepository = appRepository;
        this.revisionRepository = revisionRepository;
        this.profileRepository = profileRepository;
        this.deviceRepository = deviceRepository;
        this.leaseRepository = leaseRepository;
        this.apkInspector = apkInspector;
        this.mobileProperties = mobileProperties;
        this.portalProperties = portalProperties;
        this.deviceLeaseGuard = deviceLeaseGuard;
    }

    public ProjectPlatform platformOf(String projectId) {
        return projectRepository.findByProjectId(projectId)
                .map(e -> ProjectPlatform.parse(e.getPlatform()))
                .orElse(ProjectPlatform.WEB);
    }

    /**
     * Reject cross-tenant project references (foreign projectId/tenantId pair).
     * @throws IllegalArgumentException when project missing or tenant mismatch
     */
    public ProjectEntity requireProjectInTenant(String projectId, String tenantId) {
        ProjectEntity project = projectRepository.findByProjectId(projectId)
                .orElseThrow(() -> new IllegalArgumentException("project not found"));
        String pt = project.getTenantId() == null ? "" : project.getTenantId();
        String want = tenantId == null ? "" : tenantId;
        if (!pt.equals(want)) {
            throw new IllegalArgumentException("tenant foreign reference rejected");
        }
        return project;
    }

    /**
     * Stream APK into tenant/project quarantine, inspect, and on READY freeze an immutable revision.
     * Does not auto-uninstall anything on a device. Rejects remote URLs / traversal / policy failures.
     */
    @Transactional
    public MobileApkUploadResult acceptApkUpload(String tenantId,
                                                 String projectId,
                                                 String appIdOrNull,
                                                 String originalFilename,
                                                 InputStream apkStream,
                                                 ApkInspectionRequest inspectionRequest)
            throws ApkInspectionException {
        ProjectEntity project = requireProjectInTenant(projectId, tenantId);
        if (ProjectPlatform.parse(project.getPlatform()) != ProjectPlatform.ANDROID) {
            throw new IllegalArgumentException("mobile apps require ANDROID project");
        }
        Path storeRoot = Path.of(portalProperties.getStoreRoot()).toAbsolutePath().normalize();
        long maxBytes = mobileProperties.getUploadMaxBytes();
        ApkUploadQuarantine.QuarantinedUpload upload;
        try {
            upload = ApkUploadQuarantine.streamToQuarantine(
                    storeRoot, tenantId, projectId, originalFilename, apkStream, maxBytes);
        } catch (ApkInspectionException e) {
            throw e;
        } catch (Exception e) {
            throw new ApkInspectionException(ApkErrorCode.APK_INVALID, "quarantine failed: " + e.getMessage());
        }

        ApkInspectionRequest req = inspectionRequest == null ? new ApkInspectionRequest() : inspectionRequest;
        if (appIdOrNull != null && !appIdOrNull.isBlank()) {
            Optional<MobileAppEntity> existingApp = appRepository.findByAppId(appIdOrNull);
            if (existingApp.isPresent()) {
                MobileAppEntity app = existingApp.get();
                if (!tenantId.equals(app.getTenantId()) || !projectId.equals(app.getProjectId())) {
                    ApkUploadQuarantine.discard(upload);
                    throw new IllegalArgumentException("tenant foreign reference rejected");
                }
                priorFromApp(app).ifPresent(req::setPriorRevision);
            }
        }

        String blobRef = "mobile/quarantine/" + upload.uploadId() + "/upload.apk";
        ApkInspectionOutcome outcome;
        try {
            outcome = apkInspector.inspect(upload.apkPath(), upload.sha256Hex(), blobRef, req);
        } catch (ApkInspectionException e) {
            ApkUploadQuarantine.discard(upload);
            throw e;
        }

        MobileApkUploadResult result = new MobileApkUploadResult();
        result.setSha256(upload.sha256Hex());
        result.setUploadId(upload.uploadId());

        if (outcome.getStatus() == ApkInspectionOutcome.Status.ACTIVITY_SELECTION_REQUIRED) {
            result.setStatus("ACTIVITY_SELECTION_REQUIRED");
            result.setErrorCode(ApkErrorCode.ACTIVITY_REQUIRED.name());
            result.setMessage(ApkErrorMessages.plain(ApkErrorCode.ACTIVITY_REQUIRED));
            result.setLauncherCandidates(outcome.getLauncherCandidates());
            // leave bytes in quarantine for a follow-up selection call
            return result;
        }

        String revisionId = "apprev_" + shortId();
        Path finalApk = ApkUploadQuarantine.revisionApkPath(storeRoot, tenantId, projectId, revisionId);
        try {
            ApkUploadQuarantine.promoteToRevision(upload, finalApk);
        } catch (Exception e) {
            ApkUploadQuarantine.discard(upload);
            throw new ApkInspectionException(ApkErrorCode.APK_INVALID, "promote failed: " + e.getMessage());
        }

        String logicalBlob = "mobile/apps/" + revisionId + "/app.apk";
        MobileAppRevisionRecord draft = new MobileAppRevisionRecord();
        draft.setAppId(appIdOrNull);
        draft.setAppRevisionId(revisionId);
        draft.setSource(MobileAppSource.APK.name());
        draft.setPackageName(outcome.getManifest().getPackageName());
        draft.setVersionCode(outcome.getManifest().getVersionCode());
        draft.setVersionName(outcome.getManifest().getVersionName());
        draft.setMinSdk(outcome.getManifest().getMinSdk());
        draft.setAbiList(outcome.getManifest().getAbiList());
        draft.setLaunchActivity(outcome.getLaunchActivity());
        draft.setWaitActivity(outcome.getWaitActivity());
        draft.setSha256(upload.sha256Hex());
        draft.setCertificateDigest(outcome.getManifest().getCertificateDigest());
        draft.setApkBlobRef(logicalBlob);
        draft.setVerification(MobileVerification.UPLOADED.name());

        MobileAppRevisionRecord frozen = registerAppRevision(tenantId, projectId, draft);
        // persist blob ref + cert on entity (registerAppRevision may not have set them before this patch)
        revisionRepository.findByRevisionId(frozen.getAppRevisionId()).ifPresent(rev -> {
            rev.setApkBlobRef(logicalBlob);
            rev.setCertificateDigest(outcome.getManifest().getCertificateDigest());
            revisionRepository.save(rev);
        });

        result.setStatus("READY");
        result.setRevision(frozen);
        return result;
    }

    private Optional<ApkRevisionPolicy.PriorRevision> priorFromApp(MobileAppEntity app) {
        if (app.getActiveRevisionId() == null) {
            return Optional.empty();
        }
        return revisionRepository.findByRevisionId(app.getActiveRevisionId())
                .map(r -> new ApkRevisionPolicy.PriorRevision(
                        r.getPackageName(), r.getVersionCode(), r.getCertificateDigest()));
    }

    /** Latest revision for app by createdAt (for tests / policy). */
    public Optional<MobileAppRevisionEntity> latestRevision(String appId) {
        return revisionRepository.findByAppId(appId).stream()
                .max(Comparator.comparing(MobileAppRevisionEntity::getCreatedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())));
    }

    @Transactional
    public MobileAppRevisionRecord registerAppRevision(String tenantId, String projectId, MobileAppRevisionRecord draft) {
        ProjectEntity project = requireProjectInTenant(projectId, tenantId);
        if (ProjectPlatform.parse(project.getPlatform()) != ProjectPlatform.ANDROID) {
            throw new IllegalArgumentException("mobile apps require ANDROID project");
        }
        String appId = draft.getAppId() == null || draft.getAppId().isBlank()
                ? "app_" + shortId() : draft.getAppId();
        MobileAppEntity app = appRepository.findByAppId(appId).orElseGet(MobileAppEntity::new);
        if (app.getAppId() == null) {
            app.setAppId(appId);
            app.setTenantId(tenantId);
            app.setProjectId(projectId);
            app.setSource(MobileAppSource.parse(draft.getSource()).name());
            app.setLifecycle("ACTIVE");
            app.setCreatedAt(Instant.now());
        } else {
            if (!tenantId.equals(app.getTenantId()) || !projectId.equals(app.getProjectId())) {
                throw new IllegalArgumentException("tenant foreign reference rejected");
            }
        }
        String revisionId = draft.getAppRevisionId() == null || draft.getAppRevisionId().isBlank()
                ? "apprev_" + shortId() : draft.getAppRevisionId();
        // Immutability: never update an existing revision row — insert only
        if (revisionRepository.findByRevisionId(revisionId).isPresent()) {
            throw new IllegalStateException("app revision is immutable: " + revisionId);
        }
        MobileAppRevisionEntity rev = new MobileAppRevisionEntity();
        rev.setRevisionId(revisionId);
        rev.setAppId(appId);
        rev.setTenantId(tenantId);
        rev.setProjectId(projectId);
        rev.setSource(MobileAppSource.parse(draft.getSource()).name());
        rev.setPackageName(draft.getPackageName());
        rev.setVersionCode(draft.getVersionCode());
        rev.setVersionName(draft.getVersionName());
        rev.setMinSdk(draft.getMinSdk());
        rev.setAbiList(joinAbis(draft.getAbiList()));
        rev.setLaunchActivity(draft.getLaunchActivity());
        rev.setWaitActivity(draft.getWaitActivity());
        rev.setSha256(draft.getSha256());
        rev.setApkBlobRef(draft.getApkBlobRef());
        rev.setCertificateDigest(draft.getCertificateDigest());
        rev.setVerification(draft.getVerification() == null || draft.getVerification().isBlank()
                ? MobileVerification.UPLOADED.name()
                : MobileVerification.valueOf(draft.getVerification().trim().toUpperCase()).name());
        rev.setCreatedAt(Instant.now());
        revisionRepository.save(rev);
        app.setActiveRevisionId(revisionId);
        appRepository.save(app);
        return toRevisionRecord(rev);
    }

    public List<MobileAppRevisionRecord> listAppRevisions(String tenantId, String projectId) {
        requireProjectInTenant(projectId, tenantId);
        return revisionRepository.findByTenantIdAndProjectId(tenantId, projectId).stream()
                .map(this::toRevisionRecord)
                .collect(Collectors.toList());
    }

    @Transactional
    public MobileDeviceProfileEntity createDeviceProfile(String tenantId, String projectId, String displayName,
                                                         String deviceId, String appRevisionId) {
        requireProjectInTenant(projectId, tenantId);
        if (deviceId != null && !deviceId.isBlank()) {
            MobileDeviceEntity device = deviceRepository.findByDeviceId(deviceId)
                    .orElseThrow(() -> new IllegalArgumentException("device not found"));
            if (!tenantId.equals(device.getTenantId())) {
                throw new IllegalArgumentException("tenant foreign reference rejected");
            }
        }
        if (appRevisionId != null && !appRevisionId.isBlank()) {
            MobileAppRevisionEntity rev = revisionRepository.findByRevisionId(appRevisionId)
                    .orElseThrow(() -> new IllegalArgumentException("app revision not found"));
            if (!tenantId.equals(rev.getTenantId()) || !projectId.equals(rev.getProjectId())) {
                throw new IllegalArgumentException("tenant foreign reference rejected");
            }
        }
        MobileDeviceProfileEntity profile = new MobileDeviceProfileEntity();
        profile.setProfileRevisionId("devrev_" + shortId());
        profile.setTenantId(tenantId);
        profile.setProjectId(projectId);
        profile.setDisplayName(displayName);
        profile.setDeviceId(deviceId);
        profile.setAppRevisionId(appRevisionId);
        profile.setCreatedAt(Instant.now());
        return profileRepository.save(profile);
    }

    @Transactional
    public MobileDeviceRecord registerDevice(String tenantId, String hostId, String serialFingerprint,
                                             String serialShort, String displayName, String model,
                                             Integer apiLevel, String abi) {
        Optional<MobileDeviceEntity> existing = deviceRepository.findByHostIdAndSerialFingerprint(hostId, serialFingerprint);
        if (existing.isPresent()) {
            MobileDeviceEntity other = existing.get();
            if (!tenantId.equals(other.getTenantId())) {
                throw new IllegalArgumentException("tenant foreign reference rejected");
            }
            return toDeviceRecord(other);
        }
        MobileDeviceEntity device = new MobileDeviceEntity();
        device.setDeviceId("dev_" + shortId());
        device.setTenantId(tenantId);
        device.setHostId(hostId);
        device.setSerialFingerprint(serialFingerprint);
        device.setSerialShort(serialShort);
        device.setDisplayName(displayName);
        device.setModel(model);
        device.setApiLevel(apiLevel);
        device.setAbi(abi);
        device.setAuthorizedState(MobileAuthorizedState.AUTHORIZED.name());
        device.setReadiness(MobileDeviceReadiness.READY.name());
        device.setBusy(MobileDeviceBusy.AVAILABLE.name());
        device.setLastSeenAt(Instant.now());
        device.setCreatedAt(Instant.now());
        return toDeviceRecord(deviceRepository.save(device));
    }

    public List<MobileDeviceRecord> listDevices(String tenantId) {
        return deviceRepository.findByTenantId(tenantId).stream()
                .map(this::toDeviceRecord)
                .collect(Collectors.toList());
    }

    /**
     * M1-04: delegates to {@link DeviceLeaseGuard} (host lock + durable fence).
     * Preserves M1-01 IllegalArgumentException / DEVICE_BUSY IllegalStateException shapes.
     */
    public MobileDeviceLeaseEntity createLease(String tenantId, String deviceId, String jobId, String attemptId) {
        try {
            LeaseHandle handle = deviceLeaseGuard.reserve(tenantId, deviceId, jobId, attemptId, null);
            return handle.entity();
        } catch (DeviceLeaseException e) {
            switch (e.getCode()) {
                case DEVICE_BUSY, HOST_LOCK_BUSY -> throw new IllegalStateException("DEVICE_BUSY", e);
                case TENANT_MISMATCH -> throw new IllegalArgumentException("tenant foreign reference rejected", e);
                case DEVICE_NOT_FOUND -> throw new IllegalArgumentException("device not found", e);
                default -> throw new IllegalStateException(e.getMessage(), e);
            }
        }
    }

    public DeviceLeaseGuard deviceLeaseGuard() {
        return deviceLeaseGuard;
    }


    /** Active app revision for the project, if any (head activeRevisionId or latest by createdAt). */
    public Optional<MobileAppRevisionRecord> findActiveAppRevision(String tenantId, String projectId) {
        requireProjectInTenant(projectId, tenantId);
        List<MobileAppEntity> apps = appRepository.findByTenantIdAndProjectId(tenantId, projectId);
        for (MobileAppEntity app : apps) {
            if (app.getActiveRevisionId() != null && !app.getActiveRevisionId().isBlank()) {
                Optional<MobileAppRevisionEntity> rev = revisionRepository.findByRevisionId(app.getActiveRevisionId());
                if (rev.isPresent()) {
                    return Optional.of(toRevisionRecord(rev.get()));
                }
            }
        }
        return listAppRevisions(tenantId, projectId).stream().findFirst();
    }

    /**
     * Editable fields only (launchActivity / waitActivity) on an existing revision.
     * Matches Frontend Assumed PATCH semantics (M1-07 REST).
     */
    @Transactional
    public MobileAppRevisionRecord updateAppRevisionActivities(String tenantId, String projectId,
                                                               String appRevisionId,
                                                               String launchActivity,
                                                               String waitActivity) {
        requireProjectInTenant(projectId, tenantId);
        MobileAppRevisionEntity rev = revisionRepository.findByRevisionId(appRevisionId)
                .orElseThrow(() -> new IllegalArgumentException("app revision not found"));
        if (!tenantId.equals(rev.getTenantId()) || !projectId.equals(rev.getProjectId())) {
            throw new IllegalArgumentException("tenant foreign reference rejected");
        }
        if (launchActivity != null) {
            rev.setLaunchActivity(launchActivity);
        }
        if (waitActivity != null) {
            rev.setWaitActivity(waitActivity);
        }
        return toRevisionRecord(revisionRepository.save(rev));
    }

    /**
     * Complete a quarantined upload after ACTIVITY_SELECTION_REQUIRED by re-inspecting with
     * {@code selectedLaunchActivity} (wait defaults to launch when omitted).
     */
    @Transactional
    public MobileApkUploadResult confirmApkUploadSelection(String tenantId,
                                                           String projectId,
                                                           String uploadId,
                                                           String selectedLaunchActivity,
                                                           String waitActivity,
                                                           String appIdOrNull)
            throws ApkInspectionException {
        if (uploadId == null || uploadId.isBlank()) {
            throw new ApkInspectionException(ApkErrorCode.APK_INVALID, "uploadId required");
        }
        if (selectedLaunchActivity == null || selectedLaunchActivity.isBlank()) {
            throw new ApkInspectionException(ApkErrorCode.ACTIVITY_REQUIRED,
                    ApkErrorMessages.plain(ApkErrorCode.ACTIVITY_REQUIRED));
        }
        ProjectEntity project = requireProjectInTenant(projectId, tenantId);
        if (ProjectPlatform.parse(project.getPlatform()) != ProjectPlatform.ANDROID) {
            throw new IllegalArgumentException("mobile apps require ANDROID project");
        }
        Path storeRoot = Path.of(portalProperties.getStoreRoot()).toAbsolutePath().normalize();
        Path apkPath = ApkUploadQuarantine.quarantineRoot(storeRoot, tenantId, projectId)
                .resolve(uploadId.trim())
                .resolve("upload.apk");
        if (!java.nio.file.Files.isRegularFile(apkPath)) {
            throw new ApkInspectionException(ApkErrorCode.APK_INVALID,
                    "Unknown or expired upload. Upload the APK again.");
        }
        String sha256;
        try {
            sha256 = sha256Of(apkPath);
        } catch (Exception e) {
            throw new ApkInspectionException(ApkErrorCode.APK_INVALID, "quarantine digest failed");
        }
        ApkInspectionRequest req = new ApkInspectionRequest();
        req.setSelectedLaunchActivity(selectedLaunchActivity);
        req.setSelectedWaitActivity(waitActivity);
        if (appIdOrNull != null && !appIdOrNull.isBlank()) {
            Optional<MobileAppEntity> existingApp = appRepository.findByAppId(appIdOrNull);
            if (existingApp.isPresent()) {
                MobileAppEntity app = existingApp.get();
                if (!tenantId.equals(app.getTenantId()) || !projectId.equals(app.getProjectId())) {
                    throw new IllegalArgumentException("tenant foreign reference rejected");
                }
                priorFromApp(app).ifPresent(req::setPriorRevision);
            }
        }
        String blobRef = "mobile/quarantine/" + uploadId.trim() + "/upload.apk";
        ApkInspectionOutcome outcome = apkInspector.inspect(apkPath, sha256, blobRef, req);
        if (outcome.getStatus() == ApkInspectionOutcome.Status.ACTIVITY_SELECTION_REQUIRED) {
            MobileApkUploadResult result = new MobileApkUploadResult();
            result.setSha256(sha256);
            result.setUploadId(uploadId.trim());
            result.setStatus("ACTIVITY_SELECTION_REQUIRED");
            result.setErrorCode(ApkErrorCode.ACTIVITY_REQUIRED.name());
            result.setMessage(ApkErrorMessages.plain(ApkErrorCode.ACTIVITY_REQUIRED));
            result.setLauncherCandidates(outcome.getLauncherCandidates());
            return result;
        }
        ApkUploadQuarantine.QuarantinedUpload upload = new ApkUploadQuarantine.QuarantinedUpload(
                apkPath, uploadId.trim(), sha256, 0L, "upload.apk");
        String revisionId = "apprev_" + shortId();
        Path finalApk = ApkUploadQuarantine.revisionApkPath(storeRoot, tenantId, projectId, revisionId);
        try {
            ApkUploadQuarantine.promoteToRevision(upload, finalApk);
        } catch (Exception e) {
            throw new ApkInspectionException(ApkErrorCode.APK_INVALID, "promote failed: " + e.getMessage());
        }
        String logicalBlob = "mobile/apps/" + revisionId + "/app.apk";
        MobileAppRevisionRecord draft = new MobileAppRevisionRecord();
        draft.setAppId(appIdOrNull);
        draft.setAppRevisionId(revisionId);
        draft.setSource(MobileAppSource.APK.name());
        draft.setPackageName(outcome.getManifest().getPackageName());
        draft.setVersionCode(outcome.getManifest().getVersionCode());
        draft.setVersionName(outcome.getManifest().getVersionName());
        draft.setMinSdk(outcome.getManifest().getMinSdk());
        draft.setAbiList(outcome.getManifest().getAbiList());
        draft.setLaunchActivity(outcome.getLaunchActivity());
        draft.setWaitActivity(outcome.getWaitActivity());
        draft.setSha256(sha256);
        draft.setCertificateDigest(outcome.getManifest().getCertificateDigest());
        draft.setApkBlobRef(logicalBlob);
        draft.setVerification(MobileVerification.UPLOADED.name());
        MobileAppRevisionRecord frozen = registerAppRevision(tenantId, projectId, draft);
        revisionRepository.findByRevisionId(frozen.getAppRevisionId()).ifPresent(rev -> {
            rev.setApkBlobRef(logicalBlob);
            rev.setCertificateDigest(outcome.getManifest().getCertificateDigest());
            revisionRepository.save(rev);
        });
        MobileApkUploadResult result = new MobileApkUploadResult();
        result.setSha256(sha256);
        result.setStatus("READY");
        result.setRevision(frozen);
        return result;
    }

    private static String sha256Of(Path file) throws Exception {
        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
        try (var in = java.nio.file.Files.newInputStream(file)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) >= 0) {
                digest.update(buf, 0, n);
            }
        }
        return java.util.HexFormat.of().formatHex(digest.digest());
    }

    private MobileAppRevisionRecord toRevisionRecord(MobileAppRevisionEntity e) {
        MobileAppRevisionRecord r = new MobileAppRevisionRecord();
        r.setAppRevisionId(e.getRevisionId());
        r.setAppId(e.getAppId());
        r.setProjectId(e.getProjectId());
        r.setTenantId(e.getTenantId());
        r.setSource(e.getSource());
        r.setPackageName(e.getPackageName());
        r.setVersionCode(e.getVersionCode());
        r.setVersionName(e.getVersionName());
        r.setMinSdk(e.getMinSdk());
        r.setAbiList(splitAbis(e.getAbiList()));
        r.setLaunchActivity(e.getLaunchActivity());
        r.setWaitActivity(e.getWaitActivity());
        r.setSha256(e.getSha256());
        r.setVerification(e.getVerification());
        r.setCertificateDigest(e.getCertificateDigest());
        r.setApkBlobRef(e.getApkBlobRef());
        return r;
    }

    private MobileDeviceRecord toDeviceRecord(MobileDeviceEntity e) {
        MobileDeviceRecord r = new MobileDeviceRecord();
        r.setDeviceId(e.getDeviceId());
        r.setTenantId(e.getTenantId());
        r.setHostId(e.getHostId());
        r.setDisplayName(e.getDisplayName());
        r.setSerialShort(e.getSerialShort());
        r.setModel(e.getModel());
        r.setApiLevel(e.getApiLevel());
        r.setAbi(e.getAbi());
        r.setAuthorizedState(e.getAuthorizedState());
        r.setReadiness(e.getReadiness());
        r.setBusy(e.getBusy());
        return r;
    }

    private static String shortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private static String joinAbis(List<String> abis) {
        if (abis == null || abis.isEmpty()) {
            return null;
        }
        return String.join(",", abis);
    }

    private static List<String> splitAbis(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }
}
