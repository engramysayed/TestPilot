package delivery.runner;

import delivery.job.DurableJobClaim;
import delivery.portal.model.JobRecord;

import java.time.Duration;
import java.time.Instant;

/**
 * Customer-operated runner contract. Implementation may progress; deployment readiness
 * stays dependent on P2-03 isolation validation. The control plane never needs inbound
 * access to the customer network.
 */
public final class PrivateRunnerRules {
    public static final Duration DEFAULT_HEARTBEAT_GRACE = Duration.ofSeconds(45);

    public record Enrollment(
            String runnerId,
            String tenantId,
            Instant enrolledAt,
            Instant revokedAt,
            Instant lastHeartbeat
    ) {
    }

    public record ScopedClaim(String runnerId, String tenantId, String jobId, String attemptId) {
    }

    public record EvidenceEgress(boolean artifacts, boolean screenshots, boolean networkBodies, boolean secrets) {
        public static EvidenceEgress defaults() {
            return new EvidenceEgress(true, true, false, false);
        }
    }

    private PrivateRunnerRules() {
    }

    public static boolean canClaim(Enrollment enrollment, String tenantId, Instant now, Duration grace) {
        if (enrollment == null || tenantId == null || now == null) {
            return false;
        }
        if (enrollment.revokedAt() != null) {
            return false;
        }
        if (!tenantId.equals(enrollment.tenantId())) {
            return false;
        }
        Duration window = grace == null ? DEFAULT_HEARTBEAT_GRACE : grace;
        Instant beat = enrollment.lastHeartbeat();
        if (beat == null) {
            return false;
        }
        return !beat.plus(window).isBefore(now);
    }

    public static ScopedClaim claim(Enrollment enrollment, JobRecord job, Instant now) {
        if (!canClaim(enrollment, job == null ? null : job.getTenantId(), now, DEFAULT_HEARTBEAT_GRACE)) {
            throw new SecurityException("runner cannot claim work");
        }
        var lease = DurableJobClaim.tryClaim(job, enrollment.runnerId(), now, DurableJobClaim.DEFAULT_LEASE)
                .orElseThrow(() -> new IllegalStateException("job is not claimable"));
        return new ScopedClaim(enrollment.runnerId(), enrollment.tenantId(), job.getJobId(), lease.attemptId());
    }

    /**
     * Offline runners follow the durable job recovery contract: expired BROWSER leases
     * are uncertain; other stages re-queue.
     */
    public static boolean recoverOffline(JobRecord job, Instant now) {
        return DurableJobClaim.reconcileExpired(job, now);
    }
}
