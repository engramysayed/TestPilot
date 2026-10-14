package delivery.mobile.apk;

import java.util.ArrayList;
import java.util.List;

/**
 * Successful inspection ready to freeze as an immutable app revision,
 * or activity-selection-required detail (HTTP 422 shape for later API).
 */
public final class ApkInspectionOutcome {
    public enum Status { READY, ACTIVITY_SELECTION_REQUIRED }

    private final Status status;
    private final ApkManifestInfo manifest;
    private final String launchActivity;
    private final String waitActivity;
    private final List<String> launcherCandidates;
    private final String sha256;
    private final String apkBlobRef;

    private ApkInspectionOutcome(Status status,
                                 ApkManifestInfo manifest,
                                 String launchActivity,
                                 String waitActivity,
                                 List<String> launcherCandidates,
                                 String sha256,
                                 String apkBlobRef) {
        this.status = status;
        this.manifest = manifest;
        this.launchActivity = launchActivity;
        this.waitActivity = waitActivity;
        this.launcherCandidates = launcherCandidates == null
                ? List.of()
                : List.copyOf(new ArrayList<>(launcherCandidates));
        this.sha256 = sha256;
        this.apkBlobRef = apkBlobRef;
    }

    public static ApkInspectionOutcome ready(ApkManifestInfo manifest,
                                             ApkRevisionPolicy.ActivityChoice activities,
                                             String sha256,
                                             String apkBlobRef) {
        return new ApkInspectionOutcome(Status.READY, manifest,
                activities.launchActivity(), activities.waitActivity(),
                activities.launcherCandidates(), sha256, apkBlobRef);
    }

    public static ApkInspectionOutcome selectionRequired(ApkManifestInfo manifest,
                                                         List<String> candidates,
                                                         String sha256,
                                                         String apkBlobRef) {
        return new ApkInspectionOutcome(Status.ACTIVITY_SELECTION_REQUIRED, manifest,
                null, null, candidates, sha256, apkBlobRef);
    }

    public Status getStatus() { return status; }
    public ApkManifestInfo getManifest() { return manifest; }
    public String getLaunchActivity() { return launchActivity; }
    public String getWaitActivity() { return waitActivity; }
    public List<String> getLauncherCandidates() { return launcherCandidates; }
    public String getSha256() { return sha256; }
    public String getApkBlobRef() { return apkBlobRef; }
}
