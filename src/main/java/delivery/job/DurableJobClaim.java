package delivery.job;

import delivery.portal.model.JobRecord;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * Durable claim / lease / publish-fence rules for portal jobs.
 * Browser-stage expiry is marked uncertain instead of silent replay.
 */
public final class DurableJobClaim {
    public static final Duration DEFAULT_LEASE = Duration.ofSeconds(45);
    public static final String INTERRUPTED_UNCERTAIN = "INTERRUPTED_UNCERTAIN";

    public enum Stage {
        ADMITTED, BROWSER, EMIT, PUBLISH
    }

    public record Lease(String jobId, String attemptId, String workerId, Instant leaseUntil, int cancelGeneration,
                        Stage stage) {
    }

    private DurableJobClaim() {
    }

    public static String newWorkerId() {
        return "wkr_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    public static String newAttemptId() {
        return "att_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    public static Optional<Lease> tryClaim(JobRecord job, String workerId, Instant now, Duration ttl) {
        if (job == null || workerId == null || workerId.isBlank() || now == null) {
            return Optional.empty();
        }
        Duration leaseTtl = ttl == null || ttl.isZero() || ttl.isNegative() ? DEFAULT_LEASE : ttl;
        JobRecord.Status status = job.getStatus();
        if (status == JobRecord.Status.COMPLETED
                || status == JobRecord.Status.COMPLETED_WITH_BLOCK
                || status == JobRecord.Status.FAILED
                || status == JobRecord.Status.CANCELLED
                || status == JobRecord.Status.CANCELLING) {
            return Optional.empty();
        }
        if (status == JobRecord.Status.RUNNING) {
            if (!leaseExpired(job, now)) {
                return Optional.empty();
            }
            if (stageOf(job) == Stage.BROWSER) {
                markUncertain(job, now);
                return Optional.empty();
            }
        }
        String attempt = newAttemptId();
        job.setStatus(JobRecord.Status.RUNNING);
        job.setAttemptId(attempt);
        job.setWorkerId(workerId);
        job.setLeaseUntil(now.plus(leaseTtl));
        if (job.getClaimStage() == null || job.getClaimStage().isBlank()
                || status == JobRecord.Status.QUEUED) {
            job.setClaimStage(Stage.ADMITTED.name());
        }
        return Optional.of(toLease(job));
    }

    public static boolean sameAttempt(JobRecord job, Lease lease) {
        return job != null && lease != null && lease.attemptId() != null
                && lease.attemptId().equals(job.getAttemptId());
    }

    public static boolean heartbeat(JobRecord job, Lease lease, Instant now, Duration ttl) {
        if (lease == null) {
            return false;
        }
        return heartbeat(job, lease.attemptId(), lease.cancelGeneration(), now, ttl);
    }

    public static boolean heartbeat(
            JobRecord job, String attemptId, int expectedGeneration, Instant now, Duration ttl) {
        if (job == null || attemptId == null || now == null) {
            return false;
        }
        if (!attemptId.equals(job.getAttemptId())) {
            return false;
        }
        if (job.getStatus() != JobRecord.Status.RUNNING) {
            return false;
        }
        if (job.getCancelGeneration() != expectedGeneration) {
            return false;
        }
        Duration leaseTtl = ttl == null || ttl.isZero() || ttl.isNegative() ? DEFAULT_LEASE : ttl;
        job.setLeaseUntil(now.plus(leaseTtl));
        return true;
    }

    public static boolean mayPublish(JobRecord job, Lease lease) {
        if (lease == null) {
            return false;
        }
        return mayPublish(job, lease.attemptId(), lease.cancelGeneration());
    }

    public static boolean mayPublish(JobRecord job, String attemptId) {
        if (job == null) {
            return false;
        }
        return mayPublish(job, attemptId, job.getCancelGeneration());
    }

