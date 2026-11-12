package delivery.portal.worker;

import delivery.agent.harness.convert.AgentHarnessJobBridge;
import delivery.identity.TenantId;
import delivery.job.ArtifactIntegrityException;
import delivery.job.PublicationGuard;
import delivery.portal.AgentHarnessProperties;
import delivery.portal.jobs.DurableJobClaim;
import delivery.portal.service.PortalStore;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.BooleanSupplier;

/** Convert guard: advisory validation before lock; publish fence unchanged. */
public final class AgentAwarePublicationGuard implements PublicationGuard {
    private final PortalStore portalStore;
    private final String jobId;
    private final DurableJobClaim.Lease lease;
    private final AgentHarnessProperties harnessProperties;
    private final AgentHarnessJobBridge bridge;
    private final Path projectRoot;
    private final String projectId;
    private final BooleanSupplier cancelCheck;
    private final Duration harnessBudget;
    private volatile String expectedFingerprint = "";

    public AgentAwarePublicationGuard(
            PortalStore portalStore,
            String jobId,
            DurableJobClaim.Lease lease,
            AgentHarnessProperties harnessProperties,
            AgentHarnessJobBridge bridge,
            Path projectRoot,
            String projectId,
            BooleanSupplier cancelCheck,
            Duration harnessBudget) {
        this.portalStore = portalStore;
        this.jobId = jobId;
        this.lease = lease;
        this.harnessProperties = harnessProperties;
        this.bridge = bridge;
        this.projectRoot = projectRoot;
        this.projectId = projectId;
        this.cancelCheck = cancelCheck;
        this.harnessBudget = harnessBudget == null ? Duration.ofMinutes(15) : harnessBudget;
    }

    @Override
    public void beforeLockedPublish(Path frameworkDir, Path zipFile) throws Exception {
        if (!harnessProperties.isEnabled() || !harnessProperties.isFrameworkValidationEnabled()) {
            expectedFingerprint = "";
            return;
        }
        expectedFingerprint = bridge.validateBeforePublish(
                projectRoot, projectId, jobId, zipFile, harnessBudget, cancelCheck);
    }

    @Override
    public Optional<String> expectedZipFingerprint() {
        if (expectedFingerprint == null || expectedFingerprint.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(expectedFingerprint);
    }

    @Override
    public <T> T publish(Callable<T> action) throws Exception {
        return portalStore.publishAttempt(jobId, lease, action);
    }
}
