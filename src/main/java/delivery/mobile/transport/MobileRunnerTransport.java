package delivery.mobile.transport;

import delivery.mobile.MobileProperties;
import delivery.mobile.model.ProjectPlatform;
import keel.contract.runner.RunnerCapability;
import keel.contract.runner.RunnerDtos;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Portal-side mobile runner transport facade (no Appium). Capability negotiation, claim extras,
 * input manifest, ZIP/HMAC limits, secret channel, attempt/device fencing, token redaction.
 */
@Service
public class MobileRunnerTransport {
    private final MobileProperties properties;
    private final MobileClaimRegistry registry;
    private final MobileSecretChannel secretChannel = new MobileSecretChannel();

    public MobileRunnerTransport(MobileProperties properties, MobileClaimRegistry registry) {
        this.properties = properties;
        this.registry = registry;
    }

    public boolean mobileEnabled() {
        return properties.isEnabled();
    }

    public boolean canClaim(Collection<RunnerCapability> advertised, ProjectPlatform platform) {
        return MobileClaimGate.canClaim(advertised, platform, properties.isEnabled());
    }

    public MobileClaimSnapshot registerMobileClaim(
            String tenantId,
            String jobId,
            String attemptId,
            String projectId,
            String deviceId,
            long leaseFence,
            String appRevisionId,
            String profileRevisionId,
            String libraryRevisionId,
            String apkSha256,
            List<String> selectedTcIds,
            Map<String, String> secrets,
            String runnerToken,
            Instant now
    ) {
        String envelopeRef = "";
        if (secrets != null && !secrets.isEmpty()) {
            MobileSecretChannel.Envelope env = secretChannel.seal(
                    jobId, attemptId, secrets, now,
                    Duration.ofSeconds(Math.max(1, properties.getSecretEnvelopeTtlSeconds())),
                    runnerToken);
            envelopeRef = env.envelopeId();
        }
        MobileInputManifest manifest = new MobileInputManifest(
                "1", "ANDROID", jobId, attemptId, projectId, libraryRevisionId, appRevisionId,
                profileRevisionId, apkSha256, selectedTcIds, envelopeRef, deviceId, leaseFence);
        manifest.assertNoEmbeddedSecrets();
        MobileAttemptBinding binding = MobileAttemptBinding.fromManifest(manifest);
        MobileClaimSnapshot snap = new MobileClaimSnapshot(
                tenantId, jobId, attemptId, projectId, deviceId, leaseFence, appRevisionId, profileRevisionId,
                libraryRevisionId, apkSha256, selectedTcIds, manifest.hash(), envelopeRef, binding);
        registry.put(snap);
        return snap;
    }

    public RunnerDtos.MobileClaimExtras extrasFor(String jobId) {
        return registry.get(jobId).map(MobileClaimSnapshot::toExtras).orElse(null);
    }

    public void assertBinding(String tenantId, String jobId, String attemptId,
                              String deviceId, Long leaseFence, String inputHash) {
        MobileClaimSnapshot snap = registry.requireSameTenant(jobId, tenantId);
        snap.binding().assertMatches(jobId, attemptId, deviceId, leaseFence, inputHash);
    }

    public Map<String, String> openSecrets(String tenantId, String jobId, String attemptId,
                                           String runnerToken, Instant now) {
        MobileClaimSnapshot snap = registry.requireSameTenant(jobId, tenantId);
        if (!snap.attemptId().equals(attemptId)) {
            throw new MobileTransportException(MobileTransportErrorCode.STALE_ATTEMPT, "stale attempt token");
        }
        if (snap.secretEnvelopeRef() == null || snap.secretEnvelopeRef().isBlank()) {
            throw new MobileTransportException(MobileTransportErrorCode.SECRET_REQUIRED, "no secret envelope");
        }
        return secretChannel.open(snap.secretEnvelopeRef(), jobId, attemptId, now, runnerToken);
    }

    public void validateInputZip(byte[] zipBytes) {
        MobileInputZipValidator.validate(
                zipBytes,
                properties.getRunnerInputZipMaxEntries(),
                properties.getRunnerInputZipMaxUncompressedBytes(),
                properties.getRunnerInputZipMaxBytes());
    }

    public boolean verifyArtifactHmac(String token, String jobId, String attemptId,
                                      byte[] body, String signatureHeader) {
        return MobileHmacLimits.verifyArtifact(token, jobId, attemptId, body, signatureHeader,
                properties.getRunnerHmacMaxBodyBytes());
    }

    public String redact(String text) {
        return RunnerTransportRedactor.redact(text);
    }

    public MobileSecretChannel secretChannel() {
        return secretChannel;
    }

    public MobileClaimRegistry registry() {
        return registry;
    }

    public void complete(String jobId) {
        registry.get(jobId).ifPresent(snap -> {
            if (snap.secretEnvelopeRef() != null && !snap.secretEnvelopeRef().isBlank()) {
                secretChannel.erase(snap.secretEnvelopeRef());
            }
            registry.remove(jobId);
        });
    }
}