    public static boolean mayPublish(JobRecord job, String attemptId, int expectedGeneration) {
        if (job == null || attemptId == null || attemptId.isBlank()) {
            return false;
        }
        if (!attemptId.equals(job.getAttemptId())) {
            return false;
        }
        if (job.getCancelGeneration() != expectedGeneration) {
            return false;
        }
        JobRecord.Status status = job.getStatus();
        if (status == JobRecord.Status.CANCELLED || status == JobRecord.Status.CANCELLING
                || status == JobRecord.Status.COMPLETED
                || status == JobRecord.Status.COMPLETED_WITH_BLOCK
                || status == JobRecord.Status.FAILED) {
            return false;
        }
        return true;
    }

    public static void markStage(JobRecord job, String attemptId, Stage stage) {
        if (job == null || stage == null || attemptId == null || !attemptId.equals(job.getAttemptId())) {
            return;
        }
        job.setClaimStage(stage.name());
    }

    public static void complete(JobRecord job, String attemptId) {
        if (!mayPublish(job, attemptId)) {
            return;
        }
        job.setClaimStage(Stage.PUBLISH.name());
        job.setLeaseUntil(null);
    }

    public static void requestCancel(JobRecord job) {
        if (job == null) {
            return;
        }
        job.setCancelGeneration(job.getCancelGeneration() + 1);
    }

    /**
     * @return true when the job record was mutated (uncertain terminal or reclaimable expiry note)
     */
    public static boolean reconcileExpired(JobRecord job, Instant now) {
        if (job == null || now == null) {
            return false;
        }
        if (job.getStatus() != JobRecord.Status.RUNNING) {
            return false;
        }
        if (!leaseExpired(job, now)) {
            return false;
        }
        if (stageOf(job) == Stage.BROWSER) {
            markUncertain(job, now);
            return true;
        }
        job.setStatus(JobRecord.Status.QUEUED);
        job.setAttemptId("");
        job.setWorkerId("");
        job.setLeaseUntil(null);
        job.setMessage("Lease expired; queued for recovery");
        return true;
    }

    public static String hashInputs(JobRecord job) {
        if (job == null) {
            return "";
        }
        String raw = String.join("|",
                nullToEmpty(job.getJobId()),
                nullToEmpty(job.getProjectId()),
                nullToEmpty(job.getTenantId()),
                nullToEmpty(job.getMode()),
                job.getJobKind() == null ? "" : job.getJobKind().name(),
                nullToEmpty(job.getBaseUrl()),
                job.getExcelPath() == null ? "" : job.getExcelPath().toAbsolutePath().toString(),
                job.getAuthoringEngine() == null ? "" : job.getAuthoringEngine().name(),
                nullToEmpty(job.getProviderAllowlistSnapshot()));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            return Integer.toHexString(raw.hashCode());
        }
    }

    public static boolean leaseExpired(JobRecord job, Instant now) {
        Instant until = job.getLeaseUntil();
        if (until == null) {
            return true;
        }
        return !until.isAfter(now);
    }

    private static void markUncertain(JobRecord job, Instant now) {
        job.setStatus(JobRecord.Status.FAILED);
        job.setError(INTERRUPTED_UNCERTAIN);
        job.setMessage("Interrupted during browser work; review before replay");
        job.setCompletedAt(now);
        job.setLeaseUntil(null);
    }

    private static Stage stageOf(JobRecord job) {
        try {
            return Stage.valueOf(job.getClaimStage() == null || job.getClaimStage().isBlank()
                    ? Stage.ADMITTED.name()
                    : job.getClaimStage().trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return Stage.ADMITTED;
        }
    }

    private static Lease toLease(JobRecord job) {
        return new Lease(
                job.getJobId(),
                job.getAttemptId(),
                job.getWorkerId(),
                job.getLeaseUntil(),
                job.getCancelGeneration(),
                stageOf(job));
    }

    private static String nullToEmpty(String v) {
        return v == null ? "" : v;
    }
}
