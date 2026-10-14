package keel.contract.runner;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/** Runner API v2 wire records. Unknown fields are ignored on both sides (forward compatible). */
public final class RunnerDtos {
    private RunnerDtos() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ClaimRequest(
            List<RunnerCapability> capabilities,
            List<WireJobKind> kinds,
            Integer protocolVersion,
            String workerToolchainVersion,
            List<String> authorizedDeviceIds
    ) {
        public ClaimRequest(List<RunnerCapability> capabilities, List<WireJobKind> kinds) {
            this(capabilities, kinds, null, null, null);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MobileClaimExtras(
            String platform,
            String inputManifestHash,
            List<RunnerCapability> requiredCapabilities,
            String deviceId,
            Long leaseFence,
            String appRevisionId,
            String profileRevisionId,
            String secretEnvelopeRef
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ClaimResponse(String jobId, String attemptId, long cancelGeneration, long fencingToken,
                                String projectId, WireJobKind jobKind, String libraryRevisionId,
                                String environmentRevisionId, String inputSnapshotHash,
                                String providerAllowlistSnapshot, String leaseUntil,
                                MobileClaimExtras mobile) {
        /** Web / pre-mobile callers. */
        public ClaimResponse(String jobId, String attemptId, long cancelGeneration, long fencingToken,
                             String projectId, WireJobKind jobKind, String libraryRevisionId,
                             String environmentRevisionId, String inputSnapshotHash,
                             String providerAllowlistSnapshot, String leaseUntil) {
            this(jobId, attemptId, cancelGeneration, fencingToken, projectId, jobKind, libraryRevisionId,
                    environmentRevisionId, inputSnapshotHash, providerAllowlistSnapshot, leaseUntil, null);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LeaseResponse(String jobId, boolean cancelRequested, long cancelGeneration, String leaseUntil) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StageRequest(WireStage stage) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ProgressRequest(String message, Integer done, Integer total) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ArtifactChunkResponse(String jobId, String artifactId, int index, boolean stored, boolean complete) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ResultRequest(String status, Integer passedCount, Integer todoCount, String message,
                                String inputSnapshotHash, String providerAllowlistSnapshot) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ErrorBody(String code, String message) {
    }
}
