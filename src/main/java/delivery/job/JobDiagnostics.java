package delivery.job;

import delivery.ir.TcDraftStatus;
import delivery.portal.model.JobRecord;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** User-facing result integrity and operator next-actions for one job. */
public final class JobDiagnostics {
    private JobDiagnostics() {
    }

    public static Map<String, Object> describe(JobRecord job, boolean artifactPresent, boolean artifactExpired) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (job == null) {
            out.put("status", "UNKNOWN");
            out.put("proofKind", "UNCHECKED");
            out.put("nextAction", "Job was not found.");
            out.put("downloadAvailable", Boolean.FALSE);
            return out;
        }
        JobRecord.Status status = job.getStatus();
        String proof = proofKind(job);
        boolean downloadable = JobRecord.isDownloadable(job.getJobKind(), status)
                && artifactPresent
                && !artifactExpired;
        out.put("jobId", job.getJobId());
        out.put("status", status.name());
        out.put("proofKind", proof);
        out.put("proofSource", ResultIntegrity.proofSource(proof, false));
        out.put("libraryRevisionId", job.getLibraryRevisionId());
        out.put("environmentRevisionId", job.getEnvironmentRevisionId());
        out.put("parentJobId", job.getParentJobId());
        out.put("failureClass", delivery.job.FailureClassifier.suggest(
                (job.getMessage() == null ? "" : job.getMessage()) + " " + (job.getError() == null ? "" : job.getError())
        ).name());
        out.put("providerAllowlist", job.getProviderAllowlistSnapshot());
        ProviderUsage.Snapshot usage = ProviderUsage.from(job, java.util.List.of());
        out.put("providersUsed", usage.used());
        out.put("fallbackUsed", usage.fallbackUsed());
        out.put("fallbackFrom", usage.fallbackFrom());
        out.put("fallbackTo", usage.fallbackTo());
        out.put("fallbackReason", usage.fallbackReason());
        out.put("claimStage", job.getClaimStage());
        out.put("attemptId", job.getAttemptId());
        out.put("downloadAvailable", downloadable);
        out.put("artifactExpired", artifactExpired);
        out.put("simulated", "SIMULATED".equals(proof));
        out.put("nextAction", nextAction(job, downloadable, artifactExpired));
        return out;
    }

    public static String caseProofKind(TcDraftStatus status, boolean simulated) {
        if (simulated) {
            return "SIMULATED";
        }
        if (status == null) {
            return "UNCHECKED";
        }
        return switch (status) {
            case PASSED -> "FRESH";
            case REUSED -> "REUSED";
            case PARTIAL -> "BLOCKED";
            case TODO -> "UNCHECKED";
        };
    }

    static String proofKind(JobRecord job) {
        if (DurableJobClaim.INTERRUPTED_UNCERTAIN.equals(job.getError())) {
            return "INTERRUPTED";
        }
        String message = job.getMessage() == null ? "" : job.getMessage().toLowerCase(Locale.ROOT);
        if (message.contains("dry-run")) {
            return "SIMULATED";
        }
        if (job.getStatus() == JobRecord.Status.COMPLETED_WITH_BLOCK) {
            return "BLOCKED";
        }
        if (job.getStatus() == JobRecord.Status.COMPLETED
                && job.getPassedCount() <= 0
                && job.getTodoCount() > 0) {
            return "UNCHECKED";
        }
        if (message.contains("reused")) {
            return "REUSED";
        }
        if (job.getStatus() == JobRecord.Status.COMPLETED) {
            return "FRESH";
        }
        return "UNCHECKED";
    }

    static String nextAction(JobRecord job, boolean downloadable, boolean artifactExpired) {
        JobRecord.Status status = job.getStatus();
        if (DurableJobClaim.INTERRUPTED_UNCERTAIN.equals(job.getError())) {
            return "Review before replay; browser actions may already have run.";
        }
        if (status == JobRecord.Status.QUEUED) {
            return "Wait; the job has not started. Cancel if it was submitted in error.";
        }
        if (status == JobRecord.Status.CANCELLING) {
            return "Cancellation requested; wait for worker acknowledgement.";
        }
        if (status == JobRecord.Status.RUNNING) {
            return "Job is running. Force-stop only if it is stuck past the deadline.";
        }
        if (status == JobRecord.Status.CANCELLED) {
            return "Job was cancelled. Resubmit with the same library revision if you still need results.";
        }
        if (artifactExpired || (!downloadable && JobRecord.isDownloadable(job.getJobKind(), status))) {
            return "Artifact expired or missing; rerun to produce a new package.";
        }
        if ("SIMULATED".equals(proofKind(job))) {
            return "This was a dry-run simulation, not live browser proof.";
        }
        if (downloadable) {
            return "Download remains bound to this job's artifact.";
        }
        return "See the message for why this job stopped.";
    }
}
