package delivery.portal.model;

import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class JobRecord {
    public enum Status { QUEUED, RUNNING, COMPLETED, COMPLETED_WITH_BLOCK, FAILED, CANCELLED }
    public enum JobKind { CONVERT, EXECUTE, GENERATE_BATCH, GENERATE_COMPARE, HUNT }

    private final String jobId;
    private final String projectId;
    private final Long ownerUserId;
    private final String mode;
    private final Path excelPath;
    private final String baseUrl;
    private final String username;
    private final String password;
    private final boolean finalRevise;
    private final JobKind jobKind;
    private String generateModel;
    private volatile Instant createdAt;
    private volatile Instant completedAt;
    private final AtomicReference<Status> status = new AtomicReference<>(Status.QUEUED);
    private final AtomicInteger passedCount = new AtomicInteger(0);
    private final AtomicInteger todoCount = new AtomicInteger(0);
    private final AtomicInteger progressCurrent = new AtomicInteger(0);
    private final AtomicInteger progressTotal = new AtomicInteger(0);
    private final AtomicReference<String> message = new AtomicReference<>("");
    private final AtomicReference<Path> zipPath = new AtomicReference<>();
    private final AtomicReference<String> error = new AtomicReference<>();

    public JobRecord(String jobId, String projectId, Long ownerUserId, String mode, Path excelPath,
                     String baseUrl, String username, String password) {
        this(jobId, projectId, ownerUserId, mode, excelPath, baseUrl, username, password, false);
    }

    public JobRecord(String jobId, String projectId, Long ownerUserId, String mode, Path excelPath,
                     String baseUrl, String username, String password, boolean finalRevise) {
        this(jobId, projectId, ownerUserId, mode, excelPath, baseUrl, username, password, finalRevise, JobKind.CONVERT);
    }

    public JobRecord(String jobId, String projectId, Long ownerUserId, String mode, Path excelPath,
                     String baseUrl, String username, String password, boolean finalRevise, JobKind jobKind) {
        this.jobId = jobId;
        this.projectId = projectId;
        this.ownerUserId = ownerUserId;
        this.mode = mode;
        this.excelPath = excelPath;
        this.baseUrl = baseUrl;
        this.username = username;
        this.password = password;
        this.finalRevise = finalRevise;
        this.jobKind = jobKind == null ? JobKind.CONVERT : jobKind;
    }

    public String getJobId() { return jobId; }
    public String getProjectId() { return projectId; }
    public Long getOwnerUserId() { return ownerUserId; }
    public String getMode() { return mode; }
    public Path getExcelPath() { return excelPath; }
    public String getBaseUrl() { return baseUrl; }
    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public boolean isFinalRevise() { return finalRevise; }
    public JobKind getJobKind() { return jobKind; }
    public String getGenerateModel() { return generateModel; }
    public void setGenerateModel(String generateModel) { this.generateModel = generateModel; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }

    /** Prefer completion time; fall back to when this job was queued. */
    public Instant runAt() {
        return completedAt != null ? completedAt : createdAt;
    }

    public static String workingForLabel(Instant createdAt, Instant now) {
        Instant origin = createdAt != null ? createdAt : now;
        if (origin == null || now == null) {
            return "Working for 0:00";
        }
        long sec = Math.max(0, java.time.Duration.between(origin, now).getSeconds());
        return "Working for " + (sec / 60) + ":" + String.format(java.util.Locale.ROOT, "%02d", sec % 60);
    }

    public Status getStatus() { return status.get(); }
    public void setStatus(Status s) { status.set(s); }
    public int getPassedCount() { return passedCount.get(); }
    public void setPassedCount(int v) { passedCount.set(v); }
    public int getTodoCount() { return todoCount.get(); }
    public void setTodoCount(int v) { todoCount.set(v); }
    public int getProgressCurrent() { return progressCurrent.get(); }
    public void setProgressCurrent(int v) { progressCurrent.set(v); }
    public int getProgressTotal() { return progressTotal.get(); }
    public void setProgressTotal(int v) { progressTotal.set(v); }
    public String getMessage() { return message.get(); }
    public void setMessage(String m) { message.set(m == null ? "" : m); }
    public Path getZipPath() { return zipPath.get(); }
    public void setZipPath(Path p) { zipPath.set(p); }
    public String getError() { return error.get(); }
    public void setError(String e) { error.set(e); }

    public static boolean isDownloadable(JobKind kind, Status s) {
        if (kind == JobKind.EXECUTE) {
            return false;
        }
        if (kind == JobKind.GENERATE_BATCH) {
            return s == Status.COMPLETED;
        }
        if (kind == JobKind.GENERATE_COMPARE) {
            return false;
        }
        if (kind == JobKind.HUNT) {
            return s == Status.COMPLETED || s == Status.COMPLETED_WITH_BLOCK || s == Status.FAILED;
        }
        return s == Status.COMPLETED || s == Status.COMPLETED_WITH_BLOCK
                || s == Status.FAILED;
    }

    public static boolean isDownloadable(JobKind kind, String status) {
        return isDownloadable(kind, parseStatus(status));
    }

    private static Status parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return Status.FAILED;
        }
        try {
            return Status.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return Status.FAILED;
        }
    }

    public static boolean isDownloadable(Status s) {
        return isDownloadable(JobKind.CONVERT, s);
    }

    public static boolean isDownloadable(String status) {
        return isDownloadable(JobKind.CONVERT, status);
    }

    public static JobKind parseJobKind(String raw) {
        if (raw == null || raw.isBlank()) {
            return JobKind.CONVERT;
        }
        try {
            return JobKind.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return JobKind.CONVERT;
        }
    }
}
