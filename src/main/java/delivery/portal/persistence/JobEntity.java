package delivery.portal.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "jobs")
public class JobEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String jobId;

    @Column(nullable = false)
    private String projectId;

    @Column(nullable = false)
    private Long ownerUserId;

    @Column(nullable = false)
    private String mode;

    @Column(nullable = false)
    private String status;

    private int passedCount;
    private int todoCount;

    @Column(nullable = false, columnDefinition = "integer default 0")
    private int progressCurrent;

    @Column(nullable = false, columnDefinition = "integer default 0")
    private int progressTotal;

    @Column(length = 1024)
    private String message;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    private Instant completedAt;

    @Column(length = 1024)
    private String zipPath;

    @Column(length = 2048)
    private String baseUrl;

    @Column(length = 2048)
    private String excelPath;

    @Column(length = 2048)
    private String usernameCipher;

    @Column(length = 2048)
    private String passwordCipher;

    @Column(length = 16, columnDefinition = "varchar(16) default 'CONVERT'")
    private String jobKind = "CONVERT";

    @Column(length = 128)
    private String generateModel;

    public Long getId() { return id; }
    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }
    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }
    public Long getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getPassedCount() { return passedCount; }
    public void setPassedCount(int passedCount) { this.passedCount = passedCount; }
    public int getTodoCount() { return todoCount; }
    public void setTodoCount(int todoCount) { this.todoCount = todoCount; }
    public int getProgressCurrent() { return progressCurrent; }
    public void setProgressCurrent(int progressCurrent) { this.progressCurrent = progressCurrent; }
    public int getProgressTotal() { return progressTotal; }
    public void setProgressTotal(int progressTotal) { this.progressTotal = progressTotal; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public String getZipPath() { return zipPath; }
    public void setZipPath(String zipPath) { this.zipPath = zipPath; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getExcelPath() { return excelPath; }
    public void setExcelPath(String excelPath) { this.excelPath = excelPath; }
    public String getUsernameCipher() { return usernameCipher; }
    public void setUsernameCipher(String usernameCipher) { this.usernameCipher = usernameCipher; }
    public String getPasswordCipher() { return passwordCipher; }
    public void setPasswordCipher(String passwordCipher) { this.passwordCipher = passwordCipher; }
    public String getJobKind() { return jobKind; }
    public void setJobKind(String jobKind) { this.jobKind = jobKind; }
    public String getGenerateModel() { return generateModel; }
    public void setGenerateModel(String generateModel) { this.generateModel = generateModel; }
}
