package delivery.job;

import delivery.portal.model.JobRecord;

import java.nio.file.Path;
import java.util.UUID;

/** Copy frozen pins onto a new job id so reruns do not overwrite prior evidence. */
public final class RerunSupport {
    private RerunSupport() {
    }

    public static JobRecord newAttempt(JobRecord source) {
        return newAttempt(source, source == null ? null : source.getExcelPath());
    }

    public static JobRecord newAttempt(JobRecord source, Path excelPath) {
        if (source == null) {
            throw new IllegalArgumentException("source job is required");
        }
        Path excel = excelPath != null ? excelPath : source.getExcelPath();
        String newId = "job_rerun_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        JobRecord copy = new JobRecord(
                newId,
                source.getProjectId(),
                source.getOwnerUserId(),
                source.getMode(),
                excel,
                source.getBaseUrl(),
                source.getUsername(),
                source.getPassword(),
                source.isFinalRevise(),
                source.getJobKind());
        copy.setAuthoringEngine(source.getAuthoringEngine());
        copy.setTenantId(source.getTenantId());
        copy.setLibraryRevisionId(source.getLibraryRevisionId());
        copy.setProviderAllowlistSnapshot(source.getProviderAllowlistSnapshot());
        copy.setEnvironmentRevisionId(source.getEnvironmentRevisionId());
        copy.setPrecisionMaxSnapshot(source.getPrecisionMaxSnapshot());
        copy.setParentJobId(source.getJobId());
        copy.setInputSnapshotHash(hashPinnedInputs(source));
        copy.setStatus(JobRecord.Status.QUEUED);
        return copy;
    }

    public static String hashPinnedInputs(JobRecord job) {
        if (job == null) {
            return "";
        }
        String raw = String.join("|",
                nz(job.getProjectId()),
                nz(job.getTenantId()),
                nz(job.getMode()),
                job.getJobKind() == null ? "" : job.getJobKind().name(),
                nz(job.getBaseUrl()),
                job.getAuthoringEngine() == null ? "" : job.getAuthoringEngine().name(),
                nz(job.getProviderAllowlistSnapshot()),
                nz(job.getLibraryRevisionId()),
                nz(job.getEnvironmentRevisionId()));
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            return Integer.toHexString(raw.hashCode());
        }
    }

    private static String nz(String v) {
        return v == null ? "" : v;
    }
}
