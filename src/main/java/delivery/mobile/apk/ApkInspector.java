package delivery.mobile.apk;

import delivery.mobile.MobileProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * M1-02: validate quarantined APK content, inspect manifest via SDK tools (never execute APK),
 * apply package/API/ABI/signature/downgrade gates. No auto-uninstall fallback.
 */
@Component
public class ApkInspector {
    private final MobileProperties props;
    private final ApkAnalysisBackend backend;

    public ApkInspector(MobileProperties props, ApkAnalysisBackend backend) {
        this.props = props == null ? new MobileProperties() : props;
        this.backend = backend;
    }

    /**
     * Inspect a file already streamed into quarantine. {@code sha256} is the upload digest.
     * {@code apkBlobRef} is a logical store reference (not a filesystem escape hatch).
     */
    public ApkInspectionOutcome inspect(Path quarantinedApk,
                                        String sha256,
                                        String apkBlobRef,
                                        ApkInspectionRequest request) throws ApkInspectionException {
        ApkZipValidator.validate(
                quarantinedApk,
                props.getZipMaxEntries(),
                props.getZipMaxUncompressedBytes() > 0
                        ? props.getZipMaxUncompressedBytes()
                        : props.getUploadMaxBytes());

        ApkManifestInfo info = backend.analyze(quarantinedApk);
        ApkInspectionRequest req = request == null ? new ApkInspectionRequest() : request;

        boolean allowDowngrade = req.isAllowDowngrade() || props.isAllowDowngrade();
        boolean allowSig = req.isAllowSignatureReplacement() || props.isAllowSignatureReplacement();

        try {
            ApkRevisionPolicy.enforce(
                    info,
                    req.getExpectedPackage(),
                    req.getPriorRevision(),
                    req.getDeviceConstraints(),
                    allowDowngrade,
                    allowSig);
        } catch (ApkInspectionException e) {
            throw e;
        }

        try {
            ApkRevisionPolicy.ActivityChoice activities = ApkRevisionPolicy.resolveActivities(
                    info, req.getSelectedLaunchActivity(), req.getSelectedWaitActivity());
            return ApkInspectionOutcome.ready(info, activities, sha256, apkBlobRef);
        } catch (ApkInspectionException e) {
            if (e.getCode() == ApkErrorCode.ACTIVITY_REQUIRED
                    && info.getLauncherActivities() != null
                    && info.getLauncherActivities().size() > 1
                    && (req.getSelectedLaunchActivity() == null || req.getSelectedLaunchActivity().isBlank())) {
                return ApkInspectionOutcome.selectionRequired(
                        info, info.getLauncherActivities(), sha256, apkBlobRef);
            }
            throw e;
        }
    }

    /** Tripwire for acceptance: inspector never authorizes silent uninstall. */
    public boolean allowsAutoUninstallFallback() {
        return ApkRevisionPolicy.autoUninstallFallbackAllowed();
    }
}
