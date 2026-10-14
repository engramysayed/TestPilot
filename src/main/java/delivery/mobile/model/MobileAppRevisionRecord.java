package delivery.mobile.model;

import java.util.ArrayList;
import java.util.List;

/** Safe app revision view for PortalStore / Frontend M1-07 (exact JSON names). */
public class MobileAppRevisionRecord {
    private String appRevisionId;
    private String appId;
    private String projectId;
    private String tenantId;
    private String source;
    private String packageName;
    private Long versionCode;
    private String versionName;
    private Integer minSdk;
    private List<String> abiList = new ArrayList<>();
    private String launchActivity;
    private String waitActivity;
    private String sha256;
    private String verification;
    /** Optional signing cert digest (APK uploads); never a secret. */
    private String certificateDigest;
    /** Logical blob ref under tenant store; never a raw host path in API responses. */
    private String apkBlobRef;

    public String getAppRevisionId() { return appRevisionId; }
    public void setAppRevisionId(String appRevisionId) { this.appRevisionId = appRevisionId; }
    public String getAppId() { return appId; }
    public void setAppId(String appId) { this.appId = appId; }
    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
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
    public List<String> getAbiList() { return abiList; }
    public void setAbiList(List<String> abiList) {
        this.abiList = abiList == null ? new ArrayList<>() : new ArrayList<>(abiList);
    }
    public String getLaunchActivity() { return launchActivity; }
    public void setLaunchActivity(String launchActivity) { this.launchActivity = launchActivity; }
    public String getWaitActivity() { return waitActivity; }
    public void setWaitActivity(String waitActivity) { this.waitActivity = waitActivity; }
    public String getSha256() { return sha256; }
    public void setSha256(String sha256) { this.sha256 = sha256; }
    public String getVerification() { return verification; }
    public void setVerification(String verification) { this.verification = verification; }
    public String getCertificateDigest() { return certificateDigest; }
    public void setCertificateDigest(String certificateDigest) { this.certificateDigest = certificateDigest; }
    public String getApkBlobRef() { return apkBlobRef; }
    public void setApkBlobRef(String apkBlobRef) { this.apkBlobRef = apkBlobRef; }
}
