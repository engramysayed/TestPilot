package delivery.mobile.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(name = "mobile_app_revisions",
        uniqueConstraints = @UniqueConstraint(name = "uk_mobile_app_revisions_rev", columnNames = "revision_id"),
        indexes = {
                @Index(name = "idx_mobile_app_revisions_app", columnList = "app_id"),
                @Index(name = "idx_mobile_app_revisions_tenant_project", columnList = "tenant_id,project_id")
        })
public class MobileAppRevisionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "revision_id", nullable = false, length = 64)
    private String revisionId;

    @Column(name = "app_id", nullable = false, length = 64)
    private String appId;

    @Column(name = "tenant_id", nullable = false, length = 40)
    private String tenantId;

    @Column(name = "project_id", nullable = false, length = 64)
    private String projectId;

    @Column(nullable = false, length = 32)
    private String source;

    @Column(name = "package_name", length = 256)
    private String packageName;

    @Column(name = "version_code")
    private Long versionCode;

    @Column(name = "version_name", length = 128)
    private String versionName;

    @Column(name = "min_sdk")
    private Integer minSdk;

    /** Comma-separated ABI list for persistence; API exposes string[]. */
    @Column(name = "abi_list", length = 512)
    private String abiList;

    @Column(name = "launch_activity", length = 512)
    private String launchActivity;

    @Column(name = "wait_activity", length = 512)
    private String waitActivity;

    @Column(name = "apk_blob_ref", length = 512)
    private String apkBlobRef;

    @Column(length = 64)
    private String sha256;

    @Column(name = "certificate_digest", length = 128)
    private String certificateDigest;

    /** UPLOADED | OBSERVED */
    @Column(nullable = false, length = 32)
    private String verification = "UPLOADED";

    @Column(name = "observed_metadata_json", length = 4000)
    private String observedMetadataJson;

    @Column(name = "created_by", length = 64)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public String getRevisionId() { return revisionId; }
    public void setRevisionId(String revisionId) { this.revisionId = revisionId; }
    public String getAppId() { return appId; }
    public void setAppId(String appId) { this.appId = appId; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getPackageName() { return packageName; }
    public void setPackageName(String packageName) { this.packageName = packageName; }
    public Long getVersionCode() { return versionCode; }
    public void setVersionCode(Long versionCode) { this.versionCode = versionCode; }
    public String getVersionName() { return versionName; }
    public void setVersionName(String versionName) { this.versionName = versionName; }
    public Integer getMinSdk() { return minSdk; }
    public void setMinSdk(Integer minSdk) { this.minSdk = minSdk; }
    public String getAbiList() { return abiList; }
    public void setAbiList(String abiList) { this.abiList = abiList; }
    public String getLaunchActivity() { return launchActivity; }
    public void setLaunchActivity(String launchActivity) { this.launchActivity = launchActivity; }
    public String getWaitActivity() { return waitActivity; }
    public void setWaitActivity(String waitActivity) { this.waitActivity = waitActivity; }
    public String getApkBlobRef() { return apkBlobRef; }
    public void setApkBlobRef(String apkBlobRef) { this.apkBlobRef = apkBlobRef; }
    public String getSha256() { return sha256; }
    public void setSha256(String sha256) { this.sha256 = sha256; }
    public String getCertificateDigest() { return certificateDigest; }
    public void setCertificateDigest(String certificateDigest) { this.certificateDigest = certificateDigest; }
    public String getVerification() { return verification; }
    public void setVerification(String verification) { this.verification = verification; }
    public String getObservedMetadataJson() { return observedMetadataJson; }
    public void setObservedMetadataJson(String observedMetadataJson) { this.observedMetadataJson = observedMetadataJson; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
