package delivery.mobile.transport;

import keel.contract.runner.RunnerCapability;
import keel.contract.runner.RunnerDtos;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Server-side frozen mobile claim context for one attempt. */
public final class MobileClaimSnapshot {
    private final String tenantId;
    private final String jobId;
    private final String attemptId;
    private final String projectId;
    private final String deviceId;
    private final long leaseFence;
    private final String appRevisionId;
    private final String profileRevisionId;
    private final String libraryRevisionId;
    private final String apkSha256;
    private final List<String> selectedTcIds;
    private final String inputManifestHash;
    private final String secretEnvelopeRef;
    private final MobileAttemptBinding binding;

    public MobileClaimSnapshot(String tenantId, String jobId, String attemptId, String projectId,
                               String deviceId, long leaseFence, String appRevisionId, String profileRevisionId,
                               String libraryRevisionId, String apkSha256, List<String> selectedTcIds,
                               String inputManifestHash, String secretEnvelopeRef, MobileAttemptBinding binding) {
        this.tenantId = tenantId == null ? "" : tenantId;
        this.jobId = jobId;
        this.attemptId = attemptId;
        this.projectId = projectId == null ? "" : projectId;
        this.deviceId = deviceId;
        this.leaseFence = leaseFence;
        this.appRevisionId = appRevisionId == null ? "" : appRevisionId;
        this.profileRevisionId = profileRevisionId == null ? "" : profileRevisionId;
        this.libraryRevisionId = libraryRevisionId == null ? "" : libraryRevisionId;
        this.apkSha256 = apkSha256 == null ? "" : apkSha256;
        this.selectedTcIds = selectedTcIds == null ? List.of() : List.copyOf(selectedTcIds);
        this.inputManifestHash = inputManifestHash;
        this.secretEnvelopeRef = secretEnvelopeRef == null ? "" : secretEnvelopeRef;
        this.binding = binding;
    }

    public String tenantId() { return tenantId; }
    public String jobId() { return jobId; }
    public String attemptId() { return attemptId; }
    public String projectId() { return projectId; }
    public String deviceId() { return deviceId; }
    public long leaseFence() { return leaseFence; }
    public String inputManifestHash() { return inputManifestHash; }
    public String secretEnvelopeRef() { return secretEnvelopeRef; }
    public MobileAttemptBinding binding() { return binding; }

    public RunnerDtos.MobileClaimExtras toExtras() {
        Set<RunnerCapability> required = EnumSet.of(RunnerCapability.ANDROID_UIAUTOMATOR2);
        return new RunnerDtos.MobileClaimExtras(
                "ANDROID",
                inputManifestHash,
                List.copyOf(required),
                deviceId,
                leaseFence,
                appRevisionId,
                profileRevisionId,
                secretEnvelopeRef
        );
    }
}
