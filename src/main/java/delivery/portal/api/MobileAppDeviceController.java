package delivery.portal.api;

import delivery.mobile.MobilePortalStore;
import delivery.mobile.MobileProperties;
import delivery.mobile.apk.ApkErrorCode;
import delivery.mobile.apk.ApkErrorMessages;
import delivery.mobile.apk.ApkInspectionException;
import delivery.mobile.apk.ApkInspectionRequest;
import delivery.mobile.model.MobileApkUploadResult;
import delivery.mobile.model.MobileAppRevisionRecord;
import delivery.mobile.model.MobileAppSource;
import delivery.mobile.model.MobileDeviceRecord;
import delivery.mobile.model.ProjectPlatform;
import delivery.portal.model.ProjectRecord;
import delivery.portal.security.CurrentUserService;
import delivery.portal.service.PortalStore;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Real portal REST for mobile app/device settings (M1-07).
 * Paths/JSON match Frontend Assumed {@code MobileAppDeviceStubController}.
 * Gated by {@code delivery.mobile.enabled} (default false → 404).
 * OWNER/ADMIN mutate; MEMBER read-only. Never echoes full serial, secrets, or raw host paths.
 * Wires M1-01 models + M1-02 {@link MobilePortalStore#acceptApkUpload} / confirm selection.
 * No Appium jars on portal.
 */
@RestController
@RequestMapping("/api/projects")
public class MobileAppDeviceController {
    private final MobileProperties mobileProperties;
    private final PortalStore store;
    private final MobilePortalStore mobileStore;
    private final CurrentUserService currentUser;

    public MobileAppDeviceController(MobileProperties mobileProperties, PortalStore store,
                                     MobilePortalStore mobileStore, CurrentUserService currentUser) {
        this.mobileProperties = mobileProperties;
        this.store = store;
        this.mobileStore = mobileStore;
        this.currentUser = currentUser;
    }

    public record AppSaveBody(
            String source,
            String packageName,
            Long versionCode,
            String versionName,
            Integer minSdk,
            List<String> abiList,
            String launchActivity,
            String waitActivity,
            String sha256,
            String verification,
            String appId,
            String appRevisionId,
            String certificateDigest,
            String apkBlobRef
    ) {
    }

    public record AppActivitiesBody(String launchActivity, String waitActivity) {
    }

    @GetMapping("/{projectId}/mobile/app")
    public ResponseEntity<?> getApp(@PathVariable String projectId) {
        ResponseEntity<?> gate = gate(projectId, false);
        if (gate != null) {
            return gate;
        }
        ProjectRecord project = store.getOwnedProject(projectId, currentUser.requireUserId()).orElseThrow();
        Optional<MobileAppRevisionRecord> app =
                mobileStore.findActiveAppRevision(project.getTenantId(), projectId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("projectId", projectId);
        out.put("platform", ProjectPlatform.parse(project.getPlatform()).name());
        out.put("app", app.map(this::appToMap).orElse(null));
        out.put("canEdit", store.canOperate(projectId, currentUser.requireUserId()));
        out.put("uploadMaxBytes", mobileProperties.getUploadMaxBytes());
        return ResponseEntity.ok(out);
    }

    @PutMapping(value = "/{projectId}/mobile/app", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> putApp(@PathVariable String projectId,
                                    @RequestBody(required = false) AppSaveBody body) {
        ResponseEntity<?> gate = gate(projectId, true);
        if (gate != null) {
            return gate;
        }
        if (body == null) {
            return ResponseEntity.badRequest()
                    .body(new ApiError("BAD_REQUEST", "app body is required").asMap());
        }
        ProjectRecord project = store.getOwnedProject(projectId, currentUser.requireUserId()).orElseThrow();
        if (ProjectPlatform.parse(project.getPlatform()) != ProjectPlatform.ANDROID) {
            return ResponseEntity.badRequest()
                    .body(new ApiError("BAD_REQUEST", "mobile apps require ANDROID project").asMap());
        }
        try {
            MobileAppRevisionRecord draft = new MobileAppRevisionRecord();
            draft.setSource(body.source() == null ? MobileAppSource.APK.name() : body.source());
            draft.setPackageName(body.packageName());
            draft.setVersionCode(body.versionCode());
            draft.setVersionName(body.versionName());
            draft.setMinSdk(body.minSdk());
            draft.setAbiList(body.abiList() == null ? List.of() : body.abiList());
            draft.setLaunchActivity(body.launchActivity());
            draft.setWaitActivity(body.waitActivity());
            draft.setSha256(body.sha256());
            draft.setVerification(body.verification());
            draft.setAppId(body.appId());
            draft.setAppRevisionId(body.appRevisionId());
            draft.setCertificateDigest(body.certificateDigest());
            draft.setApkBlobRef(body.apkBlobRef());
            MobileAppRevisionRecord saved = mobileStore.registerAppRevision(
                    project.getTenantId(), projectId, draft);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("projectId", projectId);
            out.put("app", appToMap(saved));
            return ResponseEntity.ok(out);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(new ApiError("BAD_REQUEST", e.getMessage()).asMap());
        }
    }

    @PatchMapping(value = "/{projectId}/mobile/app", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> patchAppActivities(@PathVariable String projectId,
                                                @RequestBody(required = false) AppActivitiesBody body) {
        ResponseEntity<?> gate = gate(projectId, true);
        if (gate != null) {
            return gate;
        }
        ProjectRecord project = store.getOwnedProject(projectId, currentUser.requireUserId()).orElseThrow();
        Optional<MobileAppRevisionRecord> current =
                mobileStore.findActiveAppRevision(project.getTenantId(), projectId);
        if (current.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiError("NOT_FOUND", "No app configured for this project").asMap());
        }
        if (body == null) {
            return ResponseEntity.badRequest()
                    .body(new ApiError("BAD_REQUEST", "launchActivity/waitActivity body required").asMap());
        }
        try {
            MobileAppRevisionRecord updated = mobileStore.updateAppRevisionActivities(
                    project.getTenantId(), projectId, current.get().getAppRevisionId(),
                    body.launchActivity(), body.waitActivity());
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("projectId", projectId);
            out.put("app", appToMap(updated));
            return ResponseEntity.ok(out);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(new ApiError("BAD_REQUEST", e.getMessage()).asMap());
        }
    }

    /**
     * Multipart APK upload/inspect via M1-02 {@link MobilePortalStore#acceptApkUpload}.
     * Optional {@code uploadId}+{@code selectedLaunchActivity} confirms a pending selection (422 → 200).
     */
    @PostMapping(value = "/{projectId}/mobile/apps", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadApk(@PathVariable String projectId,
                                       @RequestParam(value = "file", required = false) MultipartFile file,
                                       @RequestParam(value = "selectedLaunchActivity", required = false)
                                       String selectedLaunchActivity,
                                       @RequestParam(value = "waitActivity", required = false) String waitActivity,
                                       @RequestParam(value = "appId", required = false) String appId,
                                       @RequestParam(value = "uploadId", required = false) String uploadId) {
        ResponseEntity<?> gate = gate(projectId, true);
        if (gate != null) {
            return gate;
        }
        ProjectRecord project = store.getOwnedProject(projectId, currentUser.requireUserId()).orElseThrow();
        if (ProjectPlatform.parse(project.getPlatform()) != ProjectPlatform.ANDROID) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(uploadError(ApkErrorCode.APK_INVALID,
                            "mobile apps require ANDROID project", null, null));
        }

        try {
            if (uploadId != null && !uploadId.isBlank()
                    && selectedLaunchActivity != null && !selectedLaunchActivity.isBlank()) {
                MobileApkUploadResult confirmed = mobileStore.confirmApkUploadSelection(
                        project.getTenantId(), projectId, uploadId,
                        selectedLaunchActivity, waitActivity, appId);
                return toUploadResponse(confirmed);
            }

            if (file == null || file.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(uploadError(ApkErrorCode.APK_INVALID, "APK file is required.", null, null));
            }

            ApkInspectionRequest req = new ApkInspectionRequest();
            if (selectedLaunchActivity != null && !selectedLaunchActivity.isBlank()) {
                req.setSelectedLaunchActivity(selectedLaunchActivity);
            }
            if (waitActivity != null && !waitActivity.isBlank()) {
                req.setSelectedWaitActivity(waitActivity);
            }

            MobileApkUploadResult result = mobileStore.acceptApkUpload(
                    project.getTenantId(),
                    projectId,
                    appId,
                    file.getOriginalFilename(),
                    file.getInputStream(),
                    req);
            return toUploadResponse(result);
        } catch (ApkInspectionException e) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(uploadError(e.getCode(),
                            ApkErrorMessages.plainOrFallback(e.getCode(), e.getMessage()),
                            null, uploadId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(uploadError(ApkErrorCode.APK_INVALID, e.getMessage(), null, null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(uploadError(ApkErrorCode.APK_INVALID,
                            ApkErrorMessages.plain(ApkErrorCode.APK_INVALID), null, null));
        }
    }

    @GetMapping("/{projectId}/mobile/devices")
    public ResponseEntity<?> listDevices(@PathVariable String projectId) {
        ResponseEntity<?> gate = gate(projectId, false);
        if (gate != null) {
            return gate;
        }
        ProjectRecord project = store.getOwnedProject(projectId, currentUser.requireUserId()).orElseThrow();
        List<Map<String, Object>> devices = new ArrayList<>();
        for (MobileDeviceRecord d : mobileStore.listDevices(project.getTenantId())) {
            devices.add(deviceToMap(d));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("projectId", projectId);
        out.put("devices", devices);
        out.put("canEdit", store.canOperate(projectId, currentUser.requireUserId()));
        return ResponseEntity.ok(out);
    }

    private ResponseEntity<?> toUploadResponse(MobileApkUploadResult result) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", result.getStatus());
        out.put("revision", result.getRevision() == null ? null : appToMap(result.getRevision()));
        out.put("launcherCandidates",
                result.getLauncherCandidates() == null ? List.of() : result.getLauncherCandidates());
        String errorCode = result.getErrorCode();
        out.put("errorCode", errorCode);
        if (errorCode != null && !errorCode.isBlank()) {
            try {
                out.put("message", ApkErrorMessages.plain(ApkErrorCode.valueOf(errorCode)));
            } catch (IllegalArgumentException ignored) {
                out.put("message", result.getMessage());
            }
        } else {
            out.put("message", result.getMessage());
        }
        out.put("sha256", result.getSha256());
        out.put("uploadId",
                "ACTIVITY_SELECTION_REQUIRED".equals(result.getStatus()) ? result.getUploadId() : null);
        if ("ACTIVITY_SELECTION_REQUIRED".equals(result.getStatus())) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(out);
        }
        return ResponseEntity.ok(out);
    }

    private ResponseEntity<?> gate(String projectId, boolean mutate) {
        if (!mobileProperties.isEnabled()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiError("NOT_FOUND", "Mobile is disabled").asMap());
        }
        Long uid = currentUser.requireUserId();
        if (mutate) {
            return ProjectAccess.denyUnlessOperable(store, projectId, uid);
        }
        return ProjectAccess.denyUnlessReadable(store, projectId, uid);
    }

    private Map<String, Object> uploadError(ApkErrorCode code, String message, String sha256, String uploadId) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", null);
        out.put("revision", null);
        out.put("launcherCandidates", List.of());
        out.put("errorCode", code == null ? ApkErrorCode.APK_INVALID.name() : code.name());
        out.put("message", message == null ? ApkErrorMessages.plain(code) : message);
        out.put("sha256", sha256);
        out.put("uploadId", uploadId);
        return out;
    }

    private Map<String, Object> appToMap(MobileAppRevisionRecord r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("appRevisionId", r.getAppRevisionId());
        m.put("appId", r.getAppId());
        m.put("projectId", r.getProjectId());
        m.put("tenantId", r.getTenantId());
        m.put("source", r.getSource());
        m.put("packageName", r.getPackageName());
        m.put("versionCode", r.getVersionCode());
        m.put("versionName", r.getVersionName());
        m.put("minSdk", r.getMinSdk());
        m.put("abiList", r.getAbiList() == null ? List.of() : r.getAbiList());
        m.put("launchActivity", r.getLaunchActivity());
        m.put("waitActivity", r.getWaitActivity());
        m.put("sha256", r.getSha256());
        m.put("verification", r.getVerification());
        m.put("certificateDigest", r.getCertificateDigest());
        String blob = r.getApkBlobRef();
        if (blob == null || blob.isBlank()) {
            if (r.getAppRevisionId() != null) {
                blob = "mobile/apps/" + r.getAppRevisionId() + "/app.apk";
            }
        }
        // Never emit raw host paths
        if (blob != null && (blob.contains(":\\") || (blob.startsWith("/") && blob.contains("/home/")))) {
            blob = "mobile/apps/" + r.getAppRevisionId() + "/app.apk";
        }
        m.put("apkBlobRef", blob);
        return m;
    }

    private Map<String, Object> deviceToMap(MobileDeviceRecord d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("deviceId", d.getDeviceId());
        m.put("displayName", d.getDisplayName());
        m.put("serialShort", d.getSerialShort());
        m.put("model", d.getModel());
        m.put("apiLevel", d.getApiLevel());
        m.put("abi", d.getAbi());
        m.put("authorizedState", d.getAuthorizedState());
        m.put("readiness", d.getReadiness());
        m.put("busy", d.getBusy());
        m.put("hostId", d.getHostId());
        m.put("tenantId", d.getTenantId());
        return m;
    }
}
